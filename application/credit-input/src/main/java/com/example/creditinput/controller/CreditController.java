package com.example.creditinput.controller;

import com.example.creditinput.model.CreditApplicationRequest;
import com.example.creditinput.model.CreditApplicationResponse;
import com.example.creditinput.model.PolicyResponse;
import com.example.creditinput.service.CreditStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/credit")
@CrossOrigin(origins = "http://localhost:8085")
public class CreditController {

    private static final Logger log = LoggerFactory.getLogger(CreditController.class);
    private static final String NAME_PATTERN = "[A-Z][a-z]+(?: [A-Z][a-z]+){1,3}";
    private static final String PHONE_PATTERN = "[6-9]\\d{9}";
    private static final String AADHAAR_PATTERN = "[2-9]\\d{11}";
    private static final String PAN_PATTERN = "[A-Z]{5}\\d{4}[A-Z]";
    private static final String CREDIT_CARD_PATTERN = "\\d{13,19}";
    private static final String CREDIT_CARD_EXPIRY_PATTERN = "(0[1-9]|1[0-2])/\\d{4}";
    private static final String CVC_PATTERN = "\\d{3,4}";
    private final CreditStorageService storageService;
    private final RestTemplate restTemplate;
    private final String policyGeneratorGenerateUrl;

    public CreditController(
            CreditStorageService storageService,
            RestTemplate restTemplate,
            @Value("${app.policy-generator-generate-url}") String policyGeneratorGenerateUrl
    ) {
        this.storageService = storageService;
        this.restTemplate = restTemplate;
        this.policyGeneratorGenerateUrl = policyGeneratorGenerateUrl;
    }

    @PostMapping("/apply")
    public ResponseEntity<CreditApplicationResponse> apply(@RequestBody CreditApplicationRequest request) {
        validate(request);
        storageService.save(request);
        log.info("Received credit application: {}", request);

        PolicyResponse policyResponse;
        try {
            log.info("Calling policy-generator via {}", policyGeneratorGenerateUrl);
            policyResponse = restTemplate.postForObject(
                    policyGeneratorGenerateUrl,
                    request,
                    PolicyResponse.class
            );
            if (policyResponse != null) {
                log.info("Received policy-generator response with application number {}", policyResponse.getApplicationNumber());
            }
        } catch (Exception ex) {
            log.error("Failed to call policy-generator service", ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to contact policy-generator service.");
        }

        if (policyResponse == null || policyResponse.getApplicationNumber() == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Policy-generator service returned invalid response.");
        }

        return ResponseEntity.ok(new CreditApplicationResponse(policyResponse.getApplicationNumber()));
    }

    private void validate(CreditApplicationRequest request) {
        if (request.getName() == null || !request.getName().matches(NAME_PATTERN)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name must use 2 to 4 words with leading capitals.");
        }
        if (request.getPhoneNumber() == null || !request.getPhoneNumber().matches(PHONE_PATTERN)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phone number must be a 10-digit Indian mobile number starting with 6 to 9.");
        }
        if (request.getAadharNumber() == null || !request.getAadharNumber().matches(AADHAAR_PATTERN)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aadhaar number must be 12 digits and start with 2 to 9.");
        }
        if (request.getPanNumber() == null || !request.getPanNumber().matches(PAN_PATTERN)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PAN number must match the standard format ABCDE1234F.");
        }
        if (request.getCreditCardNumber() == null || !request.getCreditCardNumber().matches(CREDIT_CARD_PATTERN)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Credit card number must contain 13 to 19 digits.");
        }
        if (request.getCreditCardExpiry() == null || !request.getCreditCardExpiry().matches(CREDIT_CARD_EXPIRY_PATTERN)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Credit card expiry must use MM/YYYY format.");
        }
        if (request.getCvc() == null || !request.getCvc().matches(CVC_PATTERN)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CVC must be 3 or 4 digits.");
        }
    }
}
