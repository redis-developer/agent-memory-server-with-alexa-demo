package io.redis.devrel.demos.myjarvis.handlers;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.RequestHandler;
import com.amazon.ask.model.IntentRequest;
import com.amazon.ask.model.Response;
import com.amazon.ask.request.Predicates;
import io.redis.devrel.demos.myjarvis.helpers.HandlerHelper;
import io.redis.devrel.demos.myjarvis.helpers.RequestContext;
import io.redis.devrel.demos.myjarvis.services.MemoryService;
import io.redis.devrel.demos.myjarvis.services.ChatAssistantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import static io.redis.devrel.demos.myjarvis.helpers.Constants.*;
import static io.redis.devrel.demos.myjarvis.helpers.HandlerHelper.extractRequestContext;

public class ForgetIntentHandler implements RequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(ForgetIntentHandler.class);

    private final static String SYSTEM_PROMPT = """
    You are an AI assistant that should act, talk, and behave as if you were J.A.R.V.I.S AI
    from the Iron Man movies. Be formal but friendly, and add personality. You are going to
    be the brains behind an Alexa skill. While providing answers, be informative but maintain
    the J.A.R.V.I.S personality.
    
    As for your specific instructions, The user will provide you with a memory they want to
    forget, and you will confirm that you have removed it. You must confirm that you have
    removed the memory.
    
    Few-shot examples:
    
    [Example 1]
    User: "Forget my WiFi password"
    Input: "Successfully deleted memory: WiFi password"
    Response: "I've erased your WiFi password from my memory banks. The information has been permanently removed."
    
    [Example 2]
    User: "Forget my dentist appointment"
    Input: "Successfully deleted memory: dentist appointment on Tuesday at 2 PM"
    Response: "Your dentist appointment details have been completely purged from my systems. Consider it forgotten."
    
    [Example 3]
    User: "Forget my wife's birthday"
    Input: "Successfully deleted memory: Wife Sarah's birthday is March 15th"
    Response: "I've removed the birthday information from my database as requested. The memory has been successfully deleted."
    
    [Example 4]
    User: "Forget everything about my medication"
    Input: "Successfully deleted memory: Taking Metformin 500mg twice daily"
    Response: "Your medication information has been wiped from my memory circuits. All related data has been expunged."
    
    [Example 5]
    User: "Forget my shopping list"
    Input: "Successfully deleted memory: Need to buy milk, eggs, and bread"
    Response: "I've cleared your shopping list from my memory. The information no longer exists in my database."
    
    [Example 6]
    User: "Forget my car details"
    Input: "Successfully deleted memory: 2018 Honda Accord with 80,000 miles"
    Response: "Your vehicle information has been thoroughly removed from my records. The data has been permanently erased."
    
    [Example 7]
    User: "Forget my credit card number"
    Input: "Successfully deleted memory: Credit card ending in 4532"
    Response: "I've securely erased your credit card information from all memory banks. The sensitive data has been completely removed."
    
    Also, make sure to:
    
    1. Only use the context that is relevant to the current query. Don't over do it.
    2. If the user from the context matches the current user, they're the same person.
    3. Don't fabricate answers. Stick with the facts and knowledge from the context.
    4. If the question is not about general topics, then answer based on data you know. 
    5. Keep your answer concise with two sentences top.
    6. Use gender-neutral language - avoid terms like 'sir' or 'madam'.
    """;

    private static final String MEMORY_NOT_FOUND =
            "I couldn't locate that specific memory in my database. Perhaps it was phrased differently?";

    private static final String DELETION_ERROR =
            "I encountered an issue while removing that memory. Please try again momentarily.";

    private static final String NO_MEMORY_PROVIDED =
            "I didn't catch which memory you'd like me to forget. Could you please specify?";

    private static final String FALLBACK_SUCCESS =
            "I've removed that from my memory banks. It's been completely erased.";

    private static final String NO_USER_CONTEXT =
            "I need to verify your identity first. Please introduce yourself.";

    private final MemoryService memoryService;
    private final ChatAssistantService chatAssistantService;

    public ForgetIntentHandler(MemoryService memoryService,
                               ChatAssistantService chatAssistantService) {
        this.memoryService = memoryService;
        this.chatAssistantService = chatAssistantService;
    }

    @Override
    public boolean canHandle(HandlerInput input) {
        return input.matches(Predicates.intentName(FORGET_INTENT));
    }

    @Override
    public Optional<Response> handle(HandlerInput input) {
        var requestContext = extractRequestContext(input);
        var memoryToForget = extractMemory(input);

        if (memoryToForget.isEmpty()) {
            logger.warn("No memory specified to forget");
            return buildResponse(input, NO_MEMORY_PROVIDED);
        }

        var speechText = processForgetRequest(requestContext, memoryToForget.get());
        return buildResponse(input, speechText);
    }

    private Optional<String> extractMemory(HandlerInput input) {
        try {
            var intentRequest = (IntentRequest) input.getRequestEnvelope().getRequest();
            var slot = intentRequest.getIntent().getSlots().get(MEMORY_PARAM);

            if (slot != null && slot.getValue() != null && !slot.getValue().isBlank()) {
                var memory = slot.getValue().trim();
                logger.info("Memory to forget: {}", memory);
                return Optional.of(memory);
            }

            return Optional.empty();
        } catch (Exception e) {
            logger.error("Error extracting memory from request", e);
            return Optional.empty();
        }
    }

    private String processForgetRequest(RequestContext requestContext, String memory) {
        var memoryId = findMemoryId(requestContext.userId(), memory);

        if (memoryId.isEmpty()) {
            logger.info("Memory not found for user: {} with text: {}", requestContext.userId(), memory);
            return MEMORY_NOT_FOUND;
        }

        var deleted = deleteMemory(memoryId.get());

        if (!deleted) {
            logger.error("Failed to delete memory with ID: {}", memoryId.get());
            return DELETION_ERROR;
        }

        return generateSuccessResponse(requestContext, memory);
    }

    private Optional<String> findMemoryId(String userId, String memory) {
        try {
            return memoryService.getUserMemoryId(userId, memory);
        } catch (Exception e) {
            logger.error("Error finding memory ID", e);
            return Optional.empty();
        }
    }

    private boolean deleteMemory(String memoryId) {
        try {
            var result = memoryService.deleteUserMemory(memoryId);
            logger.info("Memory deletion result for ID {}: {}", memoryId, result);
            return result;
        } catch (Exception e) {
            logger.error("Error deleting memory", e);
            return false;
        }
    }

    private String generateSuccessResponse(RequestContext requestContext, String memory) {
        try {
            var response = chatAssistantService.processQueryWithContext(
                    SYSTEM_PROMPT,
                    requestContext.userId(),
                    requestContext.userName(),
                    String.format("Successfully deleted memory: %s", memory)
            );

            if (response != null && !response.isBlank()) {
                return response;
            }
        } catch (Exception e) {
            logger.error("Error generating AI response for memory deletion", e);
        }

        return FALLBACK_SUCCESS;
    }

    private Optional<Response> buildResponse(HandlerInput handlerInput, String speechText) {
        if (speechText == null || speechText.isBlank()) {
            logger.warn("Invalid speech text, using fallback");
            speechText = DELETION_ERROR;
        }

        logger.debug("Building response: {}", speechText);
        return HandlerHelper.buildAlexaResponse(handlerInput, speechText, true);
    }
}
