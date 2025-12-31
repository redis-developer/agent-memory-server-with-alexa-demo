package io.redis.devrel.demos.myjarvis.extensions;

import dev.langchain4j.data.message.*;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class TokenLimitingChatMemory implements ChatMemory {

    private static final Logger logger = LoggerFactory.getLogger(TokenLimitingChatMemory.class);

    private final String sessionId;
    private final ChatMemoryStore chatMemoryStore;
    private final List<ChatMessage> messages;
    private final TokenCountEstimator tokenCountEstimator;
    private final int maxTokens;

    public TokenLimitingChatMemory(String sessionId,
                                   ChatMemoryStore chatMemoryStore,
                                   TokenCountEstimator tokenCountEstimator,
                                   int maxTokens) {
        this.sessionId = sessionId;
        this.chatMemoryStore = chatMemoryStore;
        this.tokenCountEstimator = tokenCountEstimator;
        this.maxTokens = maxTokens;

        // Load existing messages
        this.messages = new ArrayList<>(chatMemoryStore.getMessages(sessionId));
        trimMessagesToFitTokenLimit();

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
        trimMessagesToFitTokenLimit();
        chatMemoryStore.updateMessages(sessionId, messages);
    }

    @Override
    public List<ChatMessage> messages() {
        return new ArrayList<>(messages);
    }

    @Override
    public void clear() {
        messages.clear();
        chatMemoryStore.deleteMessages(sessionId);
    }

    private void trimMessagesToFitTokenLimit() {
        while (messages.size() > 1) { // Keep at least one message
            int currentTokens = tokenCountEstimator.estimateTokenCountInMessages(messages);

            if (currentTokens <= maxTokens) {
                break;
            }

            // Remove the oldest message (but keep system messages if they're important)
            for (int i = 0; i < messages.size(); i++) {
                if (!(messages.get(i) instanceof SystemMessage)) {
                    ChatMessage removed = messages.remove(i);
                    break;
                }
            }

            // If we only have system messages, remove the oldest one
            if (currentTokens > maxTokens && messages.size() > 1) {
                messages.remove(0);
            }
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String sessionId;
        private ChatMemoryStore chatMemoryStore;
        private TokenCountEstimator tokenCountEstimator;
        private int maxTokens = Integer.MAX_VALUE;

        public Builder withSessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder withChatMemoryStore(ChatMemoryStore chatMemoryStore) {
            this.chatMemoryStore = chatMemoryStore;
            return this;
        }

        public Builder withTokenEstimator(TokenCountEstimator tokenCountEstimator) {
            this.tokenCountEstimator = tokenCountEstimator;
            return this;
        }

        public Builder withMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public TokenLimitingChatMemory build() {
            return new TokenLimitingChatMemory(
                    sessionId, chatMemoryStore,
                    tokenCountEstimator, maxTokens);
        }
    }
}
