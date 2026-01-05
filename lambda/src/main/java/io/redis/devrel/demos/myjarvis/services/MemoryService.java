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
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static io.redis.devrel.demos.myjarvis.helpers.Constants.*;

public class MemoryService {

    private static final Logger logger = LoggerFactory.getLogger(MemoryService.class);
    private static final ObjectMapper objectMapper = createObjectMapper();
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .build();

    private static final String MEMORIES_NAMESPACE = "memories";
    private static final String KNOWLEDGE_NAMESPACE = "knowledge";
    private static final String MEMORY_TYPE_SEMANTIC = "semantic";

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        return mapper;
    }

    public Optional<String> getUserMemoryId(String userId, String memory) {
        var searchRequest = Map.of(
                "user_id", Map.of("eq", userId),
                "namespace", Map.of("eq", MEMORIES_NAMESPACE),
                "text", memory,
                "limit", 1
        );

        return executeSearch(searchRequest)
                .stream()
                .findFirst()
                .map(node -> node.get("id").asText());
    }

    public boolean createUserMemory(String sessionId, String userId,
                                    String timezone, String memory) {
        var currentDateTime = getDateAndTime(timezone);
        var formattedMemory = "Memory from %s: %s".formatted(currentDateTime, memory);

        var memoryData = Map.of(
                "memories", List.of(Map.of(
                        "id", sessionId,
                        "session_id", sessionId,
                        "user_id", userId,
                        "namespace", MEMORIES_NAMESPACE,
                        "text", formattedMemory,
                        "memory_type", MEMORY_TYPE_SEMANTIC
                ))
        );

        try {
            var request = buildJsonRequest(
                    URI.create(AGENT_MEMORY_SERVER_URL + "/v1/long-term-memory/"),
                    memoryData,
                    "POST"
            );

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == HttpStatus.SC_OK) {
                var root = objectMapper.readTree(response.body());
                return "ok".equals(root.path("status").asText());
            }
        } catch (Exception ex) {
            logger.error("Error saving long-term memory", ex);
        }

        return false;
    }

    public boolean deleteUserMemory(String sessionId) {
        try {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(AGENT_MEMORY_SERVER_URL + "/v1/long-term-memory?memory_ids=" + sessionId))
                    .DELETE()
                    .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            return response.statusCode() == HttpStatus.SC_OK;
        } catch (Exception ex) {
            logger.error("Error deleting the long-term memory", ex);
            return false;
        }
    }

    public List<String> searchUserMemories(String userId, String memory) {
        var searchRequest = Map.of(
                "user_id", Map.of("eq", userId),
                "namespace", Map.of("eq", MEMORIES_NAMESPACE),
                "text", memory,
                "recent_boost", true,
                "limit", USER_MEMORIES_SEARCH_LIMIT
        );

        return extractTexts(executeSearch(searchRequest));
    }

    public void createKnowledgeBaseEntry(String memory) {
        var sanitizedMemory = Optional.ofNullable(memory)
                .map(m -> m.replaceAll("[\\r\\n]+", " "))
                .map(m -> m.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", ""))
                .orElse("");

        var formattedMemory = "Fact from %s, %s".formatted(Instant.now(), sanitizedMemory);

        var memoryData = Map.of(
                "memories", List.of(Map.of(
                        "id", "knowledge.entry.%s".formatted(UUID.randomUUID()),
                        "namespace", KNOWLEDGE_NAMESPACE,
                        "text", formattedMemory,
                        "memory_type", MEMORY_TYPE_SEMANTIC
                ))
        );

        try {
            var request = buildJsonRequest(
                    URI.create(AGENT_MEMORY_SERVER_URL + "/v1/long-term-memory/"),
                    memoryData,
                    "POST"
            );

            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception ex) {
            logger.error("Exception occurred while creating long-term memory", ex);
        }
    }

    public List<String> searchKnowledgeBase(String memory) {
        var searchRequest = Map.of(
                "namespace", Map.of("eq", KNOWLEDGE_NAMESPACE),
                "text", memory,
                "limit", KNOWLEDGE_BASE_SEARCH_LIMIT
        );

        return extractTexts(executeSearch(searchRequest));
    }

    private String getDateAndTime(String timezone) {
        ZoneId zone = ZoneId.of(timezone);
        var now = ZonedDateTime.now(zone);
        var formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(zone);
        return formatter.format(now);
    }

    private HttpRequest buildJsonRequest(URI uri, Object body, String method) {
        try {
            var requestBuilder = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("Content-Type", "application/json");

            var bodyPublisher = body != null
                    ? HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body))
                    : HttpRequest.BodyPublishers.noBody();

            return switch (method) {
                case "POST" -> requestBuilder.POST(bodyPublisher).build();
                case "DELETE" -> requestBuilder.DELETE().build();
                default -> throw new IllegalArgumentException("Unsupported method: " + method);
            };
        } catch (Exception e) {
            throw new RuntimeException("Failed to build request", e);
        }
    }

    private List<JsonNode> executeSearch(Map<String, Object> searchRequest) {
        try {
            var request = buildJsonRequest(
                    URI.create(AGENT_MEMORY_SERVER_URL + "/v1/long-term-memory/search?optimize_query=false"),
                    searchRequest,
                    "POST"
            );

            logger.debug("Executing request: " + request.toString());
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            logger.debug("Finishing search execution. Response status: " + response.statusCode());

            if (response.statusCode() == HttpStatus.SC_OK) {
                var memories = objectMapper.readTree(response.body()).path("memories");
                if (!memories.isEmpty()) {
                    var result = new ArrayList<JsonNode>();
                    memories.forEach(result::add);
                    return result;
                }
            }
        } catch (Exception ex) {
            logger.error("Error during memory search", ex);
        }

        return List.of();
    }

    private List<String> extractTexts(List<JsonNode> nodes) {
        return nodes.stream()
                .map(node -> node.path("text").asText())
                .filter(text -> !text.isEmpty())
                .toList();
    }
}