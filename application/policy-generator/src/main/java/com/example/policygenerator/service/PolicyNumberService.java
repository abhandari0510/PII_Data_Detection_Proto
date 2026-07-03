package com.example.policygenerator.service;

import com.example.policygenerator.model.CreditApplicationRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PolicyNumberService {

    private static final Logger log = LoggerFactory.getLogger(PolicyNumberService.class);
    private static final Path STORAGE_PATH = Paths.get("data", "policy-generator-applications.json");
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, String> applicationNumberMap = new ConcurrentHashMap<>();

    public PolicyNumberService() {
        loadMappings();
    }

    public String getOrCreateApplicationNumber(CreditApplicationRequest request) {
        String key = buildKey(request);
        boolean wasAbsent = !applicationNumberMap.containsKey(key);
        String number = applicationNumberMap.computeIfAbsent(key, this::createApplicationNumberFromHash);
        if (wasAbsent) {
            persistMappings();
            log.info("Generated new application number {} for key {}", number, key);
        }
        return number;
    }

    private void loadMappings() {
        try {
            if (Files.exists(STORAGE_PATH)) {
                String content = new String(Files.readAllBytes(STORAGE_PATH), StandardCharsets.UTF_8);
                Map<String, String> existing = objectMapper.readValue(content, new TypeReference<Map<String, String>>() {});
                applicationNumberMap.putAll(existing);
                log.info("Loaded {} policy mappings from local storage", existing.size());
            }
        } catch (IOException e) {
            log.warn("Unable to read existing policy generator mappings", e);
        }
    }

    private void persistMappings() {
        try {
            Files.createDirectories(STORAGE_PATH.getParent());
            byte[] content = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(applicationNumberMap)
                    .getBytes(StandardCharsets.UTF_8);
            Files.write(STORAGE_PATH, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("Persisted {} policy mappings", applicationNumberMap.size());
        } catch (IOException e) {
            log.error("Unable to persist policy mappings", e);
        }
    }

    private String buildKey(CreditApplicationRequest request) {
        return String.join("|",
                normalize(request.getName()),
                normalize(request.getPhoneNumber()),
                normalize(request.getAadharNumber()),
                normalize(request.getPanNumber()),
                normalize(request.getCreditCardNumber()),
                normalize(request.getCreditCardExpiry()),
                normalize(request.getCvc())
        );
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private String createApplicationNumberFromHash(String key) {
        byte[] digest = sha256(key);
        BigInteger integer = new BigInteger(1, digest);
        BigInteger modulus = BigInteger.TEN.pow(10);
        String candidate = integer.mod(modulus).toString();
        return String.format("APP%010d", new BigInteger(candidate));
    }

    private byte[] sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(input.getBytes());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
