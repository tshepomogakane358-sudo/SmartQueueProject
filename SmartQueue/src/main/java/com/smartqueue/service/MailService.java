package com.smartqueue.service;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.util.Properties;

public class MailService {

    // Gmail account (accessed via Outlook, but the mail server is still Gmail's)
    private static final String FROM_EMAIL = "tshepomogakane358@gmail.com";
    private static final String PASSWORD = "wswi rrzc piht wagn";
    private static final String TO_EMAIL = "tshepomogakane358@gmail.com";

    public void sendMail(String senderName, String senderEmail, String phone,
                         String subject, String message) throws MessagingException {

        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");

        Session session = Session.getInstance(props, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(FROM_EMAIL, PASSWORD);
            }
        });

        Message mimeMessage = new MimeMessage(session);
        mimeMessage.setFrom(new InternetAddress(FROM_EMAIL));
        mimeMessage.setRecipients(Message.RecipientType.TO, InternetAddress.parse(TO_EMAIL));
        mimeMessage.setSubject("SmartQueue Contact Form: " + subject);
        mimeMessage.setText(
                "Name: " + senderName + "\n" +
                        "Email: " + senderEmail + "\n" +
                        "Phone: " + phone + "\n\n" +
                        "Message:\n" + message
        );

        Transport.send(mimeMessage);
    }


    public void sendSimpleMail(String toEmail, String subject, String bodyText) throws MessagingException {

        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");

        Session session = Session.getInstance(props, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(FROM_EMAIL, PASSWORD);
            }
        });

        Message mimeMessage = new MimeMessage(session);
        mimeMessage.setFrom(new InternetAddress(FROM_EMAIL));
        mimeMessage.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
        mimeMessage.setSubject(subject);
        mimeMessage.setText(bodyText);

        Transport.send(mimeMessage);
    }
}