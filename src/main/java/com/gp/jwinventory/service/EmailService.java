package com.gp.jwinventory.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${app.email-enabled}")
    private boolean emailEnabled;

    @Value("${app.mail.from}")
    private String fromAddress;

    @Value("${app.mail.from-name}")
    private String fromName;

    /** Recipients go in BCC so addresses are not disclosed to each other. */
    public void send(List<String> recipients, String subject, String body) {
        if (!emailEnabled) {
            log.info("Email disabled (EMAIL_ENABLED=false): skipping '{}' to {} recipient(s)", subject, recipients.size());
            return;
        }
        Context context = new Context(Locale.ITALIAN);
        context.setVariable("subject", subject);
        context.setVariable("lines", body.split("\\R", -1));
        String html = templateEngine.process("email/notification", context);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(new InternetAddress(fromAddress, fromName));
            helper.setTo(fromAddress);
            helper.setBcc(recipients.toArray(String[]::new));
            helper.setSubject(subject);
            helper.setText(body, html);
            mailSender.send(message);
            log.info("Email '{}' sent to {} recipient(s)", subject, recipients.size());
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new IllegalStateException("Composizione email non riuscita: " + e.getMessage(), e);
        }
    }
}
