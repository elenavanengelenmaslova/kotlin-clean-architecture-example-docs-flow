package com.example.clean.architecture.azure.notification

import com.azure.communication.email.EmailClient
import com.azure.communication.email.models.EmailMessage
import com.azure.core.credential.TokenCredential
import com.azure.core.credential.TokenRequestContext
import com.example.clean.architecture.notification.DocumentNotificationInterface
import com.example.clean.architecture.warmup.Warmable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
@Primary
class ACSEmailSender(
    private val emailClient: EmailClient,
    private val azureCredential: TokenCredential,
    @Value("\${azure.acs.sender-email}") private val senderEmail: String,
    @Value("\${azure.acs.recipient-email}") private val recipientEmail: String,
) : DocumentNotificationInterface, Warmable {

    /**
     * Phase 2 priming: forces the `DefaultAzureCredential` managed-identity token fetch
     * for the Azure Communication Services scope. This is credential warmup ONLY — it
     * does NOT send a real email — so the first production send avoids the token-fetch latency.
     */
    override fun warmUp() {
        logger.info { "Warming up Azure Communication Services managed-identity credential" }
        // Fetch the ACS-scoped token to prime the credential cache; no email is sent.
        azureCredential.getTokenSync(TokenRequestContext().addScopes(ACS_SCOPE))
    }

    override fun sendEmail(review: String) {
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
            logger.error(e) { "Failed to send email via Azure ACS: ${e.message}" }
        }.getOrThrow()
    }

    private companion object {
        const val ACS_SCOPE = "https://communication.azure.com/.default"
    }
}