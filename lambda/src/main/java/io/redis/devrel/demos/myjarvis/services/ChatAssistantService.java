package io.redis.devrel.demos.myjarvis.services;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.injector.ContentInjector;
import dev.langchain4j.rag.content.injector.DefaultContentInjector;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.router.LanguageModelQueryRouter;
import dev.langchain4j.rag.query.transformer.CompressingQueryTransformer;
import dev.langchain4j.rag.query.transformer.QueryTransformer;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.redis.devrel.demos.myjarvis.extensions.WorkingMemoryChat;
import io.redis.devrel.demos.myjarvis.extensions.WorkingMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.redis.devrel.demos.myjarvis.helpers.Constants.*;
import static io.redis.devrel.demos.myjarvis.helpers.MessageHelper.messageContent;

public class ChatAssistantService {

    private static final Logger logger = LoggerFactory.getLogger(ChatAssistantService.class);

    private final List<Object> tools;
    private final ChatModel chatModel;
    private final MemoryService memoryService;

    public ChatAssistantService(ChatModel chatModel,
                                MemoryService memoryService,
                                List<Object> tools) {
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
                getAllUserMemories(userId), "User specific memories like preferences, events, and interactions",
                getGeneralKnowledgeBase(), "General knowledge base (not user related) with facts and data"
        );

        // Compress the user's query and the preceding conversation into a single query.
        // This should significantly improve the quality of the retrieval process.
        QueryTransformer queryTransformer = new CompressingQueryTransformer(chatModel);

        // This router make sure to only query the retrievers that are relevant
        // to the user query. This is more efficient in terms of context size
        LanguageModelQueryRouter router = LanguageModelQueryRouter.builder()
                .chatModel(chatModel)
                .retrieverToDescription(retrievers)
                .fallbackStrategy(LanguageModelQueryRouter.FallbackStrategy.ROUTE_TO_ALL)
                .build();

        ContentInjector contentInjector = DefaultContentInjector.builder()
                .promptTemplate(PromptTemplate.from(
                        "{{userMessage}}\n\n[Context]\n{{contents}}"
                ))
                .build();

        return DefaultRetrievalAugmentor.builder()
                .queryTransformer(queryTransformer)
                .contentInjector(contentInjector)
                .queryRouter(router)
                .build();
    }

    private ContentRetriever getAllUserMemories(String userId) {
        return query -> {
            Set<String> userMemories = new LinkedHashSet<>();

            // Add recent conversation messages
            ChatMemory chatMemory = getChatMemory(userId);
            chatMemory.messages()
                    .stream()
                    .filter(msg -> msg instanceof UserMessage)
                    .map(msg -> {
                        String content = messageContent(msg);
                        return extractOriginalMessage(content);
                    })
                    .filter(content -> !content.isBlank())
                    .forEach(userMemories::add);

            // Add long-term memories (these use semantic search)
            userMemories.addAll(memoryService.searchUserMemories(userId, query.text()));

            return userMemories.stream()
                    .map(Content::from)
                    .toList();
        };
    }

    private String extractOriginalMessage(String messageContent) {
        if (!messageContent.contains("Query: ")) {
            return messageContent;
        }

        int queryStart = messageContent.indexOf("Query: ") + 7;
        String fromQuery = messageContent.substring(queryStart);

        // Find where the actual query ends (before augmentation)
        int augmentStart = fromQuery.indexOf("\n\nAnswer using");
        if (augmentStart > 0) {
            fromQuery = fromQuery.substring(0, augmentStart).trim();
        } else {
            // No augmentation, look for newline
            int firstNewline = fromQuery.indexOf('\n');
            if (firstNewline > 0) {
                fromQuery = fromQuery.substring(0, firstNewline).trim();
            }
        }

        // Handle special cases
        if (fromQuery.startsWith("User asked to store this memory:")) {
            return fromQuery.substring("User asked to store this memory:".length()).trim();
        }

        return fromQuery;
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
                .maxContextWindow(Integer.parseInt(OPENAI_CHAT_MAX_TOKENS))
                .build();

        return WorkingMemoryChat.builder()
                .id(id)
                .chatMemoryStore(chatMemoryStore)
                .build();
    }

}
