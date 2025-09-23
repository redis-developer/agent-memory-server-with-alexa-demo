package io.redis.devrel.demos.myjarvis.helpers;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.interceptor.RequestInterceptor;
import com.amazon.ask.model.IntentRequest;
import com.amazon.ask.model.services.ServiceException;
import com.amazon.ask.model.services.ups.UpsServiceClient;
import io.redis.devrel.demos.myjarvis.services.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.redis.devrel.demos.myjarvis.helpers.Constants.*;

public class UserValidationInterceptor implements RequestInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(UserValidationInterceptor.class);

    private static final Set<String> WHITELIST_INTENTS = Set.of(
            AMAZON_YES_INTENT,
            AMAZON_NO_INTENT,
            AMAZON_CANCEL_INTENT,
            AMAZON_STOP_INTENT,
            AMAZON_HELP_INTENT,
            AMAZON_FALLBACK_INTENT,
            AGENT_MEMORY_SERVER_INTENT,
            KNOWLEDGE_BASE_INTENT,
            USER_INTRO_INTENT
    );

    private final UserService userService;

    public UserValidationInterceptor(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void process(HandlerInput handlerInput) {
        var request = handlerInput.getRequestEnvelope().getRequest();

        // This is required for the APL support
        if (!(request instanceof IntentRequest)) {
            logger.debug("Bypassing validation for non-intent requests: {}", request);
            return;
        }

        var intentName = extractIntentName(handlerInput);

        if (isWhitelisted(intentName)) {
            logger.debug("Bypassing validation for whitelisted intent: {}", intentName);
            return;
        }

        var userId = HandlerHelper.extractUserIdFromRequest(handlerInput);
        var userContext = resolveUserContext(handlerInput, userId);

        storeUserContext(handlerInput, userContext);
    }

    private String extractIntentName(HandlerInput input) {
        try {
            var intentRequest = (IntentRequest) input.getRequestEnvelope().getRequest();
            return intentRequest.getIntent().getName();
        } catch (Exception e) {
            logger.error("Failed to extract intent name", e);
            throw new UserDoesNotExistException("Unable to process request");
        }
    }

    private boolean isWhitelisted(String intentName) {
        return WHITELIST_INTENTS.contains(intentName);
    }

    private UserContext resolveUserContext(HandlerInput input, String userId) {
        logger.debug("Resolving user context for userId: {}", userId);

        // Check if user already exists
        var existingUser = getExistingUser(userId);
        if (existingUser.isPresent()) {
            logger.info("Found existing user: {}", existingUser.get());
            return new UserContext(userId, existingUser.get());
        }

        // Try to fetch and create new user
        var userName = fetchUserNameFromProfile(input);
        if (userName.isEmpty()) {
            logger.warn("Unable to fetch user name from profile");
            throw new UserDoesNotExistException("User not found and unable to fetch profile");
        }

        // Create new user
        createNewUser(userId, userName.get());
        return new UserContext(userId, userName.get());
    }

    private Optional<String> getExistingUser(String userId) {
        try {
            return userService.getUserName(userId);
        } catch (Exception e) {
            logger.error("Error checking existing user", e);
            return Optional.empty();
        }
    }

    private Optional<String> fetchUserNameFromProfile(HandlerInput input) {
        try {
            var upsClient = input.getServiceClientFactory().getUpsService();

            // Try recognized person first
            var personName = fetchRecognizedPersonName(input, upsClient);
            if (personName.isPresent()) {
                return personName;
            }

            // Fallback to account owner
            return fetchAccountOwnerName(upsClient);

        } catch (Exception e) {
            logger.error("Failed to fetch user profile", e);
            return Optional.empty();
        }
    }

    private Optional<String> fetchRecognizedPersonName(HandlerInput input,
                                                       UpsServiceClient upsClient) {
        try {
            var person = input.getRequestEnvelope().getContext().getSystem().getPerson();

            if (person == null || person.getPersonId() == null) {
                logger.debug("No recognized person in request");
                return Optional.empty();
            }

            var givenName = upsClient.getPersonsProfileGivenName();

            if (givenName != null && !givenName.isBlank()) {
                logger.info("Retrieved recognized person name: {}", givenName);
                return Optional.of(givenName);
            }

            return Optional.empty();

        } catch (ServiceException e) {
            logger.debug("Could not fetch recognized person profile: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<String> fetchAccountOwnerName(UpsServiceClient upsClient) {
        try {
            var givenName = upsClient.getProfileGivenName();

            if (givenName != null && !givenName.isBlank()) {
                logger.info("Retrieved account owner name: {}", givenName);
                return Optional.of(givenName);
            }

            return Optional.empty();

        } catch (ServiceException e) {
            logger.debug("Could not fetch account owner profile: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private void createNewUser(String userId, String userName) {
        try {
            if (!userService.createUser(userId, userName)) {
                logger.error("Failed to create user: {} with name: {}", userId, userName);
                throw new UserDoesNotExistException("Unable to create user profile");
            }

            logger.info("Successfully created new user: {} with name: {}", userId, userName);

        } catch (Exception e) {
            logger.error("Error creating user", e);
            throw new UserDoesNotExistException("Failed to create user profile");
        }
    }

    private void storeUserContext(HandlerInput input, UserContext context) {
        try {
            input.getAttributesManager().setRequestAttributes(
                    Map.of(
                            USER_ID_PARAM, context.userId(),
                            USER_NAME_PARAM, context.userName()
                    )
            );

            logger.debug("Stored user context in request attributes: {}", context);

        } catch (Exception e) {
            logger.error("Failed to store user context", e);
            throw new UserDoesNotExistException("Unable to process user context");
        }
    }
}
