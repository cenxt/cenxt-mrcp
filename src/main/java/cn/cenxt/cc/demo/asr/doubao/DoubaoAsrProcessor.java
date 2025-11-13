package cn.cenxt.cc.demo.asr.doubao;

import cn.cenxt.cc.mrcp.asr.*;
import cn.cenxt.cc.demo.util.LogUtil;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;


/**
 * 功能描述： 豆包ASR处理
 *
 * @author cenxt
 * @email cpt725@qq.com
 * @date 2025/9/3 17:22
 */
@Slf4j
@Component
public class DoubaoAsrProcessor implements AsrProcessHandler {

    @Autowired
    private DoubaoAsrConfig config;

    @Override
    public String getName() {
        return "doubao";
    }

    @Override
    public AsrConfig getConfig() {
        return config;
    }

    /**
     * 执行ASR识别
     *
     * @param request  ASR请求参数
     * @param listener ASR结果接收监听
     */
    @Override
    public AsrHandle start(AsrRequest request, AsrResultListener listener) {
        log.info("doubao asr request:{}", request);
        config.setParams(request);
        long speechCompleteTimeout = request.getSpeechCompleteTimeout();
        if (speechCompleteTimeout <= 0) {
            speechCompleteTimeout = 1000;
        }
        String uid = request.getCallee();
        if (!StringUtils.hasText(uid)) {
            uid = "uid";
        }
        DoubaoAsrParams asrParams = DoubaoAsrParams.builder()
                .app(DoubaoAsrParams.App.builder()
                        .appid(config.getAppId())
                        .cluster(config.getCluster())
                        .token(config.getAccessToken()).build())
                .user(DoubaoAsrParams.User.builder().uid(uid).build())
                .request(DoubaoAsrParams.Request.builder()
                        .reqid(UUID.randomUUID().toString())
                        .workflow("audio_in,resample,partition,vad,fe,decode")
                        .nbest(1)
                        .show_utterances(true)
                        .result_type("single")
                        .sequence(1)
                        .vad_signal(true)
//                        .start_silence_time("1000")
                        .vad_silence_time(speechCompleteTimeout + "")
                        .build())
                .audio(DoubaoAsrParams.Audio.builder()
                        .format("raw")
                        .codec("pcm")
                        .rate(8000)
                        .bits(16)
                        .channels(1)
                        .build())
                .build();

        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer; " + config.getAccessToken());
        headers.put("Content-Type", "text/plain; charset=utf-8");
        WebSocketClient asrClient = new WebSocketClient(URI.create(config.getUrl())
                , headers) {
            @Override
            public void onOpen(ServerHandshake serverHandshake) {
                LogUtil.setUuid(request.getUuid());
                log.info("doubao asr open");
            }

            @Override
            public void onMessage(ByteBuffer bytes) {
                byte[] msg_byte = bytes.array();
                int header_len = (msg_byte[0] & 0x0f) << 2;
                int message_type = (msg_byte[1] & 0xf0) >> 4;
                int message_type_flag = msg_byte[1] & 0x0f;
                int message_serial = (msg_byte[2] & 0xf0) >> 4;
                int message_compress = msg_byte[2] & 0x0f;
                byte[] payload = null;
                int payload_len = 0;
                int payload_offset = header_len;

                if (message_type == DoubaoAsrParams.MessageType.FULL_SERVER_RESPONSE) {
                    payload_offset += 4;
                    payload = new byte[msg_byte.length - payload_offset];
                    System.arraycopy(msg_byte, payload_offset, payload, 0, payload.length);
                    DoubaoAsrResponse response = JSON.parseObject(payload, DoubaoAsrResponse.class);
                    log.debug("doubao asr response {}", JSON.toJSONString(response));
                    if (response.getCode() != 1000) {
                        log.error("doubao asr response error:{}", JSON.toJSONString(response));
                        close(CloseFrame.NORMAL, "doubao asr response error code:" + response.getCode());
                        return;
                    }
                    DoubaoAsrResponse.Result[] result = response.getResult();
                    if (result != null && result.length > 0) {
                        DoubaoAsrResponse.Utterances[] utterances = result[0].getUtterances();
                        if (utterances != null && utterances.length > 0) {
                            DoubaoAsrResponse.Utterances utterance = utterances[0];
                            if (StringUtils.hasText(utterance.getText())) {
                                listener.input();
                                if (utterance.isDefinite()) {
                                    log.info("doubao asr response definite:{}", JSON.toJSONString(response));
                                    listener.complete(utterance.getText());
                                } else {
                                    listener.change(utterance.getText());
                                }
                            }
                        }
                    }

                } else if (message_type == DoubaoAsrParams.MessageType.SERVER_ACK) {
                    log.info("doubao asr response ack");
                } else if (message_type == DoubaoAsrParams.MessageType.ERROR_MESSAGE_FROM_SERVER) {
                    int error_code = ByteBuffer.wrap(msg_byte, payload_offset, 4).getInt();
                    payload_offset += 8;
                    payload = new byte[msg_byte.length - payload_offset];
                    System.arraycopy(msg_byte, payload_offset, payload, 0, payload.length);
                    log.error("doubao asr response error code:{},response:{}", error_code, new String(payload));
                    close(CloseFrame.NORMAL, "doubao asr response error code:" + error_code);
                } else {
                    log.warn("unsupported message type {}", message_type);
                }
            }

            @Override
            public void onMessage(String s) {
                log.info("doubao asr onMessage:{}", s);
            }

            @Override
            public void onClose(int i, String s, boolean b) {
                log.info("doubao asr close");
                listener.complete(null);
            }

            @Override
            public void onError(Exception e) {
                log.error("doubao asr error", e);
                close(CloseFrame.NORMAL, e.toString());
                listener.complete(null);
            }
        };

        String json = JSON.toJSONString(asrParams);
        log.info("doubao asr send: {}", json);
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        byte[] header = {0x11, 0x10, 0x10, 0x00};
        ByteBuffer requestByte = ByteBuffer.allocate(8 + jsonBytes.length);
        requestByte.put(header).putInt(jsonBytes.length).put(jsonBytes);

        try {
            asrClient.connectBlocking();
            asrClient.send(requestByte.array());
            log.info("doubao asr send success");
        } catch (Exception e) {
            log.error("doubao asr error", e);
        }
        return new AsrHandle() {
            /**
             * 停止处理
             */
            @Override
            protected void onStop() {
                log.info("doubao asr stop");
                asrClient.close();
                listener.complete(null);
            }

            /**
             * 接收数据
             *
             * @param bytes 数据
             */
            @Override
            protected void onReceive(byte[] bytes) {
                log.debug("doubao asr send payload:{}", bytes.length);
//                try {
//                    bytes = gzip_compress(bytes);
//                } catch (IOException e) {
//                    log.warn("doubao asr gzip_compress error", e);
//                    return;
//                }
                byte[] header = {0x11, 0x20, 0x00, 0x00};
                ByteBuffer requestByte = ByteBuffer.allocate(8 + bytes.length);
                requestByte.put(header).putInt(bytes.length).put(bytes);
                asrClient.send(requestByte.array());
            }
        };

    }

    private byte[] gzip_compress(byte[] content) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(content.length);
        GZIPOutputStream gzip = new GZIPOutputStream(out);
        gzip.write(content);
        gzip.close();
        byte[] result = out.toByteArray();
        out.close();
        return result;
    }
}
