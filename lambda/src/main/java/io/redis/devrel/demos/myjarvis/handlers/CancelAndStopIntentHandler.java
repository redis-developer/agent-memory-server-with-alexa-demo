package io.redis.devrel.demos.myjarvis.handlers;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.RequestHandler;
import com.amazon.ask.model.Response;
import static com.amazon.ask.request.Predicates.intentName;
import static io.redis.devrel.demos.myjarvis.helpers.Constants.SKILL_NAME;
import static io.redis.devrel.demos.myjarvis.helpers.Constants.AMAZON_STOP_INTENT;
import static io.redis.devrel.demos.myjarvis.helpers.Constants.AMAZON_CANCEL_INTENT;

import java.util.Optional;

public class CancelAndStopIntentHandler implements RequestHandler {

    @Override
    public boolean canHandle(final HandlerInput handlerInput) {
        return handlerInput.matches(intentName(AMAZON_STOP_INTENT)
                .or(intentName(AMAZON_CANCEL_INTENT)));
    }

    @Override
    public Optional<Response> handle(final HandlerInput handlerInput) {
        return handlerInput.getResponseBuilder()
                .withSpeech("Goodbye")
                .withSimpleCard(SKILL_NAME, "Goodbye")
                .withShouldEndSession(true)
                .build();
    }

}
