package io.redis.devrel.demos.myjarvis.helpers;

import com.amazon.ask.dispatcher.exception.ExceptionHandler;
import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.model.Response;

import java.util.Optional;

public class UserDoesNotExistExceptionHandler implements ExceptionHandler {

    @Override
    public boolean canHandle(HandlerInput handlerInput, Throwable throwable) {
        return throwable instanceof UserDoesNotExistException;
    }

    @Override
    public Optional<Response> handle(HandlerInput handlerInput, Throwable throwable) {
        String speechText = "It seems I don't know you yet. Please introduce yourself by saying your name";

        return handlerInput.getResponseBuilder()
                .withSpeech(speechText)
                .withReprompt("What's your name?")
                .withShouldEndSession(false)
                .build();
    }
}
