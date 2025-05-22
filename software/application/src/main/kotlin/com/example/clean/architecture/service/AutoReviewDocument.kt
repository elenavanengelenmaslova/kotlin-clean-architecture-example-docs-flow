package com.example.clean.architecture.service

import com.example.clean.architecture.notification.DocumentNotificationInterface
import com.example.clean.architecture.persistence.ObjectStorageInterface
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class AutoDocumentReviewer(
    // TODO: Inject objectStorage and notification
) : ReviewAndNotifyDocument {

    override fun invoke(blobId: String): Result<String> = runCatching {
        // TODO: generate secure access URI
        val linkToDocument = "Hello World"

        val review = "${someVeryComplexReviewBusinessLogic()}\n Download at: $linkToDocument"
        logger.info { "Generated review: $review, sending email..." }
        // TODO: send email
        review
    }.onFailure { logger.error(it) { "Failed to generate and send a review: ${it.message}" } }


    /**
     * This is a very complex business logic, like analysis with publisher-specific machine learning models.
     */
    private fun someVeryComplexReviewBusinessLogic(): String =
        chapterReviews.random()

    companion object {
        private val chapterReviews = listOf(
            "Kept me guessing whether it would ever end — thrilling!",
            "Even AI stopped reading halfway through and needed a coffee break.",
            "Flows smoother than a Friday deploy on espresso.",
            "Don't beat around the bush, and get to the point!"
        )
    }
}
