package io.redis.devrel.demos.myjarvis.extensions;

import dev.langchain4j.data.message.*;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static io.redis.devrel.demos.myjarvis.helpers.MessageHelper.messageContent;

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
        if (message == null) {
            logger.warn("Attempted to add null message");
            return;
        }

        logger.info("Adding message type: {}", message.getClass().getSimpleName());

        try {
            // Only clean AFTER the assistant has responded
            if (message instanceof AiMessage && !messages.isEmpty()) {
                logger.info("Processing AiMessage, cleaning previous UserMessage");

                // Clean the PREVIOUS user message if it exists
                int lastUserIndex = -1;
                for (int i = messages.size() - 1; i >= 0; i--) {
                    if (messages.get(i) instanceof UserMessage) {
                        lastUserIndex = i;
                        break;
                    }
                }

                if (lastUserIndex >= 0) {
                    String content = messageContent(messages.get(lastUserIndex));
                    logger.info("Cleaning user message at index {}: {}", lastUserIndex,
                            content.substring(0, Math.min(100, content.length())));

                    String cleanContent = extractOriginalQuery(content);
                    if (!cleanContent.isBlank()) {
                        messages.set(lastUserIndex, UserMessage.from("Query: " + cleanContent));
                        logger.info("Cleaned to: Query: {}", cleanContent);
                    }
                }
            }

            // Add the new message as-is
            messages.add(message);
            logger.debug("Number of chat messages is {}", messages.size());
            chatMemoryStore.updateMessages(id, messages);

        } catch (Exception ex) {
            logger.error("Error adding message", ex);
            // Still add the message even if cleaning failed
            messages.add(message);
            chatMemoryStore.updateMessages(id, messages);
        }
    }

    private String extractOriginalQuery(String messageContent) {
        if (!messageContent.contains("Query: ")) {
            return messageContent;
        }

        int queryStart = messageContent.indexOf("Query: ") + 7;
        String fromQuery = messageContent.substring(queryStart);

        // Find where the actual query ends (before augmentation)
        int augmentStart = fromQuery.indexOf("\n\nAnswer using");
        if (augmentStart > 0) {
            fromQuery = fromQuery.substring(0, augmentStart).trim();
        } else {
            // No augmentation, look for newline
            int firstNewline = fromQuery.indexOf('\n');
            if (firstNewline > 0) {
                fromQuery = fromQuery.substring(0, firstNewline).trim();
            }
        }

        // Handle special cases
        if (fromQuery.startsWith("User asked to store this memory:")) {
            return fromQuery.substring("User asked to store this memory:".length()).trim();
        }

        return fromQuery;
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
