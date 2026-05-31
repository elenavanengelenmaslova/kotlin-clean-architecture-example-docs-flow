package com.example.clean.architecture.aws.notification

import aws.sdk.kotlin.services.ses.SesClient
import aws.sdk.kotlin.services.ses.model.GetSendQuotaRequest
import aws.sdk.kotlin.services.ses.model.SendEmailRequest
import io.mockk.clearMocks
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

/**
 * Verifies the SES adapter's Phase 2 (Warmable) credential/connection warmup performs a single
 * low-cost `getSendQuota` call and does NOT send a real email.
 */
class SESEmailSenderTest {

    private val sesClient: SesClient = mockk(relaxed = true)
    private val senderEmail = "sender@example.com"
    private val recipientEmail = "recipient@example.com"

    private val emailSender = SESEmailSender(sesClient, senderEmail, recipientEmail)

    @AfterEach
    fun tearDown() {
        clearMocks(sesClient)
    }

    @Test
    fun `Given a cold instance When warmUp is called Then it warms credentials via a single getSendQuota call`() {
        // When
        emailSender.warmUp()

        // Then — exactly one getSendQuota call warms the SES connection/credentials
        coVerify(exactly = 1) { sesClient.getSendQuota(any<GetSendQuotaRequest>()) }
    }

    @Test
    fun `Given a cold instance When warmUp is called Then it does NOT send a real email`() {
        // When
        emailSender.warmUp()

        // Then — warmup is credential/connection priming only, never a real send
        coVerify(exactly = 0) { sesClient.sendEmail(any<SendEmailRequest>()) }
    }
}
