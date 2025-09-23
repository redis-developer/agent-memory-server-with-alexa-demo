package io.redis.devrel.demos.myjarvis.extensions;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class WorkingMemoryChat implements ChatMemory {

    private static final Logger logger = LoggerFactory.getLogger(WorkingMemoryChat.class);

    private final String sessionId;
    private final WorkingMemoryStore memoryStore;
    private final List<ChatMessage> messages;

    public WorkingMemoryChat(String sessionId, String agentMemoryServerUrl) {
        this.sessionId = sessionId;
        this.memoryStore = new WorkingMemoryStore(agentMemoryServerUrl);
        this.messages = new ArrayList<>(memoryStore.getMessages(sessionId));
        logger.debug("Initialized WorkingMemoryChat for session {} with {} messages",
                sessionId, this.messages.size());
    }

    @Override
    public Object id() {
        return sessionId;
    }

    @Override
    public void add(ChatMessage message) {
        if (message == null) {
            return;
        }

        messages.add(message);
        memoryStore.updateMessages(sessionId, messages);
    }

    @Override
    public List<ChatMessage> messages() {
        return new ArrayList<>(messages);
    }

    @Override
    public void clear() {
        messages.clear();
        memoryStore.deleteMessages(sessionId);
    }
}
