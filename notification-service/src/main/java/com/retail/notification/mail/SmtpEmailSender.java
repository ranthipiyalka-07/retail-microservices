package com.retail.notification.mail;

import com.retail.notification.config.NotificationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Needs spring.mail.host (and usually port, username and password) in the configuration. */
@Component
@ConditionalOnProperty(name = "retail.notification.mail.mode", havingValue = "smtp")
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mail;
    private final String from;

    public SmtpEmailSender(JavaMailSender mail, NotificationProperties props) {
        this.mail = mail;
        this.from = props.mail().from();
    }

    @Override
    public void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();      // plain text only
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        try {
            mail.send(message);
        } catch (MailParseException | MailPreparationException e) {
            throw new MailDeliveryException(e.getMessage(), true, e);      // the message itself is wrong: no retry
        } catch (MailException e) {
            throw new MailDeliveryException(e.getMessage(), false, e);     // server down, timeout, login: try again later
        }
    }
}