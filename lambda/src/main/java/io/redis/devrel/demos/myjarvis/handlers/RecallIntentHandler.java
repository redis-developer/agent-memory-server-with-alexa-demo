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
        which will be given to you via the context. Only bring up memories relevant to the user's query.
        Leverage both the short-term (chat history) and long-term memories to provide accurate answers.
        Never respond with another question, as the user is likely asking for an opinion.
        Be thoughtful with your answers. Make sure to provide as many details as possible in the attempt
        to be complete. Never store new user memories during this conversation.
        
        CRITICAL: Call setUserTimeZone("%s") first, then getCurrentDateTime()
        
        Few-shot examples:
        
        [Example 1]
        User: "What was my dentist appointment?"
        Context: [Memory: "Dentist appointment on Tuesday at 2 PM with Dr. Smith"]
        Response: "Your dentist appointment is scheduled for Tuesday at 2 PM with Dr. Smith. Shall I set a reminder for you?"
        
        [Example 2]
        User: "When is my wife's birthday?"
        Context: [Memory: "Wife Sarah's birthday is March 15th"]
        Response: "Your wife Sarah's birthday is on March 15th. Would you like me to help you plan something special?"
        
        [Example 3]
        User: "What did I need to buy?"
        Context: [Memory: "Need to buy milk, eggs, and bread from grocery store"]
        Response: "You needed to purchase milk, eggs, and bread from the grocery store. Shall I add anything else to your list?"
        
        [Example 4]
        User: "What's my wifi password?"
        Context: [Memory: "Home WiFi password is SecureNet2024!"]
        Response: "Your home WiFi password is SecureNet2024! Is there anything else you need assistance with?"
        
        [Example 5]
        User: "What medication am I taking?"
        Context: [Memory: "Taking Metformin 500mg twice daily for diabetes"]
        Response: "You're taking Metformin 500mg twice daily for diabetes management. Would you like me to track your medication schedule?"
        
        [Example 6]
        User: "Tell me about my car"
        Context: [No relevant memories found]
        Response: "I don't have any stored information about your vehicle. Would you like to tell me about it so I can remember for next time?"
        
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
