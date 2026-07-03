package com.example.creditui.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
public class CreditProxyController {

    private final RestTemplate restTemplate;
    private final String creditInputApplyUrl;

    public CreditProxyController(
            RestTemplate restTemplate,
            @Value("${app.credit-input-upstream-apply-url}") String creditInputApplyUrl
    ) {
        this.restTemplate = restTemplate;
        this.creditInputApplyUrl = creditInputApplyUrl;
    }

    @PostMapping("/api/credit/apply")
    public ResponseEntity<String> apply(@RequestBody String payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    creditInputApplyUrl,
                    new HttpEntity<>(payload, headers),
                    String.class
            );
            return ResponseEntity
                    .status(response.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response.getBody());
        } catch (HttpStatusCodeException ex) {
            return ResponseEntity
                    .status(ex.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ex.getResponseBodyAsString());
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to contact credit-input service.", ex);
        }
    }
}
