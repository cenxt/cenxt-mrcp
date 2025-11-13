package cn.cenxt.cc.demo.ai.coze;

import cn.cenxt.cc.demo.ai.AiConversationManager;
import cn.cenxt.cc.demo.util.LogUtil;
import cn.cenxt.cc.mrcp.ai.AiProcessHandler;
import cn.cenxt.cc.mrcp.ai.AiRequest;
import cn.cenxt.cc.mrcp.ai.AiResultListener;
import cn.cenxt.cc.mrcp.common.StopHandle;
import com.alibaba.fastjson.JSON;
import com.coze.openapi.client.chat.CancelChatReq;
import com.coze.openapi.client.chat.CancelChatResp;
import com.coze.openapi.client.chat.CreateChatReq;
import com.coze.openapi.client.chat.model.ChatEvent;
import com.coze.openapi.client.chat.model.ChatEventType;
import com.coze.openapi.client.connversations.message.model.Message;
import com.coze.openapi.client.connversations.message.model.MessageType;
import com.coze.openapi.service.auth.TokenAuth;
import com.coze.openapi.service.service.CozeAPI;
import io.reactivex.Flowable;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
public class CozeProcessor implements AiProcessHandler {

    @Autowired
    private CozeConfig cozeConfig;

    private CozeAPI coze;

    @Autowired
    private AiConversationManager conversationManager;

    @PostConstruct
    public void init() {
        TokenAuth authCli = getAuthCli();
        coze =
                new CozeAPI.Builder()
                        .baseURL(cozeConfig.getApiUrl())
                        .auth(authCli)
                        .readTimeout(10000)
                        .build();
    }

    private TokenAuth getAuthCli() {
        return new TokenAuth(cozeConfig.getApiKey());
    }

    @Override
    public String getName() {
        return "coze";
    }

    /**
     * LLM处理
     *
     * @param request  请求
     * @param listener 监听器
     * @return 处理句柄
     */
    @Override
    public StopHandle start(AiRequest request, AiResultListener listener) {
        log.info("coze agent query:{}", request);
        AtomicReference<String> chatId = new AtomicReference<>();
        try {
            String uid = request.getCaller();
            if (!StringUtils.hasText(uid)) {
                uid = "uid";
            }
            CreateChatReq req =
                    CreateChatReq.builder()
                            .botID(cozeConfig.getBotId())
                            .userID(uid)
                            .conversationID(conversationManager.getConversation(request.getUuid()))
                            .messages(Collections.singletonList(Message.buildUserQuestionText(request.getQuery())))
                            .build();

            Flowable<ChatEvent> resp = coze.chat().stream(req);
            resp.forEach(
                    event -> {
                        try {
                            LogUtil.setUuid(request.getUuid());
                            log.debug("coze agent query receive:{}", JSON.toJSONString(event));
                            if (ChatEventType.CONVERSATION_CHAT_CREATED.equals(event.getEvent())) {
                                log.info("coze agent query receive created:{}", event.getChat().getConversationID());
                                chatId.set(event.getChat().getID());
                                conversationManager.addConversation(request.getUuid(), event.getChat().getConversationID());
                            } else if (ChatEventType.CONVERSATION_MESSAGE_DELTA.equals(event.getEvent())
                                    && MessageType.ANSWER.equals(event.getMessage().getType())) {
                                log.debug("coze agent query receive delta:{}", event.getMessage().getContent());
                                if (StringUtils.hasText(event.getMessage().getContent())) {
                                    listener.change(event.getMessage().getContent());
                                }
                            } else if (ChatEventType.CONVERSATION_MESSAGE_COMPLETED.equals(event.getEvent())
                                    && MessageType.ANSWER.equals(event.getMessage().getType())) {
                                log.info("coze agent query receive complete:{}", event.getMessage().getContent());
                                chatId.set("");
                                listener.complete(event.getMessage().getContent());
                            } else if (ChatEventType.CONVERSATION_CHAT_FAILED.equals(event.getEvent())
                                    || ChatEventType.ERROR.equals(event.getEvent())) {
                                log.error("coze agent query receive error:{}", event);
                                listener.error(new RuntimeException("coze agent query receive error:" + event));
                            }
                        } catch (Exception e) {
                            log.error("coze agent query resp foreach error", e);
                            chatId.set("");
                            listener.error(e);

                        }
                    });
        } catch (Exception e) {
            log.error("coze agent query error", e);
            chatId.set("");
            listener.error(e);

        }
        return new StopHandle() {
            /**
             * 停止TTS处理
             */
            @Override
            protected void onStop() {
                log.info("coze agent query stop");
                String id = chatId.get();
                String conversation = conversationManager.getConversation(request.getUuid());
                int count = 0;
                while (id == null) {
                    id = chatId.get();
                    count++;
                    if (count > 10) {
                        break;
                    }
                    try {
                        log.info("coze agent close wait for chatId");
                        Thread.sleep(200);
                    } catch (InterruptedException ignored) {
                    }
                }
                if (id == null || id.isEmpty()) {
                    return;
                }
                log.info("coze agent send cancel conversationId:{}, chatId:{}", conversation, id);
                try {
                    CancelChatReq req = CancelChatReq.builder()
                            .chatID(id)
                            .conversationID(conversation)
                            .build();

                    CancelChatResp cancel = coze.chat().cancel(req);
                    log.info("coze agent query cancel resp:{}", JSON.toJSONString(cancel));
                } catch (Exception e) {
                    log.info("coze agent query cancel error:{}", e.getMessage());
                }
                listener.complete(null);
            }
        };
    }
}
