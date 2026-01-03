package io.redis.devrel.demos.myjarvis.helpers;

import dev.langchain4j.data.message.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageHelper {

    private static final Logger logger = LoggerFactory.getLogger(MessageHelper.class);

    public static String determineRole(ChatMessage message) {
        return switch (message) {
            case UserMessage userMessage -> "user";
            case AiMessage aiMessage -> "assistant";
            case SystemMessage systemMessage -> "system";
            case ToolExecutionResultMessage toolExecutionResultMessage -> "tool";
            default -> {
                String className = message.getClass().getSimpleName().toLowerCase();
                if (className.contains("user")) {
                    yield "user";
                } else if (className.contains("ai") || className.contains("assistant")) {
                    yield "assistant";
                } else if (className.contains("system")) {
                    yield "system";
                } else if (className.contains("tool")) {
                    yield "tool";
                } else {
                    logger.warn("Unknown ChatMessage type: {}", message.getClass().getName());
                    yield "unknown";
                }
            }
        };
    }

    public static String messageContent(ChatMessage message) {
        return switch (message) {
            case UserMessage userMessage -> userMessage.singleText();
            case AiMessage aiMessage -> aiMessage.text();
            case SystemMessage systemMessage -> systemMessage.text();
            case ToolExecutionResultMessage toolExecutionResultMessage -> toolExecutionResultMessage.text();
            default -> {
                logger.warn("Unknown message type for content extraction: {}", message.getClass().getName());
                yield message.toString();
            }
        };
    }

}
