package com.example.clean.architecture.aws.notification

import aws.sdk.kotlin.services.ses.SesClient
import aws.sdk.kotlin.services.ses.model.Body
import aws.sdk.kotlin.services.ses.model.Content
import aws.sdk.kotlin.services.ses.model.Destination
import aws.sdk.kotlin.services.ses.model.Message
import aws.sdk.kotlin.services.ses.model.SendEmailRequest
import com.example.clean.architecture.notification.DocumentNotificationInterface
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class SESEmailSender(
    private val sesClient: SesClient,
    @Value("\${aws.ses.sender-email}") private val senderEmail: String,
    @Value("\${aws.ses.recipient-email}") private val recipientEmail: String,
) : DocumentNotificationInterface {

    override fun sendEmail(review: String): Unit = runBlocking {
        logger.info { "Sending review email via SES..." }
        runCatching {
            val sendRequest = SendEmailRequest {
                source = senderEmail
                destination = Destination {
                    toAddresses = listOf(recipientEmail)
                }
                message = Message {
                    subject = Content {
                        data = "Document Review"
                        charset = "UTF-8"
                    }
                    body = Body {
                        text = Content {
                            data = review
                            charset = "UTF-8"
                        }
                    }
                }
            }
            val result = sesClient.sendEmail(sendRequest)
            logger.info { "Email sent with message id: ${result.messageId}" }
        }.onFailure { e ->
            logger.error(e) { "Failed to send email via SES" }
        }.getOrThrow()
    }
}
