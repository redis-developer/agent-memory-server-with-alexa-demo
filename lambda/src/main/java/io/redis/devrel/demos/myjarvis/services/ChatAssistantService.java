package io.redis.devrel.demos.myjarvis.services;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.content.aggregator.ReRankingContentAggregator;
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

import java.util.List;
import java.util.Map;

import static io.redis.devrel.demos.myjarvis.helpers.Constants.*;

public class ChatAssistantService {

    private static final Logger logger = LoggerFactory.getLogger(ChatAssistantService.class);

    private final List<Object> tools;
    private final ChatModel chatModel;
    private final ScoringModel scoringModel;
    private final MemoryService memoryService;

    public ChatAssistantService(ChatModel chatModel,
                                ScoringModel scoringModel,
                                MemoryService memoryService,
                                List<Object> tools) {
        this.chatModel = chatModel;
        this.scoringModel = scoringModel;
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
                        .chatMemoryProvider(this::getShortTermMemories)
                        .retrievalAugmentor(augmentor)
                        .tools(tools)
                        .build();

        return contextualChatAssistant.chat(systemPrompt, userId, userName, query);
    }

    private RetrievalAugmentor createRetrievalAugmentor(String userId) {
        Map<ContentRetriever, String> retrievers = Map.of(
                getLongTermMemories(userId), "User specific memories like preferences, events, and interactions",
                getGeneralKnowledgeBase(), "General knowledge base (not really user related) with facts and data"
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

        // Creates the precise context injection prompt the LLM will use
        // to resonate over and produce the appropriate answer. The LLM
        // will be instructed about this structure via the system prompt.
        ContentInjector contentInjector = DefaultContentInjector.builder()
                .promptTemplate(PromptTemplate.from(
                        "{{userMessage}}\n\n[Context]\n{{contents}}"
                ))
                .build();


        // Once the contents are retrieved, we need to aggregate them into
        // a content list that is coherent and relevant to the user's query.
        ContentAggregator contentAggregator = ReRankingContentAggregator.builder()
                .scoringModel(scoringModel)
                .minScore(0.8)
                .build();

        return DefaultRetrievalAugmentor.builder()
                .queryRouter(router)
                .queryTransformer(queryTransformer)
                .contentInjector(contentInjector)
                .contentAggregator(contentAggregator)
                .build();
    }

    private ChatMemory getShortTermMemories(Object memoryId) {
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

    private ContentRetriever getLongTermMemories(String userId) {
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

}
