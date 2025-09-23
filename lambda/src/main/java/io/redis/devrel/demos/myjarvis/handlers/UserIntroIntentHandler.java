package io.redis.devrel.demos.myjarvis.handlers;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.RequestHandler;
import com.amazon.ask.model.IntentRequest;
import com.amazon.ask.model.Response;
import com.amazon.ask.request.Predicates;
import io.redis.devrel.demos.myjarvis.services.UserService;
import io.redis.devrel.demos.myjarvis.services.ChatAssistantService;
import io.redis.devrel.demos.myjarvis.helpers.HandlerHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import static io.redis.devrel.demos.myjarvis.helpers.Constants.*;
import static io.redis.devrel.demos.myjarvis.helpers.HandlerHelper.extractUserIdFromRequest;

public class UserIntroIntentHandler implements RequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(UserIntroIntentHandler.class);

    private final static String SYSTEM_PROMPT = """
        You are an AI assistant that should act, talk, and behave as if you were J.A.R.V.I.S AI
        from the Iron Man movies. Be formal but friendly, and add personality. You are going to
        be the brains behind an Alexa skill.
        
        As for your specific instructions, users will introduce themselves to you and you are going
        to either confirm the acquaintance or greet them back if they are new to you. It may happen
        that the user can't be saved, so in this case provide a helpful message saying that there was
        an error while saving their name. Also, keep your answer concise with two sentences top.
        
        Also, make sure to:
        
        1. Use gender-neutral language and avoid terms like 'sir' or 'madam'.
        """;

    private static final String FALLBACK_ERROR =
            "I'm having trouble saving your information at the moment. Please try again later.";

    private final UserService userService;
    private final ChatAssistantService chatAssistantService;

    public UserIntroIntentHandler(UserService userService,
                                  ChatAssistantService chatAssistantService) {
        this.userService = userService;
        this.chatAssistantService = chatAssistantService;
    }

    @Override
    public boolean canHandle(HandlerInput handlerInput) {
        return handlerInput.matches(Predicates.intentName(USER_INTRO_INTENT));
    }

    @Override
    public Optional<Response> handle(HandlerInput handlerInput) {
        var userId = extractUserIdFromRequest(handlerInput);
        var existingUser = checkExistingUser(userId);

        if (existingUser.isPresent()) {
            return handleExistingUser(handlerInput, existingUser.get());
        }

        return handleNewUser(handlerInput, userId);
    }

    private Optional<String> checkExistingUser(String userId) {
        try {
            return userService.getUserName(userId);
        } catch (Exception e) {
            logger.error("Error checking existing user", e);
            return Optional.empty();
        }
    }

    private Optional<Response> handleExistingUser(HandlerInput handlerInput, String userName) {
        logger.info("Recognized existing user: {}", userName);

        var speechText = generateExistingUserResponse(userName);
        return buildResponse(handlerInput, speechText);
    }

    private Optional<Response> handleNewUser(HandlerInput handlerInput, String userId) {
        var newUserName = extractNewUserName(handlerInput);

        if (newUserName.isEmpty()) {
            logger.info("No user name provided in introduction");
            return buildResponse(handlerInput, "I didn't catch your name. Could you please repeat that?");
        }

        var savedSuccessfully = saveNewUser(userId, newUserName.get());
        var speechText = generateNewUserResponse(newUserName.get(), savedSuccessfully);

        return buildResponse(handlerInput, speechText);
    }

    private Optional<String> extractNewUserName(HandlerInput handlerInput) {
        try {
            var intentRequest = (IntentRequest) handlerInput.getRequestEnvelope().getRequest();
            var slot = intentRequest.getIntent().getSlots().get(USER_NAME_PARAM);

            if (slot != null && slot.getValue() != null && !slot.getValue().isBlank()) {
                var userName = slot.getValue().trim();
                logger.info("Extracted new user name: {}", userName);
                return Optional.of(userName);
            }

            return Optional.empty();
        } catch (Exception e) {
            logger.error("Error extracting user name", e);
            return Optional.empty();
        }
    }

    private boolean saveNewUser(String userId, String userName) {
        try {
            var saved = userService.createUser(userId, userName);
            logger.info("User save result for {}: {}", userName, saved);
            return saved;
        } catch (Exception e) {
            logger.error("Error saving new user", e);
            return false;
        }
    }

    private String generateExistingUserResponse(String userName) {
        try {
            var prompt = String.format("I know you %s", userName);
            var response = chatAssistantService.processQueryWithoutContext(SYSTEM_PROMPT, prompt);

            if (response != null && !response.isBlank()) {
                return response;
            }
        } catch (Exception e) {
            logger.error("Error generating AI response for existing user", e);
        }

        return String.format("Welcome back, %s! Good to have you here again.", userName);
    }

    private String generateNewUserResponse(String userName, boolean savedSuccessfully) {
        try {
            var prompt = savedSuccessfully
                    ? String.format("New user %s saved successfully", userName)
                    : String.format("Error saving new user %s", userName);

            var response = chatAssistantService.processQueryWithoutContext(SYSTEM_PROMPT, prompt);

            if (response != null && !response.isBlank()) {
                return response;
            }
        } catch (Exception e) {
            logger.error("Error generating AI response for new user", e);
        }

        if (savedSuccessfully) {
            return String.format("Pleasure to meet you, %s. I'll remember you from now on.", userName);
        } else {
            return FALLBACK_ERROR;
        }
    }

    private Optional<Response> buildResponse(HandlerInput handlerInput, String speechText) {
        if (speechText == null || speechText.isBlank()) {
            speechText = FALLBACK_ERROR;
        }

        logger.info("Building response: {}", speechText);
        return HandlerHelper.buildAlexaResponse(handlerInput, speechText, true);
    }
}
