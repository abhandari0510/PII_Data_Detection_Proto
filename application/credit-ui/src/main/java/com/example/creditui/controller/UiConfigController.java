package com.example.creditui.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

@RestController
public class UiConfigController {

    private final String creditInputApplyUrl;

    public UiConfigController(@Value("${app.credit-input-apply-url}") String creditInputApplyUrl) {
        this.creditInputApplyUrl = creditInputApplyUrl;
    }

    @GetMapping("/ui-config")
    public Map<String, String> uiConfig() {
        return Collections.singletonMap("creditInputApplyUrl", creditInputApplyUrl);
    }
}
