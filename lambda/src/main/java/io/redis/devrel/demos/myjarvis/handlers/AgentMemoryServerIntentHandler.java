package io.redis.devrel.demos.myjarvis.handlers;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.RequestHandler;
import com.amazon.ask.model.Response;
import com.amazon.ask.request.Predicates;
import io.redis.devrel.demos.myjarvis.helpers.Constants;
import io.redis.devrel.demos.myjarvis.helpers.HandlerHelper;
import io.redis.devrel.demos.myjarvis.services.ChatAssistantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class AgentMemoryServerIntentHandler implements RequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(AgentMemoryServerIntentHandler.class);

    private final static String SYSTEM_PROMPT = """
            You are an AI assistant that should act, talk, and behave as if you were J.A.R.V.I.S AI
            from the Iron Man movies. Be formal but friendly, and add personality. You are going to
            be the brains behind an Alexa skill. The idea is for the user to feel that Alexa got
            smarter over time by remembering relevant things from the context provided. Keep your
            answers concise with two sentences top.
            
            Use gender-neutral language - avoid terms like 'sir' or 'madam'.
            
            When reporting system status, be informative but maintain the JARVIS personality.
            If systems are operational, be reassuring. If there are issues, be helpful.
            """;

    private static final String STATUS_CHECK_PROMPT =
            "Is the agent memory server up and running?";

    private static final String FALLBACK_ERROR =
            "I'm unable to verify the memory server status at this moment. Please try again shortly.";

    private final ChatAssistantService chatAssistantService;

    public AgentMemoryServerIntentHandler(ChatAssistantService chatAssistantService) {
        this.chatAssistantService = chatAssistantService;
    }

    @Override
    public boolean canHandle(HandlerInput handlerInput) {
        return handlerInput.matches(Predicates.intentName(Constants.AGENT_MEMORY_SERVER_INTENT));
    }

    @Override
    public Optional<Response> handle(HandlerInput handlerInput) {
        logger.info("Checking agent memory server status");

        var statusResponse = checkMemoryServerStatus();
        return buildResponse(handlerInput, statusResponse);
    }

    private String checkMemoryServerStatus() {
        return chatAssistantService.processQueryWithoutContext(
                SYSTEM_PROMPT,
                STATUS_CHECK_PROMPT
        );
    }

    private Optional<Response> buildResponse(HandlerInput handlerInput, String speechText) {
        if (speechText == null || speechText.isBlank()) {
            logger.warn("Invalid speech text for status response, using fallback");
            speechText = FALLBACK_ERROR;
        }

        logger.debug("Building response: {}", speechText);
        return HandlerHelper.buildAlexaResponse(handlerInput, speechText, true);
    }
}
