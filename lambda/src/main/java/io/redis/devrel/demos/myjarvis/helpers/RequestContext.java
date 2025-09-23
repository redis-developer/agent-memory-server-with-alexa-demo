package io.redis.devrel.demos.myjarvis.helpers;

public record RequestContext(
        String sessionId,
        String userId,
        String userName,
        String timezone) {}
