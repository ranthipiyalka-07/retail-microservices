package com.retail.notification.mail;

/** The only class of this service that talks to a mail server. Implementations must be safe to call repeatedly. */
public interface EmailSender {

    /** Throws MailDeliveryException when the email could not be sent. */
    void send(String to, String subject, String body);
}