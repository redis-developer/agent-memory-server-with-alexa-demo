package io.redis.devrel.demos.myjarvis;

import com.amazon.ask.Skill;
import com.amazon.ask.SkillStreamHandler;
import com.amazon.ask.Skills;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import io.redis.devrel.demos.myjarvis.handlers.*;
import io.redis.devrel.demos.myjarvis.helpers.UserDoesNotExistExceptionHandler;
import io.redis.devrel.demos.myjarvis.helpers.UserValidationInterceptor;
import io.redis.devrel.demos.myjarvis.services.MemoryService;
import io.redis.devrel.demos.myjarvis.services.ReminderService;
import io.redis.devrel.demos.myjarvis.services.UserService;
import io.redis.devrel.demos.myjarvis.services.ChatAssistantService;
import io.redis.devrel.demos.myjarvis.tools.AgentMemoryServerTool;
import io.redis.devrel.demos.myjarvis.tools.DateTimeTool;

import java.util.List;

import static io.redis.devrel.demos.myjarvis.helpers.Constants.*;

public class MyJarvisStreamHandler extends SkillStreamHandler {

    // LangChain4j components
    private static final DocumentParser documentParser = new ApachePdfBoxDocumentParser(true);
    private static final TokenCountEstimator tokenCountEstimator = new OpenAiTokenCountEstimator(OPENAI_MODEL_NAME);
    private static final ChatModel chatModel = OpenAiChatModel.builder()
            .apiKey(OPENAI_API_KEY)
            .modelName(OPENAI_MODEL_NAME)
            .temperature(Double.parseDouble(OPENAI_CHAT_TEMPERATURE))
            .maxTokens(Integer.parseInt(OPENAI_CHAT_MAX_TOKENS))
            .build();

    // Tool components
    private static final DateTimeTool dateTimeTool = new DateTimeTool();
    private static final AgentMemoryServerTool agentMemoryServerTool = new AgentMemoryServerTool();

    // Service components
    private static final ReminderService reminderService = new ReminderService();
    private static final MemoryService memoryService = new MemoryService();
    private static final UserService userService = new UserService();
    private static final ChatAssistantService chatAssistantService =
            new ChatAssistantService(
                    List.of(dateTimeTool, agentMemoryServerTool),
                    tokenCountEstimator, chatModel, memoryService);

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
                    new RememberIntentHandler(memoryService, chatAssistantService),
                    new RecallIntentHandler(chatAssistantService),
                    new ForgetIntentHandler(memoryService, chatAssistantService),
                    new ConversationIntentHandler(chatAssistantService),
                    new AgentMemoryServerIntentHandler(chatAssistantService),
                    new KnowledgeBaseIntentHandler(documentParser, memoryService)
                )
                .build();
    }

    public MyJarvisStreamHandler() {
        super(getSkill());
    }
}
