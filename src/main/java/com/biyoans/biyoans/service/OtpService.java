package com.biyoans.biyoans.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Service
public class OtpService {
    private static final Logger log = LoggerFactory.getLogger(OtpService.class);
    private final SecureRandom random = new SecureRandom();
    private final RestTemplate restTemplate = new RestTemplate();

    // Generate a 6-digit OTP
    public String generateOtp() {
        int n = random.nextInt(1_000_000);
        return String.format("%06d", n);
    }

    // Send the OTP email via Brevo API
    public void sendOtpEmail(String to, String otp) {
        String url = "https://api.brevo.com/v3/smtp/email";
        
        // Render Dashboard se API Key uthayega
        String apiKey = System.getenv("BREVO_API_KEY"); 

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("api-key", apiKey);

            // Email Body Structure
            Map<String, Object> body = new HashMap<>();
            body.put("sender", Map.of("email", "sauravku7091@gmail.com", "name", "Biyoans"));
            body.put("to", Collections.singletonList(Map.of("email", to)));
            body.put("subject", "Biyoans Signup OTP Verification");
            
            String htmlContent = String.format("""
                <div style="font-family: Arial, sans-serif; line-height: 1.6;">
                  <h2 style="color: #4B0082;">Your One-Time Password (OTP)</h2>
                  <p><b>OTP Code:</b> <span style="font-size: 20px; color: #800000;">%s</span></p>
                  <p>This OTP is valid for 10 minutes. Please do not share it with anyone.</p>
                  <hr>
                  <p style="color: gray; font-size: 14px;">
                    Thank you for connecting with Biyoans Institute.
                  </p>
                </div>
                """, otp);
            
            body.put("htmlContent", htmlContent);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode() == HttpStatus.CREATED || response.getStatusCode() == HttpStatus.OK) {
                log.info("OTP email sent successfully via Brevo API to {}", to);
            } else {
                log.error("Failed to send OTP via API. Status: {}", response.getStatusCode());
            }

        } catch (Exception ex) {
            log.error("API Error while sending OTP to {}: {}", to, ex.getMessage());
        }
    }
}