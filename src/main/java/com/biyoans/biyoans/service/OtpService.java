package com.biyoans.biyoans.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.MimeMessageHelper;
import java.security.SecureRandom;

@Service
public class OtpService {
    private static final Logger log = LoggerFactory.getLogger(OtpService.class);
    private final JavaMailSender mailSender;
    private final SecureRandom random = new SecureRandom();

    public OtpService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /** Generate a 6-digit OTP like "034921" */
    public String generateOtp() {
        int n = random.nextInt(1_000_000);   // 0..999999
        return String.format("%06d", n);
    }

    /** Send the OTP email to the given address */
    public void sendOtpEmail(String to, String otp) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject("Biyoans Signup OTP Verification");

            String content = """
                <div style="font-family: Arial, sans-serif; line-height: 1.6;">
                  <h2 style="color: #4B0082;">Your One-Time Password (OTP)</h2>
                  <p><b>OTP Code:</b> <span style="font-size: 20px; color: #800000;">%s</span></p>
                  <p>This OTP is valid for <b>10 minutes</b>. Please do not share it with anyone.</p>
                  <hr>
                  <p style="color: gray; font-size: 14px;">
                    Thank you for connecting with <b>Biyoans Institute</b>.<br>
                    We look forward to serving you.
                  </p>
                </div>
                """.formatted(otp);

            helper.setText(content, true); // true = HTML
            mailSender.send(message);
            log.info("OTP email sent to {}", to);
        } catch (MessagingException ex) {
            log.error("Failed to send OTP email to {}: {}", to, ex.getMessage(), ex);
        }
    }
}
