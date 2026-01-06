package io.redis.devrel.demos.myjarvis.extensions;

import dev.langchain4j.data.message.*;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class WorkingMemoryChat implements ChatMemory {

    private static final Logger logger = LoggerFactory.getLogger(WorkingMemoryChat.class);

    private final String id;
    private final ChatMemoryStore chatMemoryStore;
    private final List<ChatMessage> messages;

    public WorkingMemoryChat(String id,
                             ChatMemoryStore chatMemoryStore) {
        this.id = id;
        this.chatMemoryStore = chatMemoryStore;

        // Load existing messages
        this.messages = new ArrayList<>(chatMemoryStore.getMessages(id));
        logger.debug("Initialized WorkingMemoryChat for session {} with {} messages",
                id, this.messages.size());
    }

    @Override
    public Object id() {
        return id;
    }

    @Override
    public void add(ChatMessage message) {
        messages.add(message);
        chatMemoryStore.updateMessages(id, messages);
    }

    @Override
    public List<ChatMessage> messages() {
        return messages;
    }

    @Override
    public void clear() {
        messages.clear();
        chatMemoryStore.deleteMessages(id);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private ChatMemoryStore chatMemoryStore;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder chatMemoryStore(ChatMemoryStore chatMemoryStore) {
            this.chatMemoryStore = chatMemoryStore;
            return this;
        }

        public WorkingMemoryChat build() {
            return new WorkingMemoryChat(id, chatMemoryStore);
        }
    }
}
