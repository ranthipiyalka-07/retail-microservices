package com.retail.notification.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Development sender: prints the email to the console and sends nothing. It shows personal data, so it is not for production. */
@Component
@ConditionalOnProperty(name = "retail.notification.mail.mode", havingValue = "log", matchIfMissing = true)
public class LogEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LogEmailSender.class);

    public LogEmailSender() {
        log.warn("Email mode is LOG: emails are printed to the console and NOT delivered. Use mode 'smtp' for real delivery.");
    }

    @Override
    public void send(String to, String subject, String body) {
        log.info("""

                ---- EMAIL (log mode: not delivered) ----
                To: {}
                Subject: {}

                {}
                ---- END EMAIL ----""", to, subject, body);
    }
}