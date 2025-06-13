package com.example.clean.architecture.persistence

interface ObjectStorageInterface {
    /**
     * Save content to storage
     */
    fun save(id: String, content: ByteArray): String
    /**
     * Generates a secure access URI for the given blob ID.
     */
    fun generateSecureAccessUri(id: String): String
}
