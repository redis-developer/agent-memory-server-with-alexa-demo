package io.redis.devrel.demos.myjarvis.helpers;

public class UserDoesNotExistException extends RuntimeException {
    public UserDoesNotExistException(String message) {
        super(message);
    }
}
