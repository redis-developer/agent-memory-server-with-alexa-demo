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
import dev.langchain4j.data.document.DocumentSplitter;
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
    private final DocumentSplitter documentSplitter;

    public KnowledgeBaseIntentHandler(DocumentParser documentParser,
                                      DocumentSplitter documentSplitter,
                                      MemoryService memoryService) {
        this.documentParser = documentParser;
        this.documentSplitter = documentSplitter;
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

        logger.info("Completed - Total files: {}, Processed: {}, Failed: {}, Total chunks: {}",
                result.totalFiles(), result.processedFiles(), result.failedFiles(), result.totalChunks());

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
        var processedFiles = new AtomicInteger(0);
        var failedFiles = new AtomicInteger(0);
        var totalChunks = new AtomicInteger(0);

        files.parallelStream().forEach(file -> {
            var chunksCreated = processFile(file);
            if (chunksCreated > 0) {
                processedFiles.incrementAndGet();
                totalChunks.addAndGet(chunksCreated);
            } else {
                failedFiles.incrementAndGet();
            }
        });

        return new ProcessingResult(
                files.size(),
                processedFiles.get(),
                failedFiles.get(),
                totalChunks.get()
        );
    }

    private int processFile(S3ObjectSummary fileSummary) {
        var fileKey = fileSummary.getKey();
        var fileName = extractFileName(fileKey);
        var startTime = System.currentTimeMillis();

        try {
            // Parse the document
            var document = parseDocument(fileKey);
            if (document.isEmpty()) {
                moveToFailed(fileKey, "Empty or unparseable document");
                return 0;
            }

            // Split into segments
            var segments = documentSplitter.split(document.get());
            if (segments.isEmpty()) {
                moveToFailed(fileKey, "No segments created from document");
                return 0;
            }

            // Store each segment in knowledge base
            var chunksStored = 0;
            for (int i = 0; i < segments.size(); i++) {
                var segment = segments.get(i);

                // Skip very short segments
                if (segment.text().trim().length() < 50) {
                    continue;
                }

                // Create entry with metadata
                var entryText = formatSegmentWithMetadata(
                        segment.text(),
                        fileName,
                        i + 1,
                        segments.size()
                );

                try {
                    memoryService.createKnowledgeBaseEntry(entryText);
                    chunksStored++;
                } catch (Exception e) {
                    logger.warn("Failed to store segment {} of {} from {}",
                            i + 1, segments.size(), fileName, e);
                }
            }

            if (chunksStored == 0) {
                moveToFailed(fileKey, "No segments could be stored");
                return 0;
            }

            // Move to processed folder
            moveToProcessed(fileKey);

            var duration = System.currentTimeMillis() - startTime;
            logger.info("Processed {} ({} bytes) in {}ms - {} segments stored out of {} total",
                    fileName, fileSummary.getSize(), duration, chunksStored, segments.size());

            return chunksStored;

        } catch (Exception e) {
            logger.error("Failed to process {}", fileKey, e);
            moveToFailed(fileKey, e.getMessage());
            return 0;
        }
    }

    private Optional<Document> parseDocument(String fileKey) {
        try (S3Object s3Object = s3Client.getObject(KNOWLEDGE_BASE_BUCKET_NAME, fileKey);
             InputStream inputStream = s3Object.getObjectContent()) {

            Document document = documentParser.parse(inputStream);

            if (document == null || document.text() == null || document.text().isBlank()) {
                logger.warn("Empty document: {}", fileKey);
                return Optional.empty();
            }

            return Optional.of(document);

        } catch (Exception e) {
            logger.error("Parse error for {}", fileKey, e);
            return Optional.empty();
        }
    }

    private String formatSegmentWithMetadata(String segmentText,
                                             String fileName,
                                             int segmentNumber,
                                             int totalSegments) {
        // Add metadata header to help with context understanding
        var metadata = String.format(
                "[Document: %s | Section %d of %d]\n",
                fileName,
                segmentNumber,
                totalSegments
        );

        return metadata + segmentText.trim();
    }

    private String extractFileName(String fileKey) {
        var lastSlash = fileKey.lastIndexOf('/');
        return lastSlash >= 0 ? fileKey.substring(lastSlash + 1) : fileKey;
    }

    private void moveToProcessed(String sourceKey) {
        try {
            var destKey = PROCESSED_FOLDER + sourceKey.substring(INGEST_FOLDER.length());

            s3Client.copyObject(
                    KNOWLEDGE_BASE_BUCKET_NAME, sourceKey,
                    KNOWLEDGE_BASE_BUCKET_NAME, destKey
            );

            s3Client.deleteObject(KNOWLEDGE_BASE_BUCKET_NAME, sourceKey);

            logger.debug("Moved {} to processed folder", sourceKey);

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

    private record ProcessingResult(
            int totalFiles,
            int processedFiles,
            int failedFiles,
            int totalChunks
    ) {}
}