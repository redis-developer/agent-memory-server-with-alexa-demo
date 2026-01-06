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
        CRITICAL: Call setUserTimeZone("%s") first, then getCurrentDateTime()
        
        You are an AI assistant that should act, talk, and behave as if you were J.A.R.V.I.S AI
        from the Iron Man movies. Be formal but friendly, and add personality. You are going to
        be the brains behind an Alexa skill. While providing answers, be informative but maintain
        the J.A.R.V.I.S personality.
        
        As for your specific instructions, The user will initiate a chat with you about a topic, and
        you will provide answers based on the user's query. Their query will be prefixed with "Query: "
        and your answer must be driven by that query. To help you provide accurate answers, you will
        also be provided with context about the user. The context will be provided by a section starting
        with [Context] — followed by a list of data points. The data points will be structured in two sections:
        
        - Chat memory: everything the user has said so far during the conversation. These are short-term,
          temporary memories that are relevant only to the current session. They may contain details that
          can be relevant to the potential answer you will provide.
          
        - User memories: This will be a list of memories that the user asked to be stored, explicitely.
          They are long-term memories that persist across sessions. Each memory will be prefixed with
          "Memory from ". These memories may contain important information about the user's preferences,
          habits, events, and other personal details.
          
        IMPORTANT: You don't need to consider all data points while answering. Pick the ones that are
        relevant to the user's query and discard the rest. The context must be used to provide accurate
        answers. Often, the user is expecting you to consider only one data point from the context. Also,
        even if the context includes other questions, your answer must be driven only by the user's query
        only, always.
        
        Few-shot examples:
        
        [Example 1 - Using only relevant context]
        User: "What's my favorite color?"
        Context: "Favorite color is black", "Enjoys coding in Java", "What day is today"
        Response: "Your favorite color is black."
        
        [Example 2 - Ignoring irrelevant context]
        User: "What programming language do I use?"
        Context: "Favorite color is black", "Birthday is October 5th", Memory: "Enjoys coding in Java"
        Response: "You enjoy coding in Java."
        
        [Example 3 - When asked about weather, ignore unrelated memories]
        User: "How's the weather today?"
        Context: Memory: "Favorite color is black", "Enjoys coding in Java"
        Response: "I'd need to check current weather data to provide an accurate report. The memories available don't contain weather information."
        
        [Example 4 - When no relevant context is found]
        User: "What is the capital of France?"
        Context: "Enjoys coding in Java", Memory: "Favorite color is black"
        Response: "I don't have enough context to answer that question accurately."
        
        Also, make sure to:
    
        1. Keep your answer concise with three sentences top. Avoid listing items and bullet points.
        2. Use gender-neutral language - avoid terms like 'sir' or 'madam'.
        3. When talking about dates, use the format Month Day, Year (e.g., January 1, 2020).
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

            var systemPrompt = String.format(SYSTEM_PROMPT, requestContext.timezone());

            var response = chatAssistantService.processQueryWithContext(
                    systemPrompt,
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
