package com.seasonyuu.fnmusic.core.airplay

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Device-scoped pairing credentials; no NAS credentials enter this store. */
class AirPlayCredentialStore(context: Context) {
    private val prefs = context.getSharedPreferences("airplay_pairings", Context.MODE_PRIVATE)
    private val alias = "fnmusic_airplay_pairing_v1"
    @Synchronized
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun read(deviceId: String): String {
        val id = normalizeDeviceId(deviceId).also { require(it.isNotEmpty()) }
        val encoded = prefs.getString(id, null) ?: return ""
        return runCatching {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            require(bytes.size >= 28)
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
                updateAAD(id.toByteArray())
                doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
            }
        }.getOrElse {
            prefs.edit().remove(id).commit()
            ""
        }
    }
    fun write(deviceId: String, credentials: String) {
        val id = normalizeDeviceId(deviceId).also { require(it.isNotEmpty()) }
        require(credentials.length <= 16_384)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key())
            updateAAD(id.toByteArray())
        }
        val bytes = cipher.iv + cipher.doFinal(credentials.toByteArray())
        check(prefs.edit().putString(id, Base64.encodeToString(bytes, Base64.NO_WRAP)).commit())
    }
}
