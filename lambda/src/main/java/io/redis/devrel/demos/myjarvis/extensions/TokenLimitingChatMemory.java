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

    private final String id;
    private final ChatMemoryStore chatMemoryStore;
    private final List<ChatMessage> messages;
    private final TokenCountEstimator tokenCountEstimator;
    private final int maxTokens;

    public TokenLimitingChatMemory(String id,
                                   ChatMemoryStore chatMemoryStore,
                                   TokenCountEstimator tokenCountEstimator,
                                   int maxTokens) {
        this.id = id;
        this.chatMemoryStore = chatMemoryStore;
        this.tokenCountEstimator = tokenCountEstimator;
        this.maxTokens = maxTokens;

        // Load existing messages
        this.messages = new ArrayList<>(chatMemoryStore.getMessages(id));
        ensureCapacity();

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
            return;
        }

        messages.add(message);
        ensureCapacity();
        chatMemoryStore.updateMessages(id, messages);
    }

    @Override
    public List<ChatMessage> messages() {
        return new ArrayList<>(messages);
    }

    @Override
    public void clear() {
        messages.clear();
        chatMemoryStore.deleteMessages(id);
    }

    private void ensureCapacity() {
        while (messages.size() > 1 && tokenCountEstimator.estimateTokenCountInMessages(messages) > maxTokens) {
            // Find the first non-SystemMessage to remove
            int indexToRemove = -1;
            for (int i = 0; i < messages.size(); i++) {
                ChatMessage message = messages.get(i);
                if (!(message instanceof SystemMessage)) {
                    // Check if this is an AiMessage with tool calls
                    if (message instanceof AiMessage aiMessage && aiMessage.hasToolExecutionRequests()) {
                        // Also need to remove the corresponding tool result message(s)
                        // Find all tool result messages that follow this AI message
                        List<Integer> toolResultIndices = new ArrayList<>();
                        for (int j = i + 1; j < messages.size(); j++) {
                            ChatMessage nextMsg = messages.get(j);
                            if (nextMsg instanceof ToolExecutionResultMessage) {
                                toolResultIndices.add(j);
                            } else if (nextMsg instanceof UserMessage) {
                                // Stop at the next user message (new turn)
                                break;
                            }
                        }

                        // Remove tool results first (in reverse order to maintain indices)
                        for (int k = toolResultIndices.size() - 1; k >= 0; k--) {
                            messages.remove((int) toolResultIndices.get(k));
                        }
                    }

                    indexToRemove = i;
                    break;
                }
            }

            if (indexToRemove >= 0) {
                messages.remove(indexToRemove);
            } else {
                break;
            }
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private ChatMemoryStore chatMemoryStore;
        private TokenCountEstimator tokenCountEstimator;
        private int maxTokens = Integer.MAX_VALUE;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder chatMemoryStore(ChatMemoryStore chatMemoryStore) {
            this.chatMemoryStore = chatMemoryStore;
            return this;
        }

        public Builder tokenEstimator(TokenCountEstimator tokenCountEstimator) {
            this.tokenCountEstimator = tokenCountEstimator;
            return this;
        }

        public Builder maxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public TokenLimitingChatMemory build() {
            return new TokenLimitingChatMemory(
                    id, chatMemoryStore,
                    tokenCountEstimator, maxTokens);
        }
    }
}