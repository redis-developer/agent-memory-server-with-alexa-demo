package io.redis.devrel.demos.myjarvis.services;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface BasicChatAssistant {

    @SystemMessage("""
        {{systemPrompt}}
        """)
    String chat(@V("systemPrompt") String systemPrompt,
                @UserMessage String query);

}
