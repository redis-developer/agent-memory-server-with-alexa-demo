package io.redis.devrel.demos.myjarvis.handlers;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.RequestHandler;
import com.amazon.ask.model.Response;
import com.amazon.ask.request.Predicates;
import io.redis.devrel.demos.myjarvis.helpers.HandlerHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import static io.redis.devrel.demos.myjarvis.helpers.Constants.*;

public class NoIntentHandler implements RequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(NoIntentHandler.class);

    private static final String[] JARVIS_RESPONSES = {
            "Understood. I'll skip the reminder for now.",
            "Very well. No reminder will be set.",
            "As you wish. I won't set up a reminder.",
            "Noted. We'll proceed without a reminder.",
            "Of course. No reminder needed then."
    };

    private static final String DEFAULT_RESPONSE = "Understood. No reminder will be set.";
    private static final String SESSION_KEY_WAITING = "waitingForReminderConfirmation";
    private static final String SESSION_KEY_REMINDER = "reminderText";

    @Override
    public boolean canHandle(HandlerInput handlerInput) {
        return hasSession(handlerInput)
                && handlerInput.matches(Predicates.intentName(AMAZON_NO_INTENT))
                && isWaitingForReminderConfirmation(handlerInput);
    }

    @Override
    public Optional<Response> handle(HandlerInput handlerInput) {
        clearReminderSession(handlerInput);
        var speechText = selectJarvisResponse();

        logger.info("User declined reminder. Response: {}", speechText);

        return buildResponse(handlerInput, speechText);
    }

    private boolean hasSession(HandlerInput handlerInput) {
        var hasSession = handlerInput.getRequestEnvelope().getSession() != null;

        if (!hasSession) {
            logger.info("No session available for NoIntent");
        }

        return hasSession;
    }

    private boolean isWaitingForReminderConfirmation(HandlerInput handlerInput) {
        try {
            var sessionAttributes = handlerInput.getAttributesManager().getSessionAttributes();
            var waiting = Boolean.TRUE.equals(sessionAttributes.get(SESSION_KEY_WAITING));

            logger.debug("Checking reminder confirmation status: {}", waiting);
            return waiting;
        } catch (Exception e) {
            logger.error("Error checking session attributes", e);
            return false;
        }
    }

    private void clearReminderSession(HandlerInput handlerInput) {
        try {
            var sessionAttributes = handlerInput.getAttributesManager().getSessionAttributes();

            var reminderText = sessionAttributes.get(SESSION_KEY_REMINDER);
            if (reminderText != null) {
                logger.debug("Clearing declined reminder: {}", reminderText);
            }

            sessionAttributes.remove(SESSION_KEY_WAITING);
            sessionAttributes.remove(SESSION_KEY_REMINDER);
            handlerInput.getAttributesManager().setSessionAttributes(sessionAttributes);

            logger.debug("Reminder session data cleared");
        } catch (Exception e) {
            logger.error("Error clearing session attributes", e);
        }
    }

    private String selectJarvisResponse() {
        try {
            int index = (int) (Math.random() * JARVIS_RESPONSES.length);
            return JARVIS_RESPONSES[index];
        } catch (Exception e) {
            logger.error("Error selecting response", e);
            return DEFAULT_RESPONSE;
        }
    }

    private Optional<Response> buildResponse(HandlerInput handlerInput, String speechText) {
        if (speechText == null || speechText.isBlank()) {
            logger.warn("Invalid speech text, using default");
            speechText = DEFAULT_RESPONSE;
        }

        return HandlerHelper.buildAlexaResponse(handlerInput, speechText, true);
    }
}
