package io.redis.devrel.demos.myjarvis.services;

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

public class ChatAssistantService {

    private static final Logger logger = LoggerFactory.getLogger(ChatAssistantService.class);

    private final List<Object> tools;
    private final TokenCountEstimator tokenCountEstimator;
    private final ChatModel chatModel;
    private final MemoryService memoryService;

    private final ChatAssistant basicAssistant;
    private final ContentRetriever knowledgeBaseRetriever;

    public ChatAssistantService(List<Object> tools,
                                TokenCountEstimator tokenCountEstimator,
                                ChatModel chatModel,
                                MemoryService memoryService) {
        this.tools = tools;
        this.tokenCountEstimator = tokenCountEstimator;
        this.chatModel = chatModel;
        this.memoryService = memoryService;

        this.basicAssistant = createBasicAssistant();
        this.knowledgeBaseRetriever = createKnowledgeBaseRetriever();

        logger.info("ChatAssistantService singleton initialized with {} tools", tools.size());
    }

    public String processQueryWithoutContext(String systemPrompt, String query) {
        logger.debug("Processing query without context {}", query);
        return basicAssistant.chat(systemPrompt, query);
    }

    public String processQueryWithContext(String systemPrompt,
                                          String userId,
                                          String userName,
                                          String query) {
        logger.debug("Processing query with context for user: {}", userId);

        ChatMemory chatMemory = getChatMemory(userId);
        RetrievalAugmentor augmentor = createRetrievalAugmentor(userId);
        ChatAssistant contextualAssistant = createContextualAssistant(chatMemory, augmentor);

        return contextualAssistant.chat(systemPrompt, userId, userName, query);
    }

    private ChatAssistant createBasicAssistant() {
        return AiServices.builder(ChatAssistant.class)
                .chatModel(chatModel)
                .tools(tools)
                .build();
    }

    private ChatAssistant createContextualAssistant(ChatMemory memory,
                                                    RetrievalAugmentor augmentor) {
        return AiServices.builder(ChatAssistant.class)
                .chatModel(chatModel)
                .chatMemory(memory)
                .retrievalAugmentor(augmentor)
                .tools(tools)
                .build();
    }

    private RetrievalAugmentor createRetrievalAugmentor(String userId) {
        ContentRetriever userMemoryRetriever = createUserMemoryRetriever(userId);

        Map<ContentRetriever, String> retrievers = Map.of(
                userMemoryRetriever, "user specific long-term memories",
                knowledgeBaseRetriever, "general knowledge base with facts"
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

    private ContentRetriever createUserMemoryRetriever(String userId) {
        return query -> memoryService.searchUserMemories(userId, query.text())
                .stream()
                .map(Content::from)
                .toList();
    }

    private ContentRetriever createKnowledgeBaseRetriever() {
        return query -> memoryService.searchKnowledgeBase(query.text())
                .stream()
                .map(Content::from)
                .toList();
    }

    private ChatMemory getChatMemory(String userId) {
        logger.debug("Creating new WorkingMemoryChat for user: {}", userId);
        ChatMemoryStore chatMemoryStore = WorkingMemoryStore.builder()
                .withAgentMemoryServerUrl(AGENT_MEMORY_SERVER_URL)
                .build();

        return TokenLimitingChatMemory.builder()
                .withSessionId(userId)
                .withChatMemoryStore(chatMemoryStore)
                .withMaxTokens(Integer.parseInt(OPENAI_CHAT_MAX_TOKENS))
                .withTokenEstimator(tokenCountEstimator)
                .build();
    }

}
