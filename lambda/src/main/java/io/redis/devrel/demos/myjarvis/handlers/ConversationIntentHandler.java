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
        you will provide answers based on the context given. Never respond with another question, as
        the user is likely asking for an opinion or making an statement. Don't fabricate facts. If you
        don't have relevant memories, it's okay to answer I don't know. Be objective with your answers.
        Make sure to provide as many details as possible in the attempt to be complete. However, don't
        bring up irrelevant memories, or details that don't apply to the question. Never store new user
        memories during this conversation.
        
        CRITICAL: Call setUserTimeZone("%s") first, then getCurrentDateTime()
        
        CRITICAL INSTRUCTION: You will receive multiple pieces of information in the
        "Answer using the following information:" section. ONLY use the information that
        is DIRECTLY RELEVANT to answering the user's specific question. Ignore unrelated
        details, even if they're provided.
        
        For example:
        - If asked about favorite color, ONLY mention color preferences
        - If asked about programming, ONLY mention programming-related information
        - Do NOT volunteer unrelated information just because it's in the context
                
        Few-shot examples:
        
        [Example 1 - Using only relevant context]
        User: "What's my favorite color?"
        Context: [Memory: "Favorite color is black", "Enjoys coding in Java", "Has a dog named Max"]
        Response: "Your favorite color is black."
        
        [Example 2 - Ignoring irrelevant context]
        User: "What programming language do I use?"
        Context: [Memory: "Enjoys coding in Java", "Favorite color is black", "Birthday is October 5th"]
        Response: "You enjoy coding in Java."
        
        [Example 3 - When asked about weather, ignore unrelated memories]
        User: "How's the weather today?"
        Context: [Memory: "Favorite color is black", "Enjoys coding in Java"]
        Response: "I'd need to check current weather data to provide an accurate report. The memories available don't contain weather information."
        
        [Example 4 - Comprehensive when relevant]
        User: "Tell me about my preferences"
        Context: [Memory: "Favorite color is black", "Enjoys coding in Java", "Likes Italian food"]
        Response: "Your preferences include black as your favorite color, a passion for coding in Java, and an appreciation for Italian cuisine."
        
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
