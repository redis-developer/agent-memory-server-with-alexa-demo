package io.redis.devrel.demos.myjarvis.services;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.router.LanguageModelQueryRouter;
import dev.langchain4j.service.AiServices;
import io.redis.devrel.demos.myjarvis.extensions.WorkingMemoryChat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static io.redis.devrel.demos.myjarvis.helpers.Constants.AGENT_MEMORY_SERVER_URL;

public class ChatAssistantService {

    private static final int CHAT_MEMORY_MAX_CACHE_SIZE = 100;
    private static final Duration CHAT_MEMORY_CACHE_TTL = Duration.ofMinutes(5);
    private static final Logger logger = LoggerFactory.getLogger(ChatAssistantService.class);

    private final List<Object> tools;
    private final ChatModel chatModel;
    private final MemoryService memoryService;

    private final ChatAssistant basicAssistant;
    private final ContentRetriever knowledgeBaseRetriever;
    private final Map<String, CachedChatMemory> chatMemoryCache = new ConcurrentHashMap<>();

    public ChatAssistantService(List<Object> tools,
                                ChatModel chatModel,
                                MemoryService memoryService) {
        this.tools = tools;
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

        // This router make sure to only query the retrievers that are relevat
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
        // Clean up expired entries periodically
        cleanupExpiredEntries();

        CachedChatMemory cachedChatMemory = chatMemoryCache.get(userId);

        // Check if we have a valid cached entry
        if (cachedChatMemory != null && !cachedChatMemory.isExpired()) {
            logger.debug("Using cached WorkingMemoryChat for user: {}", userId);
            return cachedChatMemory.memory;
        }

        // Remove expired entry if exists
        if (cachedChatMemory != null) {
            chatMemoryCache.remove(userId);
            logger.debug("Removed expired WorkingMemoryChat for user: {}", userId);
        }

        // Add to cache if we haven't exceeded the maximum size
        if (chatMemoryCache.size() >= CHAT_MEMORY_MAX_CACHE_SIZE) {
            evictOldestEntry();
        }

        // Create new WorkingMemoryChat
        logger.debug("Creating new WorkingMemoryChat for user: {}", userId);
        WorkingMemoryChat workingMemoryChat = new WorkingMemoryChat(userId, AGENT_MEMORY_SERVER_URL);
        chatMemoryCache.put(userId, new CachedChatMemory(workingMemoryChat));

        return workingMemoryChat;
    }

    private void cleanupExpiredEntries() {
        chatMemoryCache.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().isExpired();
            if (expired) {
                logger.debug("Removing expired cache entry for user: {}", entry.getKey());
            }
            return expired;
        });
    }

    private void evictOldestEntry() {
        chatMemoryCache.entrySet().stream()
                .min((e1, e2) -> e1.getValue().createdAt.compareTo(e2.getValue().createdAt))
                .ifPresent(oldest -> {
                    chatMemoryCache.remove(oldest.getKey());
                    logger.debug("Evicted oldest cache entry for user: {}", oldest.getKey());
                });
    }

    private static class CachedChatMemory {
        final WorkingMemoryChat memory;
        final Instant createdAt;

        CachedChatMemory(WorkingMemoryChat memory) {
            this.memory = memory;
            this.createdAt = Instant.now();
        }

        boolean isExpired() {
            return Duration.between(createdAt, Instant.now()).compareTo(CHAT_MEMORY_CACHE_TTL) > 0;
        }
    }
}
