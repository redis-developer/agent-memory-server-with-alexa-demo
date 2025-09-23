package io.redis.devrel.demos.myjarvis.handlers;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.RequestHandler;
import com.amazon.ask.model.Response;
import static com.amazon.ask.request.Predicates.intentName;
import static io.redis.devrel.demos.myjarvis.helpers.Constants.SKILL_NAME;
import static io.redis.devrel.demos.myjarvis.helpers.Constants.AMAZON_HELP_INTENT;

import java.util.Optional;

public class HelpIntentHandler implements RequestHandler {

    @Override
    public boolean canHandle(final HandlerInput handlerInput) {
        return handlerInput.matches(intentName(AMAZON_HELP_INTENT));
    }

    @Override
    public Optional<Response> handle(final HandlerInput handlerInput) {
        String speechText = "You can ask me to remember, recall, or just discuss things with you.";
        return handlerInput.getResponseBuilder()
                .withSpeech(speechText)
                .withSimpleCard(SKILL_NAME, speechText)
                .withReprompt(speechText)
                .build();
    }

}
