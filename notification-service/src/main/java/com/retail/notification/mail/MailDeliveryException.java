package com.retail.notification.mail;

/** permanent = retrying cannot help (the message itself is wrong), so the email is not retried. */
public class MailDeliveryException extends RuntimeException {

    private final boolean permanent;

    public MailDeliveryException(String message, boolean permanent, Throwable cause) {
        super(message, cause);
        this.permanent = permanent;
    }

    public boolean isPermanent() {
        return permanent;
    }
}