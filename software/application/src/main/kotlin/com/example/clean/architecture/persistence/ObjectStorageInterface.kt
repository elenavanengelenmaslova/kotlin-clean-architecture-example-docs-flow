package com.example.clean.architecture.persistence

interface ObjectStorageInterface {
    fun save(id: String, content: ByteArray): String
    fun get(id: String): ByteArray?
    fun delete(id: String)
    fun list(): List<String>
    /**
     * Generates a secure access URI for the given blob ID.
     */
    fun generateSecureAccessUri(id: String): String
}
