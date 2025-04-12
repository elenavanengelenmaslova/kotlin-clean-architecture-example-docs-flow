package com.example.clean.architecture.persistence

interface ObjectStorageInterface {
    fun save(id: String, content: String): String
    fun get(id: String): String?
    fun delete(id: String)
    fun list(): List<String>
}
