package com.example.clean.architecture.service

import com.example.clean.architecture.notification.DocumentNotificationInterface
import com.example.clean.architecture.persistence.ObjectStorageInterface
import org.springframework.stereotype.Service

@Service
class AutoDocumentReviewer(
    private val objectStorage: ObjectStorageInterface,
    private val documentNotification: DocumentNotificationInterface,
) : ReviewAndNotifyDocument {

    override fun invoke(blobId: String): Result<String> = runCatching {
        val linkToDocument = objectStorage.generateSecureAccessUri(blobId)
        val review = "${someVeryComplexReviewBusinessLogic()}\n Download at: $linkToDocument"
        documentNotification.sendEmail(review)
        review
    }

    private fun someVeryComplexReviewBusinessLogic(): String =
        chapterReviews.random()

    companion object {
        private val chapterReviews = listOf(
            "Kept me guessing whether it would ever end — thrilling!",
            "Even AI stopped reading halfway through and needed a coffee break.",
            "Flows smoother than a Friday deploy on espresso.",
            "Dont beat around the bush, and get to the point!"
        )
    }
}
