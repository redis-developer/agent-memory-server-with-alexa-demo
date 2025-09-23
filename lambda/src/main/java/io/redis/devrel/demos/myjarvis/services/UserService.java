package io.redis.devrel.demos.myjarvis.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.redis.devrel.demos.myjarvis.helpers.Constants.*;

public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .build();

    private static final String USERS_NAMESPACE = "users";
    private static final String MEMORY_TYPE_SEMANTIC = "semantic";

    public Optional<String> getUserName(String userId) {
        if (userId == null || userId.isBlank()) {
            logger.warn("Invalid userId provided");
            return Optional.empty();
        }

        var searchRequest = buildUserSearchRequest(userId);
        return searchUser(searchRequest)
                .toOptional()
                .flatMap(this::extractUserNameFromResponse);
    }

    public boolean createUser(String userId, String userName) {
        if (!validateUserInput(userId, userName)) {
            logger.warn("Invalid user input: userId={}, userName={}", userId, userName);
            return false;
        }

        var longTermMemory = buildUserMemory(userId, userName);
        return createUserAsLongTermMemory(longTermMemory);
    }

    private sealed interface ApiResult<T> {
        record Success<T>(T value) implements ApiResult<T> {}
        record Failure<T>(String error, int statusCode) implements ApiResult<T> {}

        default Optional<T> toOptional() {
            return switch (this) {
                case Success<T> success -> Optional.of(success.value());
                case Failure<T> failure -> {
                    logger.debug("API call failed: {} (status: {})", failure.error(), failure.statusCode());
                    yield Optional.empty();
                }
            };
        }
    }

    private Map<String, Object> buildUserSearchRequest(String userId) {
        return Map.of(
                "id", Map.of("eq", userId),
                "user_id", Map.of("eq", userId),
                "namespace", Map.of("eq", USERS_NAMESPACE),
                "limit", 1
        );
    }

    private ApiResult<JsonNode> searchUser(Map<String, Object> searchRequest) {
        try {
            var requestBody = objectMapper.writeValueAsString(searchRequest);
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(AGENT_MEMORY_SERVER_URL + "/v1/long-term-memory/search"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == HttpStatus.SC_OK) {
                var responseJson = objectMapper.readTree(response.body());
                return new ApiResult.Success<>(responseJson);
            } else {
                return new ApiResult.Failure<>(
                        "Unexpected status code",
                        response.statusCode()
                );
            }
        } catch (Exception ex) {
            logger.error("Error searching for user: {}", searchRequest.get("user_id"), ex);
            return new ApiResult.Failure<>("Exception occurred: " + ex.getMessage(), -1);
        }
    }

    private Optional<String> extractUserNameFromResponse(JsonNode response) {
        return Optional.ofNullable(response)
                .map(node -> node.path("memories"))
                .filter(memories -> !memories.isEmpty() && !memories.isMissingNode())
                .map(memories -> memories.path(0))
                .map(firstMemory -> firstMemory.path("text"))
                .filter(JsonNode::isTextual)
                .map(JsonNode::asText)
                .filter(text -> !text.isBlank());
    }

    private boolean validateUserInput(String userId, String userName) {
        return userId != null && !userId.isBlank()
                && userName != null && !userName.isBlank()
                && userId.length() <= 255
                && userName.length() <= 255;
    }

    private Map<String, Object> buildUserMemory(String userId, String userName) {
        // Sanitize userName to prevent injection
        var sanitizedUserName = userName.replaceAll("[\\p{Cntrl}]", "");

        return Map.of(
                "memories", List.of(Map.of(
                        "id", userId,
                        "user_id", userId,
                        "text", sanitizedUserName,
                        "namespace", USERS_NAMESPACE,
                        "memory_type", MEMORY_TYPE_SEMANTIC
                ))
        );
    }

    private boolean createUserAsLongTermMemory(Map<String, Object> longTermMemory) {
        try {
            var requestBody = objectMapper.writeValueAsString(longTermMemory);
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(AGENT_MEMORY_SERVER_URL + "/v1/long-term-memory/"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == HttpStatus.SC_OK) {
                var root = objectMapper.readTree(response.body());
                return "ok".equals(root.path("status").asText());
            }

            logger.warn("Failed to create user, status code: {}", response.statusCode());
            return false;

        } catch (Exception ex) {
            logger.error("Error saving new user", ex);
            return false;
        }
    }
}
