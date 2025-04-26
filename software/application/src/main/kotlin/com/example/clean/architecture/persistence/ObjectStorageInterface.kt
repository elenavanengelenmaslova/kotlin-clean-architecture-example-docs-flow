package com.example.clean.architecture.persistence

interface ObjectStorageInterface {
    fun save(id: String, content: ByteArray): String
    fun get(id: String): ByteArray?
    fun delete(id: String)
    fun list(): List<String>
}
