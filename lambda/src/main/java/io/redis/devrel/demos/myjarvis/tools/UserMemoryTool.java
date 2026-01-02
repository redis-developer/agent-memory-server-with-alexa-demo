package io.redis.devrel.demos.myjarvis.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import io.redis.devrel.demos.myjarvis.services.MemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserMemoryTool {

    private static final Logger logger = LoggerFactory.getLogger(UserMemoryTool.class);

    private final MemoryService memoryService;

    public UserMemoryTool(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @Tool("Creates a new memory for the user")
    public boolean createUserMemory(@P("the session ID from context") String sessionId,
                                    @P("the user ID from context") String userId,
                                    @P("the timezone from context") String timezone,
                                    @P("the memory to be stored") String memory) {
        logger.info("Creating a new memory for the user: sessionId={}, userId={}, timezone={}, memory={}",
                sessionId, userId, timezone, memory);
        return memoryService.createUserMemory(sessionId, userId, timezone, memory);
    }

}
