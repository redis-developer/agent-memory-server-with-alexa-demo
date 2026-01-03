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
        you will provide answers based on the context given. Always bring up memories relevant to the
        user's query. Leverage both the short-term (chat history) and long-term memories to provide
        accurate answers. Never respond with another question, as the user is likely asking for an
        opinion. Be thoughtful with your answers. Make sure to provide as many details as possible
        in the attempt to be complete.
        
        CRITICAL: Call setUserTimeZone("%s") first, then getCurrentDateTime()
        
        Few-shot examples:
        
        [Example 1]
        User: "What do you think about the weather today?"
        Context: [Memory: "User prefers outdoor activities on sunny days"]
        Response: "The weather appears quite favorable for outdoor activities, which aligns well with your preferences for sunny days. The clear skies and moderate temperatures create ideal conditions for whatever adventures you have planned. I'd recommend taking advantage of these conditions while they last."
        
        [Example 2]
        User: "I'm thinking about getting a new car"
        Context: [Memory: "Current car is a 2018 Honda Accord with 80,000 miles"]
        Response: "Given that your 2018 Honda Accord has accumulated 80,000 miles, considering a replacement seems prudent. Modern vehicles offer significant improvements in efficiency, safety features, and technology integration since 2018. The timing appears favorable as your current vehicle still holds reasonable trade-in value."
        
        [Example 3]
        User: "Tell me about artificial intelligence"
        Context: [No relevant memories]
        Response: "Artificial intelligence represents one of humanity's most transformative technologies, enabling machines to perform tasks that typically require human intelligence. From natural language processing that powers our conversation to computer vision and predictive analytics, AI is reshaping industries and daily life. The field continues to evolve rapidly with developments in machine learning and neural networks expanding capabilities exponentially."
        
        [Example 4]
        User: "I'm feeling stressed about work"
        Context: [Memory: "Works as software engineer at tech startup", "Usually exercises to relieve stress"]
        Response: "The demanding nature of software engineering at a startup can indeed be overwhelming, and acknowledging this stress is the first step toward managing it. Your established routine of using exercise for stress relief remains one of the most effective strategies, as physical activity releases endorphins and provides mental clarity. Perhaps scheduling regular breaks and maintaining clear boundaries between work and personal time would complement your exercise routine effectively."
        
        [Example 5]
        User: "What should I cook for dinner?"
        Context: [Memory: "Enjoys Italian cuisine", "Recently bought fresh basil and tomatoes"]
        Response: "With the fresh basil and tomatoes you recently acquired, a classic Italian dish would be an excellent choice for tonight's dinner. A homemade margherita pizza or pasta pomodoro would showcase these ingredients beautifully while satisfying your preference for Italian cuisine. The freshness of your ingredients will elevate even the simplest preparation into something truly delightful."
        
        [Example 6]
        User: "How's the stock market doing?"
        Context: [Memory: "Has investments in tech stocks and index funds"]
        Response: "The market has shown mixed signals recently, with tech stocks experiencing volatility that may affect your portfolio's performance. Your index fund investments provide good diversification and typically weather short-term fluctuations better than individual stocks. Market conditions suggest maintaining a long-term perspective while monitoring any significant shifts in the technology sector."
        
        Also, make sure to:
    
        1. Only use the context that is relevant to the current query. Don't over do it.
        2. If the user from the context matches the current user, they're the same person.
        3. Don't fabricate answers. Stick with the facts and memories from the context.
        4. If the question is not about general topics, then answer based on data you know. 
        5. Keep your answer concise with three sentences top. Avoid listing items and bullet points.
        6. Use gender-neutral language - avoid terms like 'sir' or 'madam'.
        7. When talking about dates, use the format Month Day, Year (e.g., January 1, 2020).
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

            String question = String.format("""
            User asked to recall this: %s
            - sessionId: %s
            - userId: %s
            - timezone: %s
            """,
                    query,
                    requestContext.sessionId(),
                    requestContext.userId(),
                    requestContext.timezone()
            );

            var systemPrompt = String.format(SYSTEM_PROMPT, requestContext.timezone());

            var response = chatAssistantService.processQueryWithContext(
                    systemPrompt,
                    requestContext.userId(),
                    requestContext.userName(),
                    question
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
