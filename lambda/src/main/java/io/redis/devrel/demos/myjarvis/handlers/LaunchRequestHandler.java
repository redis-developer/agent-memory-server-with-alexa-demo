package io.redis.devrel.demos.myjarvis.handlers;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.RequestHandler;
import com.amazon.ask.model.LaunchRequest;
import com.amazon.ask.model.Response;
import com.amazon.ask.request.Predicates;

import java.util.Optional;

import static io.redis.devrel.demos.myjarvis.helpers.Constants.SKILL_NAME;

public class LaunchRequestHandler implements RequestHandler {

    @Override
    public boolean canHandle(final HandlerInput handlerInput) {
        return handlerInput.matches(Predicates.requestType(LaunchRequest.class));
    }

    @Override
    public Optional<Response> handle(final HandlerInput handlerInput) {
        String speechText = "Welcome to My Jarvis! How can I help you?";
        return handlerInput.getResponseBuilder()
                .withSpeech(speechText)
                .withSimpleCard(SKILL_NAME, speechText)
                .withReprompt(speechText)
                .build();
    }

}
