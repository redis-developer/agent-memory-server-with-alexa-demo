package io.redis.devrel.demos.myjarvis.handlers;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.RequestHandler;
import com.amazon.ask.model.Response;
import static com.amazon.ask.request.Predicates.intentName;
import static io.redis.devrel.demos.myjarvis.helpers.Constants.SKILL_NAME;
import static io.redis.devrel.demos.myjarvis.helpers.Constants.AMAZON_FALLBACK_INTENT;

import java.util.Optional;

public class FallbackIntentHandler implements RequestHandler {

    @Override
    public boolean canHandle(final HandlerInput handlerInput) {
        return handlerInput.matches(intentName(AMAZON_FALLBACK_INTENT));
    }

    @Override
    public Optional<Response> handle(final HandlerInput handlerInput) {
        String speechText = "Sorry, I didn't get that.";
        return handlerInput.getResponseBuilder()
                .withSpeech(speechText)
                .withSimpleCard(SKILL_NAME, speechText)
                .withReprompt(speechText)
                .build();
    }

}
