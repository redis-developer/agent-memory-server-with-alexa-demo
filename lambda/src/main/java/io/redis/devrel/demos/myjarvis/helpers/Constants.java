package io.redis.devrel.demos.myjarvis.helpers;

import java.time.Duration;

public enum Constants {
    INSTANCE;
    public static final String SKILL_NAME = "My Jarvis";

    public static final String USER_INTRO_INTENT = "UserIntroIntent";
    public static final String REMEMBER_INTENT = "RememberIntent";
    public static final String RECALL_INTENT = "RecallIntent";
    public static final String FORGET_INTENT = "ForgetIntent";
    public static final String CONVERSATION_INTENT = "ConversationIntent";
    public static final String AGENT_MEMORY_SERVER_INTENT = "AgentMemoryServerIntent";
    public static final String KNOWLEDGE_BASE_INTENT = "KnowledgeBaseIntent";

    public static final String AMAZON_YES_INTENT = "AMAZON.YesIntent";
    public static final String AMAZON_NO_INTENT = "AMAZON.NoIntent";
    public static final String AMAZON_HELP_INTENT = "AMAZON.HelpIntent";
    public static final String AMAZON_STOP_INTENT = "AMAZON.StopIntent";
    public static final String AMAZON_CANCEL_INTENT = "AMAZON.CancelIntent";
    public static final String AMAZON_FALLBACK_INTENT = "AMAZON.FallbackIntent";

    public static final String USER_ID_PARAM = "userId";
    public static final String USER_NAME_PARAM = "userName";
    public static final String MEMORY_PARAM = "memory";
    public static final String QUERY_PARAM = "query";

    public static final String AGENT_MEMORY_SERVER_URL = System.getenv("AGENT_MEMORY_SERVER_URL");
    public static final String OPENAI_API_KEY = System.getenv("OPENAI_API_KEY");
    public static final String OPENAI_EMBEDDING_MODEL_NAME = System.getenv("OPENAI_EMBEDDING_MODEL_NAME");
    public static final String OPENAI_MODEL_NAME = System.getenv("OPENAI_MODEL_NAME");
    public static final String OPENAI_CHAT_TEMPERATURE = System.getenv("OPENAI_CHAT_TEMPERATURE");
    public static final String OPENAI_CHAT_MAX_TOKENS = System.getenv("OPENAI_CHAT_MAX_TOKENS");
    public static final String KNOWLEDGE_BASE_BUCKET_NAME = System.getenv("KNOWLEDGE_BASE_BUCKET_NAME");

    public static final String USER_MEMORIES_SEARCH_LIMIT =
            (System.getenv("USER_MEMORIES_SEARCH_LIMIT") == null ||
                    System.getenv("USER_MEMORIES_SEARCH_LIMIT").isEmpty())
                    ? String.valueOf(10) : System.getenv("USER_MEMORIES_SEARCH_LIMIT");

    public static final String KNOWLEDGE_BASE_SEARCH_LIMIT =
            (System.getenv("KNOWLEDGE_BASE_SEARCH_LIMIT") == null ||
                    System.getenv("KNOWLEDGE_BASE_SEARCH_LIMIT").isEmpty())
                    ? String.valueOf(1) : System.getenv("KNOWLEDGE_BASE_SEARCH_LIMIT");
}
