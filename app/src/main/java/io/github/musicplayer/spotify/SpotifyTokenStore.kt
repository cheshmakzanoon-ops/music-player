package io.github.musicplayer.spotify

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Access and refresh tokens are encrypted at rest by a non-exportable Android Keystore key. */
internal class SpotifyTokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("spotify-catalog-credentials", Context.MODE_PRIVATE)
    private val alias = "io.github.musicplayer.spotify.catalog.v1"
    private val encoder = Base64.getEncoder()
    private val decoder = Base64.getDecoder()

    @Synchronized
    fun save(tokens: SpotifyCatalogToken, clientId: String): Boolean = runCatching {
        require(SpotifyPkce.validClientId(clientId))
        val json = JsonObject().apply {
            addProperty("client_id", clientId)
            addProperty("access", tokens.accessToken)
            addProperty("refresh", tokens.refreshToken)
            addProperty("expires", tokens.expiresAtMs)
        }.toString().toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(json)
        prefs.edit().putString("token", encoder.encodeToString(cipher.iv + encrypted)).commit()
    }.getOrDefault(false)

    @Synchronized
    fun load(clientId: String): SpotifyCatalogToken? = runCatching {
        val stored = prefs.getString("token", null) ?: return null
        val bytes = decoder.decode(stored)
        require(bytes.size > 12 + 16)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        val json = JsonParser.parseString(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)).asJsonObject
        // Tokens are issued for one OAuth app. Never revive a session from an old Client ID.
        if (json.get("client_id")?.asString != clientId) {
            clear()
            return null
        }
        val access = json.get("access").asString
        val refresh = json.get("refresh")?.takeUnless { it.isJsonNull }?.asString
        val expires = json.get("expires").asLong
        if (access.isBlank() || expires <= 0) null else SpotifyCatalogToken(access, refresh, expires)
    }.getOrElse {
        // Invalidated hardware keys, corrupt storage or restore must not crash startup.
        clear()
        null
    }

    @Synchronized
    fun clear() { prefs.edit().remove("token").commit() }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }
}
