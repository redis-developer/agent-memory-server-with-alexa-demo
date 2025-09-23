package io.redis.devrel.demos.myjarvis.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static io.redis.devrel.demos.myjarvis.helpers.Constants.*;

public class AgentMemoryServerTool {

    private static final Logger logger = LoggerFactory.getLogger(AgentMemoryServerTool.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .build();

    @Tool("Check the agent memory server health")
    public boolean checkAgentMemoryServerHealth() {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(AGENT_MEMORY_SERVER_URL + "/v1/health"))
                .GET()
                .build();

        try {
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == HttpStatus.SC_OK) {
                var root = objectMapper.readTree(response.body());
                return root.path("now").asLong(0) > 0;
            }
        } catch (Exception ex) {
            logger.error("Error checking agent memory server health", ex);
        }

        return false;
    }
}
