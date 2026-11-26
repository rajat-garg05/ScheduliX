package com.schedulix.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "false", matchIfMissing = true)
public class ConsoleEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(ConsoleEmailSender.class);

    private final Environment environment;

    public ConsoleEmailSender(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void send(String to, String subject, String body) {
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            log.warn("Email delivery is disabled in production; message was not sent.");
            return;
        }
        System.out.println("============================================================");
        System.out.println("ScheduliX Dev Email (APP_MAIL_ENABLED=false)");
        System.out.println("Subject: " + subject);
        System.out.println(body);
        System.out.println("============================================================");
    }
}