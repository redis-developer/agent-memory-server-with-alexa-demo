package io.redis.devrel.demos.myjarvis.handlers;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.RequestHandler;
import com.amazon.ask.model.Response;
import com.amazon.ask.request.Predicates;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import io.redis.devrel.demos.myjarvis.services.MemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static io.redis.devrel.demos.myjarvis.helpers.Constants.*;

public class KnowledgeBaseIntentHandler implements RequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeBaseIntentHandler.class);

    private static final String INGEST_FOLDER = "ingest/";
    private static final String PROCESSED_FOLDER = "processed/";
    private static final String FAILED_FOLDER = "failed/";
    private static final String FOLDER_DELIMITER = "/";

    private final DocumentParser documentParser;
    private final MemoryService memoryService;
    private final AmazonS3 s3Client;

    public KnowledgeBaseIntentHandler(DocumentParser documentParser,
                                      MemoryService memoryService) {
        this.documentParser = documentParser;
        this.memoryService = memoryService;
        this.s3Client = AmazonS3ClientBuilder.defaultClient();
    }

    @Override
    public boolean canHandle(HandlerInput input) {
        return input.matches(Predicates.intentName(KNOWLEDGE_BASE_INTENT));
    }

    @Override
    public Optional<Response> handle(HandlerInput input) {
        logger.info("Starting scheduled sync");

        var filesToProcess = listFilesToProcess();

        if (filesToProcess.isEmpty()) {
            logger.debug("No new files to process");
            return Optional.empty();
        }

        var result = processFiles(filesToProcess);

        logger.info("Completed - Total: {}, Processed: {}, Failed: {}",
                result.total(), result.processed(), result.failed());

        return Optional.empty();
    }

    private List<S3ObjectSummary> listFilesToProcess() {
        try {
            var objectSummaries = s3Client.listObjectsV2(
                    KNOWLEDGE_BASE_BUCKET_NAME, INGEST_FOLDER
            ).getObjectSummaries();

            return objectSummaries.stream()
                    .filter(this::isValidFile)
                    .toList();

        } catch (Exception e) {
            logger.error("Failed to list S3 objects", e);
            return List.of();
        }
    }

    private boolean isValidFile(S3ObjectSummary objectSummary) {
        var key = objectSummary.getKey();

        return key.startsWith(INGEST_FOLDER)
                && !key.endsWith(FOLDER_DELIMITER)
                && objectSummary.getSize() > 0;
    }

    private ProcessingResult processFiles(List<S3ObjectSummary> files) {
        var processed = new AtomicInteger(0);
        var failed = new AtomicInteger(0);

        files.parallelStream().forEach(file -> {
            if (processFile(file)) {
                processed.incrementAndGet();
            } else {
                failed.incrementAndGet();
            }
        });

        return new ProcessingResult(files.size(), processed.get(), failed.get());
    }

    private boolean processFile(S3ObjectSummary fileSummary) {
        var fileKey = fileSummary.getKey();
        var startTime = System.currentTimeMillis();

        try {
            var documentText = parseDocument(fileKey);
            if (documentText.isEmpty()) {
                moveToFailed(fileKey, "Empty or unparseable document");
                return false;
            }

            // Store in knowledge base
            memoryService.createKnowledgeBaseEntry(documentText.get());

            // Move to processed folder
            moveToProcessed(fileKey);

            var duration = System.currentTimeMillis() - startTime;
            logger.info("Processed {} ({} bytes) in {}ms",
                    fileKey, fileSummary.getSize(), duration);

            return true;

        } catch (Exception e) {
            logger.error("Failed to process {}", fileKey, e);
            moveToFailed(fileKey, e.getMessage());
            return false;
        }
    }

    private Optional<String> parseDocument(String fileKey) {
        try (S3Object s3Object = s3Client.getObject(KNOWLEDGE_BASE_BUCKET_NAME, fileKey);
             InputStream inputStream = s3Object.getObjectContent()) {

            Document document = documentParser.parse(inputStream);

            if (document == null || document.text() == null || document.text().isBlank()) {
                logger.warn("Empty document: {}", fileKey);
                return Optional.empty();
            }

            return Optional.of(document.text());

        } catch (Exception e) {
            logger.error("Parse error for {}", fileKey, e);
            return Optional.empty();
        }
    }

    private void moveToProcessed(String sourceKey) {
        try {
            var destKey = PROCESSED_FOLDER + sourceKey.substring(INGEST_FOLDER.length());

            s3Client.copyObject(
                    KNOWLEDGE_BASE_BUCKET_NAME, sourceKey,
                    KNOWLEDGE_BASE_BUCKET_NAME, destKey
            );

            s3Client.deleteObject(KNOWLEDGE_BASE_BUCKET_NAME, sourceKey);

        } catch (Exception e) {
            logger.warn("Could not move {} to processed folder", sourceKey, e);
        }
    }

    private void moveToFailed(String sourceKey, String reason) {
        try {
            var destKey = FAILED_FOLDER + sourceKey.substring(INGEST_FOLDER.length());

            s3Client.copyObject(
                    KNOWLEDGE_BASE_BUCKET_NAME, sourceKey,
                    KNOWLEDGE_BASE_BUCKET_NAME, destKey
            );

            s3Client.deleteObject(KNOWLEDGE_BASE_BUCKET_NAME, sourceKey);

            logger.info("Moved failed document {} to failed folder. Reason: {}", sourceKey, reason);

        } catch (Exception e) {
            logger.error("Could not move {} to failed folder", sourceKey, e);
        }
    }

    private record ProcessingResult(int total, int processed, int failed) {}
}