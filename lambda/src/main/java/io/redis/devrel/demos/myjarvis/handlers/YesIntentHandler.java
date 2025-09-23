package io.redis.devrel.demos.myjarvis.handlers;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.RequestHandler;
import com.amazon.ask.model.Response;
import com.amazon.ask.model.services.ServiceException;
import com.amazon.ask.model.services.reminderManagement.ReminderManagementServiceClient;
import com.amazon.ask.request.Predicates;
import io.redis.devrel.demos.myjarvis.helpers.HandlerHelper;
import io.redis.devrel.demos.myjarvis.services.ReminderService;
import io.redis.devrel.demos.myjarvis.services.ReminderService.ReminderDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static io.redis.devrel.demos.myjarvis.helpers.Constants.*;
import static io.redis.devrel.demos.myjarvis.helpers.HandlerHelper.*;

public class YesIntentHandler implements RequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(YesIntentHandler.class);
    private static final String REMINDERS_PERMISSION = "alexa::alerts:reminders:skill:readwrite";

    private final ReminderService reminderService;

    public YesIntentHandler(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @Override
    public boolean canHandle(HandlerInput handlerInput) {
        return hasSession(handlerInput)
                && handlerInput.matches(Predicates.intentName(AMAZON_YES_INTENT))
                && isWaitingForReminderConfirmation(handlerInput);
    }

    @Override
    public Optional<Response> handle(HandlerInput handlerInput) {
        var reminderData = extractReminderDataFromSession(handlerInput);

        if (reminderData.isEmpty()) {
            return buildErrorResponse(handlerInput,
                    "I'm not sure what you're confirming. Please start over.");
        }

        var userTimeZone = getUserTimeZone(handlerInput);
        return createReminderAndBuildResponse(handlerInput, reminderData.get(), userTimeZone);
    }

    private boolean hasSession(HandlerInput input) {
        return input.getRequestEnvelope().getSession() != null;
    }

    private boolean isWaitingForReminderConfirmation(HandlerInput handlerInput) {
        var attributes = handlerInput.getAttributesManager().getSessionAttributes();
        return Boolean.TRUE.equals(attributes.get("waitingForReminderConfirmation"));
    }

    private Optional<ReminderData> extractReminderDataFromSession(HandlerInput handlerInput) {
        var attributes = handlerInput.getAttributesManager().getSessionAttributes();

        var topic = (String) attributes.remove("reminderTopic");
        var schedule = (String) attributes.remove("reminderSchedule");
        var isRecurring = (Boolean) attributes.remove("reminderIsRecurring");
        var frequency = (String) attributes.remove("reminderFrequency");
        var byDays = (List<String>) attributes.remove("reminderByDays");
        attributes.remove("waitingForReminderConfirmation");

        handlerInput.getAttributesManager().setSessionAttributes(attributes);

        if (topic == null || schedule == null) {
            return Optional.empty();
        }

        return Optional.of(new ReminderData(
                topic,
                schedule,
                Boolean.TRUE.equals(isRecurring),
                frequency,
                byDays
        ));
    }

    private Optional<Response> createReminderAndBuildResponse(HandlerInput handlerInput,
                                                              ReminderData reminderData,
                                                              String timeZone) {
        try {
            var details = new ReminderDetails(
                    reminderData.topic(),
                    LocalDateTime.parse(reminderData.schedule())
            );

            var reminderMgmtService = handlerInput.getServiceClientFactory().getReminderManagementService();

            if (checkRemindersPermission(reminderMgmtService)) {
                String token;

                if (reminderData.isRecurring()) {
                    if (!"DAILY".equals(reminderData.frequency()) && !"WEEKLY".equals(reminderData.frequency())) {
                        return buildErrorResponse(handlerInput,
                                "I'm sorry, but Alexa only supports daily and weekly recurring reminders. " +
                                        "For monthly reminders, I'll need to create a one-time reminder instead.");
                    }

                    token = reminderService.createRecurringReminder(
                            reminderMgmtService, details, timeZone,
                            reminderData.frequency(), reminderData.byDays()
                    );
                    logger.info("Successfully created recurring reminder: {}", token);
                    return buildSuccessResponse(handlerInput);
                } else {
                    token = reminderService.createReminder(reminderMgmtService, details, timeZone);
                    logger.info("Successfully created reminder: {}", token);
                    return buildSuccessResponse(handlerInput);
                }
            } else {
                return sendPermissionRequest(handlerInput);
            }

        } catch (ServiceException se) {
            logger.error("Failed to create reminder: {}", se.getMessage(), se);
            return buildErrorResponse(handlerInput,
                    "Sorry, I couldn't create the reminder. Please try again.");
        }
    }

    private boolean checkRemindersPermission(ReminderManagementServiceClient reminderMgmtService) {
        try {
            return reminderMgmtService.getReminders() != null;
        } catch (ServiceException se) {
            return se.getStatusCode() == 401 || se.getStatusCode() == 403;
        } catch (Exception ex) {
            return false;
        }
    }

    private Optional<Response> sendPermissionRequest(HandlerInput handlerInput) {
        String speechText = """
            I need permission to create reminders. Please go to your Alexa app and look for
            the permissions card. You only need to enable the 'Reminders' permission, and you can
            leave the others disabled if you prefer. Once you've granted the reminder permission,
            you can ask me to set a reminder again.
        """;

        return handlerInput.getResponseBuilder()
                .withSpeech(speechText)
                .withAskForPermissionsConsentCard(List.of(REMINDERS_PERMISSION))
                .withShouldEndSession(true)
                .build();
    }

    private Optional<Response> buildSuccessResponse(HandlerInput handlerInput) {
        return HandlerHelper.buildAlexaResponse(handlerInput,
                "Perfect! I've set up your reminder.", true);
    }

    private Optional<Response> buildErrorResponse(HandlerInput handlerInput, String message) {
        return HandlerHelper.buildAlexaResponse(handlerInput, message, true);
    }

    private record ReminderData(
            String topic,
            String schedule,
            boolean isRecurring,
            String frequency,
            List<String> byDays
    ) {}
}