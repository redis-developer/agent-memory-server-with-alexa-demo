package io.redis.devrel.demos.myjarvis;

import com.amazon.ask.Skill;
import com.amazon.ask.SkillStreamHandler;
import com.amazon.ask.Skills;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import io.redis.devrel.demos.myjarvis.handlers.*;
import io.redis.devrel.demos.myjarvis.helpers.UserDoesNotExistExceptionHandler;
import io.redis.devrel.demos.myjarvis.helpers.UserValidationInterceptor;
import io.redis.devrel.demos.myjarvis.services.ChatAssistantService;
import io.redis.devrel.demos.myjarvis.services.MemoryService;
import io.redis.devrel.demos.myjarvis.services.ReminderService;
import io.redis.devrel.demos.myjarvis.services.UserService;
import io.redis.devrel.demos.myjarvis.tools.AgentMemoryServerTool;
import io.redis.devrel.demos.myjarvis.tools.DateTimeTool;
import io.redis.devrel.demos.myjarvis.tools.UserMemoryTool;

import java.util.List;

import static io.redis.devrel.demos.myjarvis.helpers.Constants.*;

public class MyJarvisStreamHandler extends SkillStreamHandler {

    // LangChain4j components
    private static final DocumentParser documentParser = new ApachePdfBoxDocumentParser(true);
    private static final DocumentSplitter documentSplitter = new DocumentByParagraphSplitter(
            Integer.parseInt(MAX_SEGMENT_SIZE_IN_CHARS),
            Integer.parseInt(MAX_SEGMENT_OVERLAP_IN_CHARS)
    );
    private static final EmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
            .apiKey(OPENAI_API_KEY)
            .modelName(OPENAI_EMBEDDING_MODEL_NAME)
            .build();

    private static final ChatModel chatModel = OpenAiChatModel.builder()
            .apiKey(OPENAI_API_KEY)
            .modelName(OPENAI_MODEL_NAME)
            .temperature(Double.parseDouble(OPENAI_CHAT_TEMPERATURE))
            .maxTokens(Integer.parseInt(OPENAI_CHAT_MAX_TOKENS))
            .build();

    // Service components
    private static final ReminderService reminderService = new ReminderService();
    private static final MemoryService memoryService = new MemoryService();
    private static final UserService userService = new UserService();
    private static final ChatAssistantService chatAssistantService =
            new ChatAssistantService(
                    chatModel, memoryService,
                    List.of(
                            new DateTimeTool(),
                            new AgentMemoryServerTool(),
                            new UserMemoryTool(memoryService))
            );

    public MyJarvisStreamHandler() {
        super(getSkill());
    }

    private static Skill getSkill() {
        return Skills.standard()
                .addRequestInterceptor(new UserValidationInterceptor(userService))
                .addExceptionHandler(new UserDoesNotExistExceptionHandler())
                .addRequestHandlers(
                        new YesIntentHandler(reminderService),
                        new NoIntentHandler(),
                        new LaunchRequestHandler(),
                        new CancelAndStopIntentHandler(),
                        new FallbackIntentHandler(),
                        new HelpIntentHandler(),
                        new UserIntroIntentHandler(userService, chatAssistantService),
                        new RememberIntentHandler(chatAssistantService),
                        new ForgetIntentHandler(memoryService, chatAssistantService),
                        new ConversationIntentHandler(chatAssistantService),
                        new AgentMemoryServerIntentHandler(chatAssistantService),
                        new KnowledgeBaseIntentHandler(documentParser, documentSplitter, memoryService)
                )
                .build();
    }
}
