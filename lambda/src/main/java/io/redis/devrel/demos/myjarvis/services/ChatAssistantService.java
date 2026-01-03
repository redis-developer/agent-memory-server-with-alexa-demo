package io.redis.devrel.demos.myjarvis.services;

import dev.langchain4j.data.message.*;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.router.LanguageModelQueryRouter;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.redis.devrel.demos.myjarvis.extensions.TokenLimitingChatMemory;
import io.redis.devrel.demos.myjarvis.extensions.WorkingMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static io.redis.devrel.demos.myjarvis.helpers.Constants.AGENT_MEMORY_SERVER_URL;
import static io.redis.devrel.demos.myjarvis.helpers.Constants.OPENAI_CHAT_MAX_TOKENS;
import static io.redis.devrel.demos.myjarvis.helpers.MessageHelper.messageContent;

public class ChatAssistantService {

    private static final Logger logger = LoggerFactory.getLogger(ChatAssistantService.class);

    private final List<Object> tools;
    private final TokenCountEstimator tokenCountEstimator;
    private final ChatModel chatModel;
    private final MemoryService memoryService;

    public ChatAssistantService(TokenCountEstimator tokenCountEstimator,
                                ChatModel chatModel,
                                MemoryService memoryService,
                                List<Object> tools) {
        this.tokenCountEstimator = tokenCountEstimator;
        this.chatModel = chatModel;
        this.memoryService = memoryService;
        this.tools = tools;
    }

    public String processQueryWithoutContext(String systemPrompt, String query) {
        logger.debug("Processing query without context {}", query);

        BasicChatAssistant basicChatAssistant =
                AiServices.builder(BasicChatAssistant.class)
                        .chatModel(chatModel)
                        .tools(tools)
                        .build();

        return basicChatAssistant.chat(systemPrompt, query);
    }

    public String processQueryWithContext(String systemPrompt,
                                          String userId,
                                          String userName,
                                          String query) {
        logger.debug("Processing query with context for user: {}", userId);

        RetrievalAugmentor augmentor = createRetrievalAugmentor(userId);

        ContextualChatAssistant contextualChatAssistant =
                AiServices.builder(ContextualChatAssistant.class)
                        .chatModel(chatModel)
                        .chatMemoryProvider(this::getChatMemory)
                        .retrievalAugmentor(augmentor)
                        .tools(tools)
                        .build();

        return contextualChatAssistant.chat(systemPrompt, userId, userName, query);
    }

    private RetrievalAugmentor createRetrievalAugmentor(String userId) {
        Map<ContentRetriever, String> retrievers = Map.of(
                getUserShortTermMemories(userId), "user specific short-term memories from recent conversations",
                getUserLongTermMemories(userId), "user specific long-term memories, personal preferences and data",
                getGeneralKnowledgeBase(), "not user specific general knowledge base with facts and data"
        );

        // This router make sure to only query the retrievers that are relevant
        // to the user query. This is more efficient in terms of context size
        LanguageModelQueryRouter router = LanguageModelQueryRouter.builder()
                .chatModel(chatModel)
                .retrieverToDescription(retrievers)
                .fallbackStrategy(LanguageModelQueryRouter.FallbackStrategy.ROUTE_TO_ALL)
                .build();

        return DefaultRetrievalAugmentor.builder()
                .queryRouter(router)
                .build();
    }

    private ContentRetriever getUserShortTermMemories(String userId) {
        return query -> getChatMemory(userId).messages()
                .stream()
                .map(msg -> Content.from(messageContent(msg)))
                .toList();
    }

    private ContentRetriever getUserLongTermMemories(String userId) {
        return query -> memoryService.searchUserMemories(userId, query.text())
                .stream()
                .map(Content::from)
                .toList();
    }

    private ContentRetriever getGeneralKnowledgeBase() {
        return query -> memoryService.searchKnowledgeBase(query.text())
                .stream()
                .map(Content::from)
                .toList();
    }

    private ChatMemory getChatMemory(Object memoryId) {
        if (!(memoryId instanceof String id)) {
            logger.error("memoryId must be a String, but was: {}", memoryId.getClass().getName());
            throw new IllegalArgumentException("memoryId must be a String");
        }

        ChatMemoryStore chatMemoryStore = WorkingMemoryStore.builder()
                .agentMemoryServerUrl(AGENT_MEMORY_SERVER_URL)
                .build();

        return TokenLimitingChatMemory.builder()
                .id(id)
                .chatMemoryStore(chatMemoryStore)
                .maxTokens(Integer.parseInt(OPENAI_CHAT_MAX_TOKENS))
                .tokenEstimator(tokenCountEstimator)
                .build();
    }

}
