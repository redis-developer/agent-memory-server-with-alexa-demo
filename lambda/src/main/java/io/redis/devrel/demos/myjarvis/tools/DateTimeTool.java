package io.redis.devrel.demos.myjarvis.tools;

import dev.langchain4j.agent.tool.Tool;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class DateTimeTool {

    private static final ThreadLocal<String> userTimeZone =
            ThreadLocal.withInitial(() -> "America/New_York");

    @Tool("Set the user's timezone for this request")
    public String setUserTimeZone(String timeZone) {
        userTimeZone.set(timeZone);
        return "Timezone set to: " + timeZone;
    }

    @Tool("Get the current date in user's timezone")
    public String getCurrentDate() {
        return LocalDate.now(ZoneId.of(userTimeZone.get())).toString();
    }

    @Tool("Get the current date and time in user's timezone in format yyyy-MM-dd'T'HH:mm:ss")
    public String getCurrentDateTime() {
        var now = LocalDateTime.now(ZoneId.of(userTimeZone.get()));
        var formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        return now.format(formatter);
    }

    @Tool("Get the current day of the week in user's timezone")
    public String getCurrentDayOfWeek() {
        return LocalDate.now(ZoneId.of(userTimeZone.get())).getDayOfWeek().toString();
    }

    @Tool("Calculate the next occurrence of the specified day of the week")
    public String getNextDayOfWeek(String dayOfWeek) {
        var today = LocalDate.now(ZoneId.of(userTimeZone.get()));
        var targetDay = java.time.DayOfWeek.valueOf(dayOfWeek.toUpperCase());
        var daysToAdd = (targetDay.getValue() - today.getDayOfWeek().getValue() + 7) % 7;
        daysToAdd = daysToAdd == 0 ? 7 : daysToAdd;
        var nextDate = today.plusDays(daysToAdd);
        return nextDate.toString();
    }

    @Tool("Calculate datetime by adding specified minutes to current time")
    public String addMinutesToNow(int minutes) {
        var future = LocalDateTime.now(ZoneId.of(userTimeZone.get())).plusMinutes(minutes);
        var formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        return future.format(formatter);
    }

    @Tool("Calculate datetime by adding specified hours to current time")
    public String addHoursToNow(int hours) {
        var future = LocalDateTime.now(ZoneId.of(userTimeZone.get())).plusHours(hours);
        var formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        return future.format(formatter);
    }
}
