package io.redis.devrel.demos.myjarvis.services;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface ContextualChatAssistant {

    @SystemMessage("""
        {{systemPrompt}}
        
        The name of the user is {{userName}}.
        """)
    @UserMessage("UserId: {{userId}}, Query: {{query}}")
    String chat(@V("systemPrompt") String systemPrompt,
                @MemoryId @V("userId") String userId,
                @V("userName") String userName,
                @V("query") String query);
}
