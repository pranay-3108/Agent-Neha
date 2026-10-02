package com.sentineldroid

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature

class EvidenceSigner {
    private val alias = "sentinel-evidence-v1"

    init {
        ensureKey()
    }

    fun sign(bytes: ByteArray): String {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val privateKey = keyStore.getKey(alias, null) as java.security.PrivateKey
        return Signature.getInstance("SHA256withECDSA").run {
            initSign(privateKey)
            update(bytes)
            Hashing.base64(sign())
        }
    }

    fun verify(bytes: ByteArray, signatureBase64: String): Boolean = runCatching {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val certificate = keyStore.getCertificate(alias) ?: return false
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(certificate.publicKey)
            update(bytes)
            verify(java.util.Base64.getDecoder().decode(signatureBase64))
        }
    }.getOrDefault(false)

    fun publicKeyBase64(): String = runCatching {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        Hashing.base64(keyStore.getCertificate(alias).publicKey.encoded)
    }.getOrDefault("")

    private fun ensureKey() {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (keyStore.containsAlias(alias)) return

        val generator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            "AndroidKeyStore"
        )
        generator.initialize(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
        )
        generator.generateKeyPair()
    }
}
