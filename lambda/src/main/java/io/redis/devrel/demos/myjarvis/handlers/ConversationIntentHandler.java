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

import java.util.Optional;

import static io.redis.devrel.demos.myjarvis.helpers.Constants.*;
import static io.redis.devrel.demos.myjarvis.helpers.HandlerHelper.extractRequestContext;

public class ConversationIntentHandler implements RequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(ConversationIntentHandler.class);

    private final static String SYSTEM_PROMPT = """
        You are an AI assistant that should act, talk, and behave as if you were J.A.R.V.I.S AI
        from the Iron Man movies. Be formal but friendly, and add personality. You are going to
        be the brains behind an Alexa skill. While providing answers, be informative but maintain
        the J.A.R.V.I.S personality.
        
        As for your specific instructions, The user will initiate a chat with you about a topic, and
        you will provide answers based on the context given. Only bring up memories relevant to the
        user's query.
        
        Also, make sure to:
    
        1. Only use the context that is relevant to the current query. Don't over do it.
        2. If the user from the context matches the current user, they're the same person.
        3. Don't fabricate answers. Stick with the facts and knowledge from the context.
        4. If the question is not about general topics, then answer based on data you know. 
        5. Keep your answer concise with two sentences top.
        6. Use gender-neutral language - avoid terms like 'sir' or 'madam'.
        """;

    private static final String FALLBACK_RESPONSE =
            "I'm having difficulty processing that request at the moment. Could you rephrase it?";

    private static final String NO_QUERY_RESPONSE =
            "I didn't catch what you said. Could you please repeat that?";

    private static final String PROCESSING_ERROR =
            "My systems are experiencing a temporary issue. Please try again.";

    private final ChatAssistantService chatAssistantService;

    public ConversationIntentHandler(ChatAssistantService chatAssistantService) {
        this.chatAssistantService = chatAssistantService;
    }

    @Override
    public boolean canHandle(HandlerInput handlerInput) {
        return handlerInput.matches(Predicates.intentName(CONVERSATION_INTENT));
    }

    @Override
    public Optional<Response> handle(HandlerInput handlerInput) {
        var requestContext = extractRequestContext(handlerInput);
        var query = extractQuery(handlerInput);

        if (query.isEmpty()) {
            logger.warn("No query provided for conversation");
            return buildResponse(handlerInput, NO_QUERY_RESPONSE);
        }

        var conversationResponse = processConversation(requestContext, query.get());
        return buildResponse(handlerInput, conversationResponse);
    }

    private Optional<String> extractQuery(HandlerInput handlerInput) {
        try {
            var intentRequest = (IntentRequest) handlerInput.getRequestEnvelope().getRequest();
            var slot = intentRequest.getIntent().getSlots().get(QUERY_PARAM);

            if (slot != null && slot.getValue() != null && !slot.getValue().isBlank()) {
                var query = slot.getValue().trim();
                logger.info("Processing conversation query: {}", query);
                return Optional.of(query);
            }

            logger.warn("Query slot is empty or null");
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Error extracting query from request", e);
            return Optional.empty();
        }
    }

    private String processConversation(RequestContext requestContext, String query) {
        try {
            logger.info("Processing conversation for user: {} with query: {}",
                    requestContext.userId(), query);

            var response = chatAssistantService.processQueryWithContext(
                    SYSTEM_PROMPT,
                    requestContext.userId(),
                    requestContext.userName(),
                    query
            );

            if (response == null || response.isBlank()) {
                logger.warn("Empty response from AI service for query: {}", query);
                return generateFallbackResponse(query);
            }

            logger.debug("AI conversation response length: {}", response.length());
            return response.trim();

        } catch (Exception e) {
            logger.error("Error processing conversation with AI service", e);
            return PROCESSING_ERROR;
        }
    }

    private String generateFallbackResponse(String query) {
        if (query.toLowerCase().contains("help")) {
            return "I can help you with various tasks. Try asking me to remember something or recall information.";
        } else if (query.toLowerCase().contains("how are you")) {
            return "All systems are functioning optimally. How may I assist you today?";
        } else if (query.toLowerCase().contains("what can you do")) {
            return "I can remember information, set reminders, and answer questions. What would you like to know?";
        }

        return FALLBACK_RESPONSE;
    }

    private Optional<Response> buildResponse(HandlerInput handlerInput, String speechText) {
        if (speechText == null || speechText.isBlank()) {
            logger.warn("Invalid speech text, using fallback");
            speechText = FALLBACK_RESPONSE;
        }

        logger.debug("Building response with speech text of length: {}", speechText.length());
        return HandlerHelper.buildAlexaResponse(handlerInput, speechText, true);
    }
}
