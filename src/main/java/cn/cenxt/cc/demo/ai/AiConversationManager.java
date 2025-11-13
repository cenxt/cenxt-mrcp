package cn.cenxt.cc.demo.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class AiConversationManager {
    private static final Map<String, String> conversationMap = new HashMap<>();

    public void addConversation(String uuid, String conversation) {
        if (uuid != null) {
            conversationMap.put(uuid, conversation);
        }
    }

    public String getConversation(String uuid) {
        return conversationMap.get(uuid);
    }

    public void removeConversation(String uuid) {
        if (uuid != null) {
            conversationMap.remove(uuid);
            log.info("conversation size:{}", conversationMap.size());
        }
    }
}
