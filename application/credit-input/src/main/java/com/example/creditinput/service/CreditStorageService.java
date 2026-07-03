package com.example.creditinput.service;

import com.example.creditinput.model.CreditApplicationRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Collections;

@Service
public class CreditStorageService {

    private static final Logger log = LoggerFactory.getLogger(CreditStorageService.class);
    private static final Path STORAGE_PATH = Paths.get("data", "credit-input-records.jsonl");
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void save(CreditApplicationRequest request) {
        try {
            Files.createDirectories(STORAGE_PATH.getParent());
            String record = objectMapper.writeValueAsString(new StoredRecord(request, Instant.now().toString()));
            Files.write(STORAGE_PATH, Collections.singletonList(record), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            log.info("Saved credit application into local storage: {}", request);
        } catch (IOException ex) {
            log.error("Unable to persist credit application", ex);
            throw new IllegalStateException("Could not persist credit application", ex);
        }
    }

    private static final class StoredRecord {
        public final CreditApplicationRequest request;
        public final String createdAt;

        public StoredRecord(CreditApplicationRequest request, String createdAt) {
            this.request = request;
            this.createdAt = createdAt;
        }
    }
}
