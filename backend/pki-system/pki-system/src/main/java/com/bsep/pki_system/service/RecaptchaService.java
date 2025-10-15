package com.bsep.pki_system.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.Map;

@Service
public class RecaptchaService {
    @Value("${recaptcha.secret.key}")
    private String secretKey;

    private static final String VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";

    public boolean verify(String recaptchaToken) {
        if (recaptchaToken == null || recaptchaToken.isEmpty()) {
            return false;
        }
        RestTemplate restTemplate = new RestTemplate();
        String params = String.format("?secret=%s&response=%s", secretKey, recaptchaToken);
        try {
            Map<String, Object> response = restTemplate.getForObject(VERIFY_URL + params, Map.class);
            return response != null && Boolean.TRUE.equals(response.get("success"));
        } catch (Exception e) {
            return false;
        }
    }
}