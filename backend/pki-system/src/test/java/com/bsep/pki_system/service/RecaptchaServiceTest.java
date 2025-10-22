package com.bsep.pki_system.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecaptchaServiceTest {

    private RecaptchaService recaptchaService;
    private static final String SECRET_KEY = "dummy-secret";
    private static final String TOKEN = "valid-token";
    private static final String VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";

    @BeforeEach
    void setUp() {
        recaptchaService = new RecaptchaService();
        ReflectionTestUtils.setField(recaptchaService, "secretKey", SECRET_KEY);
    }

    @Test
    void testVerify_ReturnsTrue_WhenResponseSuccess() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);

        try (MockedConstruction<RestTemplate> mocked = Mockito.mockConstruction(RestTemplate.class,
                (mock, context) -> when(mock.getForObject(anyString(), eq(Map.class))).thenReturn(response))) {

            boolean result = recaptchaService.verify(TOKEN);
            assertTrue(result);
        }
    }

    @Test
    void testVerify_ReturnsFalse_WhenResponseIsNull() {
        try (MockedConstruction<RestTemplate> mocked = Mockito.mockConstruction(RestTemplate.class,
                (mock, context) -> when(mock.getForObject(anyString(), eq(Map.class))).thenReturn(null))) {

            boolean result = recaptchaService.verify(TOKEN);
            assertFalse(result);
        }
    }

    @Test
    void testVerify_ReturnsFalse_WhenSuccessIsFalse() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);

        try (MockedConstruction<RestTemplate> mocked = Mockito.mockConstruction(RestTemplate.class,
                (mock, context) -> when(mock.getForObject(anyString(), eq(Map.class))).thenReturn(response))) {

            boolean result = recaptchaService.verify(TOKEN);
            assertFalse(result);
        }
    }

    @Test
    void testVerify_ReturnsFalse_WhenTokenIsNullOrEmpty() {
        assertFalse(recaptchaService.verify(null));
        assertFalse(recaptchaService.verify(""));
    }

    @Test
    void testVerify_ReturnsFalse_WhenExceptionOccurs() {
        try (MockedConstruction<RestTemplate> mocked = Mockito.mockConstruction(RestTemplate.class,
                (mock, context) -> when(mock.getForObject(anyString(), eq(Map.class)))
                        .thenThrow(new RuntimeException("Network error")))) {

            boolean result = recaptchaService.verify(TOKEN);
            assertFalse(result);
        }
    }
}
