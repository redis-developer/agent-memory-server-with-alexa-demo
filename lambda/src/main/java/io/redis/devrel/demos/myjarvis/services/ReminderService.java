package io.redis.devrel.demos.myjarvis.services;

import com.amazon.ask.model.services.ServiceException;
import com.amazon.ask.model.services.reminderManagement.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.*;
import java.util.List;

public class ReminderService {

    private static final Logger logger = LoggerFactory.getLogger(ReminderService.class);
    private static final int MAX_RELATIVE_SECONDS = 172800; // 48 hours

    public String createReminder(ReminderManagementServiceClient reminderMgmtService,
                                 ReminderDetails reminderDetails,
                                 String userTimeZone) throws ServiceException {

        var trigger = buildTrigger(reminderDetails.scheduleTime(), userTimeZone);
        var request = buildReminderRequest(reminderDetails.topic(), trigger);

        var response = reminderMgmtService.createReminder(request);
        logger.info("Created reminder with token: {}", response.getAlertToken());

        return response.getAlertToken();
    }

    public String createRecurringReminder(ReminderManagementServiceClient reminderMgmtService,
                                          ReminderDetails reminderDetails,
                                          String userTimeZone,
                                          String frequency,
                                          List<String> byDays) throws ServiceException {

        var trigger = buildRecurringTrigger(reminderDetails.scheduleTime(), userTimeZone, frequency, byDays);
        var request = buildReminderRequest(reminderDetails.topic(), trigger);

        logger.debug("Reminder request: {}", request);
        var response = reminderMgmtService.createReminder(request);
        logger.info("Created recurring reminder with token: {}", response.getAlertToken());

        return response.getAlertToken();
    }

    private Trigger buildTrigger(LocalDateTime scheduledTime, String timeZone) {
        var duration = calculateDurationFromNow(scheduledTime, timeZone);

        if (duration.seconds() <= 0) {
            scheduledTime = scheduledTime.plusDays(1);
            duration = calculateDurationFromNow(scheduledTime, timeZone);
            logger.info("Adjusted past time to tomorrow: {}", duration.scheduledZoned());
        }

        return duration.seconds() <= MAX_RELATIVE_SECONDS
                ? createRelativeTrigger(duration.seconds())
                : createAbsoluteTrigger(scheduledTime, timeZone);
    }

    private Trigger buildRecurringTrigger(LocalDateTime scheduledTime, String timeZone,
                                          String frequency, List<String> byDays) {
        var recurrenceBuilder = Recurrence.builder()
                .withFreq(RecurrenceFreq.valueOf(frequency));

        if (byDays != null && !byDays.isEmpty()) {
            var days = byDays.stream()
                    .map(RecurrenceDay::valueOf)
                    .toList();
            recurrenceBuilder.withByDay(days);
        }

        return Trigger.builder()
                .withType(TriggerType.SCHEDULED_ABSOLUTE)
                .withScheduledTime(scheduledTime)
                .withTimeZoneId(timeZone)
                .withRecurrence(recurrenceBuilder.build())
                .build();
    }

    private Duration calculateDurationFromNow(LocalDateTime scheduledTime, String timeZone) {
        var zoneId = ZoneId.of(timeZone);
        var scheduledZoned = scheduledTime.atZone(zoneId);
        var nowZoned = ZonedDateTime.now(zoneId);
        var seconds = java.time.Duration.between(nowZoned, scheduledZoned).getSeconds();

        logger.debug("Time calculation - Now: {}, Scheduled: {}, Seconds: {}",
                nowZoned, scheduledZoned, seconds);

        return new Duration(seconds, scheduledZoned);
    }

    private Trigger createRelativeTrigger(long seconds) {
        logger.info("Using RELATIVE trigger with {} seconds", seconds);
        return Trigger.builder()
                .withType(TriggerType.SCHEDULED_RELATIVE)
                .withOffsetInSeconds((int) seconds)
                .build();
    }

    private Trigger createAbsoluteTrigger(LocalDateTime scheduledTime, String timeZone) {
        logger.info("Using ABSOLUTE trigger for {} in timezone {}", scheduledTime, timeZone);
        return Trigger.builder()
                .withType(TriggerType.SCHEDULED_ABSOLUTE)
                .withScheduledTime(scheduledTime)
                .withTimeZoneId(timeZone)
                .build();
    }

    private ReminderRequest buildReminderRequest(String topic, Trigger trigger) {
        return ReminderRequest.builder()
                .withRequestTime(OffsetDateTime.now())
                .withTrigger(trigger)
                .withAlertInfo(createAlertInfo(topic))
                .withPushNotification(createPushNotification())
                .build();
    }

    private AlertInfo createAlertInfo(String topic) {
        return AlertInfo.builder()
                .withSpokenInfo(SpokenInfo.builder()
                        .withContent(List.of(
                                SpokenText.builder()
                                        .withLocale("en-US")
                                        .withText(topic)
                                        .build()
                        ))
                        .build())
                .build();
    }

    private PushNotification createPushNotification() {
        return PushNotification.builder()
                .withStatus(PushNotificationStatus.ENABLED)
                .build();
    }

    public record ReminderDetails(String topic, LocalDateTime scheduleTime) {
    }

    private record Duration(long seconds, ZonedDateTime scheduledZoned) {
    }
}