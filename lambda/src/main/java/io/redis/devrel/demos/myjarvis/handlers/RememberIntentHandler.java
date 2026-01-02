package io.redis.devrel.demos.myjarvis.handlers;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.RequestHandler;
import com.amazon.ask.model.IntentRequest;
import com.amazon.ask.model.Response;
import com.amazon.ask.model.Slot;
import com.amazon.ask.request.Predicates;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.redis.devrel.demos.myjarvis.helpers.HandlerHelper;
import io.redis.devrel.demos.myjarvis.helpers.RequestContext;
import io.redis.devrel.demos.myjarvis.services.ChatAssistantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

import static io.redis.devrel.demos.myjarvis.helpers.Constants.*;
import static io.redis.devrel.demos.myjarvis.helpers.HandlerHelper.extractRequestContext;

public class RememberIntentHandler implements RequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(RememberIntentHandler.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final static String SYSTEM_PROMPT = """
        You are an AI assistant that should act, talk, and behave as if you were J.A.R.V.I.S AI
        from the Iron Man movies. Be formal but friendly, and add personality. You are going to
        be the brains behind an Alexa skill. While providing answers, be informative but maintain
        the J.A.R.V.I.S personality.
        
        As for your specific instructions, The user will ask you to remember memories the user 
        will provide, which will be given to you via prompt. Use the tools available to create
        the user memory.
        
        IMPORTANT: When calling createUserMemory, use EXACTLY these values:
        - sessionId: Use the sessionId value provided in the context (starts with "amzn1.ask.session")
        - userId: Use the userId value provided in the context (starts with "amzn1.ask.person")
        - timezone: Use the timezone value provided in the context (e.g., "America/New_York")
        - memory: The actual memory text the user wants to store
        
        DO NOT mix up these parameters or use generated timestamps for userId!        
        
        Also, make sure to:
        
        1. Your answer must be in JSON format as specified below.
        2. Only use the context that is relevant to the current query. Don't over do it.
        3. If the user from the context matches the current user, they're the same person.
        4. Don't fabricate answers. Stick with the facts and knowledge from the context.
        5. If the question is not about general topics, then answer based on data you know. 
        6. Keep your answer concise with two sentences top.
        7. Use gender-neutral language - avoid terms like 'sir' or 'madam'.
        
        CRITICAL: Call setUserTimeZone("%s") first, then getCurrentDateTime()
        
        IMPORTANT DATE CALCULATION:
        When user says "Tuesday" or any weekday without "next":
        - Find the NEXT occurrence of that day
        - If today is Sunday (day 0) and user says "Tuesday" (day 2), that's in 2 days
        - If today is Wednesday and user says "Tuesday", that's in 6 days (next week)
        - Use the function getNextDayOfWeek("TUESDAY") to get the correct date
        
        Analyze the memory for TWO things:
        1. Store confirmation message
        2. Whether it needs a reminder and its details
        
        Your answer MUST return this JSON:
        {
            "answer": "Confirmation message to user",
            "suggest_reminder": boolean,
            "reminder_topic": "topic",
            "schedule": "YYYY-MM-DDTHH:MM:SS or empty",
            "is_recurring": boolean,
            "frequency": "DAILY/WEEKLY or null",
            "by_days": ["MO"] or null,
            "memory_stored": boolean
        }
        
        PS: important, no extra text, only the JSON. Also, the reminder_topic should always
        be filled if suggest_reminder=true, and it must contain a clear, concise topic. It
        should not contain details about the schedule or recurrence.
        
        REMINDER DETECTION: Set suggest_reminder=true for:
        - Specific time: "at 10 AM", "at noon"
        - Daily recurrence: "every day at X"
        - Weekly recurrence: "every Monday", "every weekday"
        - Hourly recurrence: "every 4 hours", "every 2 hours" (min 1 hour for en-US, 4 hours for others)
        - One-time future: "tomorrow at X", "next Monday"
                
        If suggest_reminder=true, ALSO extract reminder details:
        
        TIME PARSING:
        - "10 a.m." → 10:00:00
        - "10 p.m." → 22:00:00
        - Use :00 for minutes unless specified
        
        RECURRENCE:
        - "every day" → is_recurring: true, frequency: "DAILY"
        - "every Monday" → is_recurring: true, frequency: "WEEKLY", by_days: ["MO"]
        - "every 4 hours" → is_recurring: true, frequency: "HOURLY", interval_hours: 4
        - "every 2 hours" → is_recurring: true, frequency: "HOURLY", interval_hours: 2
        - "in 4 hours" → is_recurring: false (ONE-TIME)
        
        DAY CODES: MO, TU, WE, TH, FR, SA, SU
        
        Few-shot examples:
        
        [Example 1]
        User: "Remember I have a dentist appointment next Tuesday at 2 PM"
        Response: {
            "answer": "Certainly, I've noted your dentist appointment for next Tuesday at 2 PM.",
            "suggest_reminder": true,
            "reminder_topic": "Dentist appointment",
            "schedule": "2024-01-09T14:00:00",
            "is_recurring": false,
            "frequency": null,
            "by_days": null,
            "memory_stored": true
        }
        
        [Example 2]
        User: "Remember to take vitamins every morning at 8 AM"
        Response: {
            "answer": "I've recorded your daily vitamin reminder for 8 AM.",
            "suggest_reminder": true,
            "reminder_topic": "Take vitamins",
            "schedule": "2024-01-03T08:00:00",
            "is_recurring": true,
            "frequency": "DAILY",
            "by_days": null,
            "memory_stored": true
        }        
        """;

    private final ChatAssistantService chatAssistantService;

    public RememberIntentHandler(ChatAssistantService chatAssistantService) {
        this.chatAssistantService = chatAssistantService;
    }

    @Override
    public boolean canHandle(HandlerInput handlerInput) {
        return handlerInput.matches(Predicates.intentName(REMEMBER_INTENT));
    }

    @Override
    public Optional<Response> handle(HandlerInput handlerInput) {
        var context = extractRequestContext(handlerInput);
        var memory = extractMemoryText(handlerInput);

        if (memory.isEmpty()) {
            return buildErrorResponse(handlerInput,
                    "I didn't catch what you wanted me to remember");
        }

        var aiResponse = processWithAI(context, memory.get());

        return aiResponse
                .map(response -> buildResponseFromAI(handlerInput, response))
                .orElseGet(() -> buildFallbackResponse(handlerInput));
    }

    private Optional<String> extractMemoryText(HandlerInput handlerInput) {
        try {
            var intentRequest = (IntentRequest) handlerInput.getRequestEnvelope().getRequest();
            var slot = intentRequest.getIntent().getSlots().get(MEMORY_PARAM);
            return Optional.ofNullable(slot).map(Slot::getValue);
        } catch (Exception e) {
            logger.error("Error extracting memory text", e);
            return Optional.empty();
        }
    }

    private Optional<AnswerResponse> processWithAI(RequestContext context,
                                                   String memory) {
        try {
            String query = String.format("""
            User asked to store this memory: %s
            - sessionId: %s
            - userId: %s
            - timezone: %s
            """,
                    memory,
                    context.sessionId(),
                    context.userId(),
                    context.timezone()
            );

            var prompt = String.format(SYSTEM_PROMPT, context.timezone());

            var response = chatAssistantService.processQueryWithContext(
                    prompt,
                    context.userId(),
                    context.userName(),
                    query
            );

            logger.info("AI response: {}", response);
            return parseResponse(response);
        } catch (Exception ex) {
            logger.error("Error processing with AI", ex);
            return Optional.empty();
        }
    }

    private Optional<Response> buildResponseFromAI(HandlerInput handlerInput, AnswerResponse aiResponse) {
        var speechText = aiResponse.answer();

        if (aiResponse.suggestReminder() && aiResponse.schedule() != null && !aiResponse.schedule().isEmpty()) {
            // Store ALL reminder details in session for YesIntentHandler
            var attributes = handlerInput.getAttributesManager().getSessionAttributes();
            attributes.put("waitingForReminderConfirmation", true);
            attributes.put("reminderTopic", aiResponse.reminderTopic());
            attributes.put("reminderSchedule", aiResponse.schedule());
            attributes.put("reminderIsRecurring", aiResponse.isRecurring());
            attributes.put("reminderFrequency", aiResponse.frequency());
            attributes.put("reminderByDays", aiResponse.byDays());
            handlerInput.getAttributesManager().setSessionAttributes(attributes);

            var promptText = speechText + " Would you like me to set up a reminder for this?";
            return HandlerHelper.buildAlexaResponse(handlerInput, promptText, false);
        }

        return buildSimpleResponse(handlerInput, speechText);
    }

    private Optional<Response> buildSimpleResponse(HandlerInput handlerInput, String speechText) {
        return HandlerHelper.buildAlexaResponse(handlerInput, speechText, true);
    }

    private Optional<Response> buildFallbackResponse(HandlerInput input) {
        var speechText = "I'm sorry, I couldn't process your request to remember that. Please try again.";
        return buildSimpleResponse(input, speechText);
    }

    private Optional<Response> buildErrorResponse(HandlerInput handlerInput, String message) {
        return HandlerHelper.buildAlexaResponse(handlerInput, message, true);
    }

    private Optional<AnswerResponse> parseResponse(String responseAsJson) {
        try {
            return Optional.ofNullable(objectMapper.readValue(responseAsJson, AnswerResponse.class));
        } catch (Exception e) {
            logger.error("Failed to parse AI response: {}", responseAsJson, e);
            return Optional.empty();
        }
    }

    private record AnswerResponse(
            String answer,
            @JsonProperty("suggest_reminder") boolean suggestReminder,
            @JsonProperty("reminder_topic") String reminderTopic,
            @JsonProperty("schedule") String schedule,
            @JsonProperty("is_recurring") boolean isRecurring,
            @JsonProperty("frequency") String frequency,
            @JsonProperty("by_days") List<String> byDays,
            @JsonProperty("memory_stored") boolean memoryStored
    ) {}
}