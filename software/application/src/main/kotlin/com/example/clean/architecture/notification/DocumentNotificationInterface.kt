package com.example.clean.architecture.notification

/**
 * Interface for sending notification
 */
fun interface DocumentNotificationInterface {
    fun sendEmail(review: String)
}