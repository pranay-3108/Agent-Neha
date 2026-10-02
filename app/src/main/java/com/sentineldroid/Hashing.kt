package com.sentineldroid

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Base64

object Hashing {
    fun sha256(value: String): String = sha256(value.toByteArray(Charsets.UTF_8))

    fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun canonicalBytes(values: List<String>): ByteArray {
        val size = values.sumOf { 4 + it.toByteArray(Charsets.UTF_8).size }
        val buffer = ByteBuffer.allocate(size)
        values.forEach {
            val bytes = it.toByteArray(Charsets.UTF_8)
            buffer.putInt(bytes.size)
            buffer.put(bytes)
        }
        return buffer.array()
    }

    fun canonicalHash(values: List<String>): String = sha256(canonicalBytes(values))

    fun base64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
}
