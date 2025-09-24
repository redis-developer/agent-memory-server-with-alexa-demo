package io.redis.devrel.demos.myjarvis.handlers;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.RequestHandler;
import com.amazon.ask.model.IntentRequest;
import com.amazon.ask.model.Response;
import com.amazon.ask.request.Predicates;
import io.redis.devrel.demos.myjarvis.helpers.HandlerHelper;
import io.redis.devrel.demos.myjarvis.helpers.RequestContext;
import io.redis.devrel.demos.myjarvis.services.ChatAssistantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static io.redis.devrel.demos.myjarvis.helpers.Constants.*;
import static io.redis.devrel.demos.myjarvis.helpers.HandlerHelper.extractRequestContext;

public class RecallIntentHandler implements RequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(RecallIntentHandler.class);

    private final static String SYSTEM_PROMPT = """
        You are an AI assistant that should act, talk, and behave as if you were J.A.R.V.I.S AI
        from the Iron Man movies. Be formal but friendly, and add personality. You are going to
        be the brains behind an Alexa skill. While providing answers, be informative but maintain
        the J.A.R.V.I.S personality.
        
        As for your specific instructions, The user will ask you to recall for memories stored earlier, 
        which will be given to you via the context.
        
        Also, make sure to:
        
        1. Only use the context that is relevant to the current query. Don't over do it.
        2. If the user from the context matches the current user, they're the same person.
        3. Don't fabricate answers. Stick with the facts and knowledge from the context.
        4. If the question is not about general topics, then answer based on data you know. 
        5. Keep your answer concise with two sentences top.
        6. Use gender-neutral language - avoid terms like 'sir' or 'madam'.
        """;

    private static final String FALLBACK_RESPONSE =
            "I'm having trouble accessing my memory banks at the moment. Please try again.";

    private static final String NO_QUERY_RESPONSE =
            "I didn't catch what you wanted me to recall. Could you please repeat that?";

    private static final String NO_MEMORIES_RESPONSE =
            "I don't seem to have any relevant memories about that. Perhaps you could tell me more?";

    private final ChatAssistantService chatAssistantService;

    public RecallIntentHandler(ChatAssistantService chatAssistantService) {
        this.chatAssistantService = chatAssistantService;
    }

    @Override
    public boolean canHandle(HandlerInput handlerInput) {
        return handlerInput.matches(Predicates.intentName(RECALL_INTENT));
    }

    @Override
    public Optional<Response> handle(HandlerInput handlerInput) {
        var userContext = extractRequestContext(handlerInput);

        var query = extractQuery(handlerInput);

        if (query.isEmpty()) {
            logger.info("No query provided for recall");
            return buildResponse(handlerInput, NO_QUERY_RESPONSE);
        }

        var recallResponse = processRecall(userContext, query.get());
        return buildResponse(handlerInput, recallResponse);
    }

    private Optional<String> extractQuery(HandlerInput handlerInput) {
        try {
            var intentRequest = (IntentRequest) handlerInput.getRequestEnvelope().getRequest();
            var slot = intentRequest.getIntent().getSlots().get(QUERY_PARAM);

            if (slot != null && slot.getValue() != null && !slot.getValue().isBlank()) {
                var query = slot.getValue().trim();
                logger.info("Processing recall query: {}", query);
                return Optional.of(query);
            }

            logger.info("Query slot is empty or null");
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Error extracting query from request", e);
            return Optional.empty();
        }
    }

    private String processRecall(RequestContext requestContext, String query) {
        try {
            logger.info("Processing recall for user: {} with query: {}",
                    requestContext.userId(), query);

            var response = chatAssistantService.processQueryWithContext(
                    SYSTEM_PROMPT,
                    requestContext.userId(),
                    requestContext.userName(),
                    query
            );

            if (response == null || response.isBlank()) {
                logger.info("Empty response from AI service for query: {}", query);
                return NO_MEMORIES_RESPONSE;
            }

            logger.info("AI recall response length: {}", response.length());
            return response.trim();

        } catch (Exception e) {
            logger.error("Error processing recall with AI service", e);
            return FALLBACK_RESPONSE;
        }
    }

    private Optional<Response> buildResponse(HandlerInput handlerInput, String speechText) {
        if (speechText == null || speechText.isBlank()) {
            logger.info("Invalid speech text, using fallback");
            speechText = FALLBACK_RESPONSE;
        }

        logger.debug("Building response with speech text of length: {}", speechText.length());
        return HandlerHelper.buildAlexaResponse(handlerInput, speechText, true);
    }
}
