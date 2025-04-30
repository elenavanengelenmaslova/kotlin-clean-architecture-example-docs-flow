package com.example.clean.architecture.persistence

interface ObjectStorageInterface {
    fun save(id: String, content: ByteArray): String
    /**
     * Generates a secure access URI for the given blob ID.
     */
    fun generateSecureAccessUri(id: String): String
}
