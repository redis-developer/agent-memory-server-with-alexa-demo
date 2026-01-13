package io.redis.devrel.demos.myjarvis.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class LangCacheService {

    private static final Logger logger = LoggerFactory.getLogger(LangCacheService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .build();

    private final String baseUrl;
    private final String apiKey;
    private final String cacheId;
    private long timeToLiveInSeconds = 60;
    private double similarityThreshold = 0.85;

    public LangCacheService(String baseUrl, String apiKey, String cacheId) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.cacheId = cacheId;
    }

    public void addNewResponse(String userId, String prompt, String response) {
        try {
            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "prompt", prompt,
                    "response", response,
                    "attributes", Map.of("userId", userId),
                    "ttlMillis", timeToLiveInSeconds * 1000
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(String.format("%s/v1/caches/%s/entries", baseUrl, cacheId)))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> responseHttp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            objectMapper.readValue(responseHttp.body(), Map.class);
        } catch (Exception ex) {
            logger.error("Failed to add new entry", ex);
        }
    }

    public Optional<String> searchForResponse(String userId, String prompt) {
        logger.debug("Searching for response for prompt {}", prompt);

        try {
            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "prompt", prompt,
                    "similarityThreshold", similarityThreshold,
                    "attributes", Map.of("userId", userId),
                    "searchStrategies", List.of("semantic")
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(String.format("%s/v1/caches/%s/entries/search", baseUrl, cacheId)))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> responseMap = objectMapper.readValue(response.body(), Map.class);
            Object dataObj = responseMap.get("data");
            logger.debug("Data found on LangCache: {}", dataObj);
            if (dataObj == null) return Optional.empty();

            List<LangCacheEntry> data = objectMapper.convertValue(
                    dataObj,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, LangCacheEntry.class)
            );

            return data.stream()
                    .map(langCacheEntry -> langCacheEntry.response)
                    .findFirst();
        } catch (Exception ex) {
            logger.error("Failed to search for entries", ex);
        }
        return Optional.empty();
    }

    public long getTimeToLiveInSeconds() {
        return timeToLiveInSeconds;
    }

    public void setTimeToLiveInSeconds(long timeToLiveInSeconds) {
        this.timeToLiveInSeconds = timeToLiveInSeconds;
    }

    public double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public void setSimilarityThreshold(double similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }

    private record LangCacheEntry(
            String id,
            String prompt,
            String response,
            String searchStrategy,
            Double similarity,
            Map<String, Object> attributes
    ) {}

    public static Builder builder() {
        return new LangCacheService.Builder();
    }

    public static class Builder {
        private String baseUrl;
        private String apiKey;
        private String cacheId;
        private Optional<Long> timeToLiveInSeconds = Optional.empty();
        private Optional<Double> similarityThreshold = Optional.empty();

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder cacheId(String cacheId) {
            this.cacheId = cacheId;
            return this;
        }

        public Builder timeToLiveInSeconds(long timeToLiveInSeconds) {
            this.timeToLiveInSeconds = Optional.of(timeToLiveInSeconds);
            return this;
        }

        public Builder similarityThreshold(double similarityThreshold) {
            this.similarityThreshold = Optional.of(similarityThreshold);
            return this;
        }

        public LangCacheService build() {
            if (baseUrl == null) {
                throw new IllegalArgumentException("baseUrl is required");
            }
            if (apiKey == null) {
                throw new IllegalArgumentException("apiKey is required");
            }
            if  (cacheId == null) {
                throw new IllegalArgumentException("cacheId is required");
            }

            LangCacheService langCacheService = new LangCacheService(baseUrl, apiKey, cacheId);
            timeToLiveInSeconds.ifPresent(langCacheService::setTimeToLiveInSeconds);
            similarityThreshold.ifPresent(langCacheService::setSimilarityThreshold);

            return langCacheService;
        }
    }
}
