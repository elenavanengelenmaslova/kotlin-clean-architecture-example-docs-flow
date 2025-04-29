package com.example.clean.architecture.notification

fun interface DocumentNotificationInterface {
    fun sendEmail(review: String)
}