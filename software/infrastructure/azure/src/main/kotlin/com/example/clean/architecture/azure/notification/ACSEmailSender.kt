package com.example.clean.architecture.azure.notification

import com.azure.communication.email.EmailClient
import com.azure.communication.email.implementation.models.EmailContent
import com.azure.communication.email.implementation.models.EmailRecipients
import com.azure.communication.email.models.EmailAddress
import com.azure.communication.email.models.EmailMessage
import com.example.clean.architecture.notification.DocumentNotificationInterface
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
@Primary
class ACSEmailSender(
    private val emailClient: EmailClient,
    @Value("\${azure.acs.sender-email}") private val senderEmail: String,
    @Value("\${azure.acs.recipient-email}") private val recipientEmail: String,
) : DocumentNotificationInterface {

    override fun sendEmail(review: String): Unit {
        logger.info { "Sending review email via Azure Communication Services..." }
        runCatching {
            val message = EmailMessage()
                .setSenderAddress(senderEmail)
                .setToRecipients(recipientEmail)
                .setSubject("Document Review")
                .setBodyPlainText(review)
            val result = emailClient.beginSend(message).finalResult
            result.error?.let { error("${result.error.code}: ${result.error.message}") }
        }.onFailure { e ->
            logger.error(e) { "Failed to send email via Azure ACS" }
        }.getOrThrow()
    }
}