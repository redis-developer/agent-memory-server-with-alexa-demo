package io.redis.devrel.demos.myjarvis.extensions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.*;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

public class WorkingMemoryStore implements ChatMemoryStore {

    private static final Logger logger = LoggerFactory.getLogger(WorkingMemoryStore.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .build();

    private final String agentMemoryServerUrl;
    private long timeToLiveInSeconds = 300;
    private boolean storeSystemMessages = false;
    private String namespace = "chat-messages";

    public WorkingMemoryStore(String agentMemoryServerUrl) {
        this.agentMemoryServerUrl = agentMemoryServerUrl;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        List<ChatMessage> chatMessages = new ArrayList<>();
        var request = HttpRequest.newBuilder()
                .uri(URI.create(agentMemoryServerUrl + "/v1/working-memory/" +
                        memoryId + "?namespace=" + namespace))
                .GET()
                .build();

        try {
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == HttpStatus.SC_OK) {
                var messages = objectMapper.readTree(response.body()).path("messages");
                if (!messages.isEmpty() && !messages.isMissingNode()) {
                    for (JsonNode messageNode : messages) {
                        var role = messageNode.path("role").asText("");
                        var content = messageNode.path("content").asText("");

                        // Skip system messages if we're not storing them
                        if (!storeSystemMessages && "system".equalsIgnoreCase(role)) {
                            continue;
                        }

                        ChatMessage chatMessage = switch (role.toLowerCase()) {
                            case "user" -> UserMessage.from(content);
                            case "assistant", "ai" -> AiMessage.from(content);
                            case "system" -> SystemMessage.from(content);
                            default -> {
                                if (!role.isEmpty()) {
                                    logger.warn("Unknown message role: {}", role);
                                }
                                yield null;
                            }
                        };

                        if (chatMessage != null) {
                            chatMessages.add(chatMessage);
                        }
                    }

                    return chatMessages;
                }
            }
        } catch (Exception ex) {
            logger.error("Error during working-term memory search", ex);
        }

        return chatMessages;
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> list) {
        try {
            // Filter out system messages if storeSystemMessages is false
            List<ChatMessage> messagesToStore = storeSystemMessages ? list :
                    list.stream()
                            .filter(msg -> !(msg instanceof SystemMessage))
                            .toList();

            List<Map<String, String>> messages = messagesToStore.stream()
                    .map(message -> {
                        Map<String, String> messageMap = new HashMap<>();
                        messageMap.put("role", determineRole(message));
                        messageMap.put("content", messageContent(message));
                        return messageMap;
                    })
                    .collect(Collectors.toList());

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("messages", messages);
            requestBody.put("session_id", memoryId.toString());
            requestBody.put("namespace", namespace);
            requestBody.put("ttl_seconds", timeToLiveInSeconds);
            requestBody.put("long_term_memory_strategy",
                    Map.of("strategy", "preferences",
                            "config", Map.of()));

            String jsonPayload = objectMapper.writeValueAsString(requestBody);

            var request = HttpRequest.newBuilder()
                    .uri(URI.create(agentMemoryServerUrl + "/v1/working-memory/" + memoryId))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(3))
                    .PUT(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception ex) {
            logger.error("Error updating working memory for session: {}", memoryId, ex);
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        try {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(agentMemoryServerUrl + "/v1/working-memory/" +
                            memoryId + "?namespace=" + namespace))
                    .DELETE()
                    .timeout(Duration.ofSeconds(3))
                    .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == HttpStatus.SC_OK ||
                    response.statusCode() == HttpStatus.SC_NO_CONTENT ||
                    response.statusCode() == HttpStatus.SC_ACCEPTED) {
                logger.info("Successfully deleted chat messages for session: {}", memoryId);
            } else if (response.statusCode() == HttpStatus.SC_NOT_FOUND) {
                logger.warn("Chat messages not found for session: {}", memoryId);
            } else {
                logger.error("Failed to delete chat messages. Status code: {}, Response: {}",
                        response.statusCode(), response.body());
            }

        } catch (Exception ex) {
            logger.error("Error deleting chat messages for session: " + memoryId, ex);
        }
    }

    public long getTimeToLiveInSeconds() {
        return timeToLiveInSeconds;
    }

    public void setTimeToLiveInSeconds(long timeToLiveInSeconds) {
        this.timeToLiveInSeconds = timeToLiveInSeconds;
    }

    public boolean isStoreSystemMessages() {
        return storeSystemMessages;
    }

    public void setStoreSystemMessages(boolean storeSystemMessages) {
        this.storeSystemMessages = storeSystemMessages;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    private String determineRole(ChatMessage message) {
        return switch (message) {
            case UserMessage userMessage -> "user";
            case AiMessage aiMessage -> "assistant";
            case SystemMessage systemMessage -> "system";
            case ToolExecutionResultMessage toolExecutionResultMessage -> "tool";
            default -> {
                String className = message.getClass().getSimpleName().toLowerCase();
                if (className.contains("user")) {
                    yield "user";
                } else if (className.contains("ai") || className.contains("assistant")) {
                    yield "assistant";
                } else if (className.contains("system")) {
                    yield "system";
                } else if (className.contains("tool")) {
                    yield "tool";
                } else {
                    logger.warn("Unknown ChatMessage type: {}", message.getClass().getName());
                    yield "unknown";
                }
            }
        };
    }

    private String messageContent(ChatMessage message) {
        return switch (message) {
            case UserMessage userMessage -> userMessage.singleText();
            case AiMessage aiMessage -> aiMessage.text();
            case SystemMessage systemMessage -> systemMessage.text();
            case ToolExecutionResultMessage toolExecutionResultMessage -> toolExecutionResultMessage.text();
            default -> {
                logger.warn("Unknown message type for content extraction: {}", message.getClass().getName());
                yield message.toString();
            }
        };
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String agentMemoryServerUrl;
        private Optional<Long> timeToLiveInSeconds = Optional.empty();
        private Optional<Boolean> storeSystemMessages = Optional.empty();
        private Optional<String> namespace = Optional.empty();

        public Builder withAgentMemoryServerUrl(String value) {
            this.agentMemoryServerUrl = value;
            return this;
        }

        public Builder withTimeToLiveInSeconds(long value) {
            this.timeToLiveInSeconds = Optional.of(value);
            return this;
        }

        public Builder withStoreSystemMessages(boolean value) {
            this.storeSystemMessages = Optional.of(value);
            return this;
        }

        public Builder withNamespace(String value) {
            this.namespace = Optional.of(value);
            return this;
        }

        public WorkingMemoryStore build() {
            if (agentMemoryServerUrl == null) {
                throw new IllegalStateException("agentMemoryServerUrl is required");
            }

            WorkingMemoryStore workingMemoryStore = new WorkingMemoryStore(agentMemoryServerUrl);
            timeToLiveInSeconds.ifPresent(workingMemoryStore::setTimeToLiveInSeconds);
            storeSystemMessages.ifPresent(workingMemoryStore::setStoreSystemMessages);
            namespace.ifPresent(workingMemoryStore::setNamespace);

            return workingMemoryStore;
        }
    }
}
