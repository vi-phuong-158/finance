package vn.phuong.financebridge

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface WebhookCredentialStore {
    fun saveSecret(secret: String): Boolean
    fun getSecret(): String?
    fun hasSecret(): Boolean
    fun clearSecret(): Boolean

    companion object {
        operator fun invoke(context: Context): WebhookCredentialStore =
            EncryptedWebhookCredentialStore(
                storage = SharedPreferencesCredentialStorage(context.applicationContext),
                cryptoEngine = AndroidKeystoreCryptoEngine(),
            )

        fun createForTesting(
            storage: CredentialStorage = InMemoryCredentialStorage(),
            cryptoEngine: CredentialCryptoEngine = JvmAesGcmCryptoEngine(),
        ): WebhookCredentialStore = EncryptedWebhookCredentialStore(storage, cryptoEngine)
    }
}

data class EncryptedPayload(val iv: ByteArray, val ciphertext: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedPayload) return false
        return iv.contentEquals(other.iv) && ciphertext.contentEquals(other.ciphertext)
    }

    override fun hashCode(): Int = 31 * iv.contentHashCode() + ciphertext.contentHashCode()
}

interface CredentialStorage {
    fun save(ivBase64: String, ciphertextBase64: String): Boolean
    fun load(): Pair<String, String>?
    fun clear(): Boolean
    fun hasData(): Boolean
}

interface CredentialCryptoEngine {
    fun encrypt(plaintext: ByteArray): EncryptedPayload
    fun decrypt(iv: ByteArray, ciphertext: ByteArray): ByteArray
    fun clearKey()
}

class EncryptedWebhookCredentialStore internal constructor(
    private val storage: CredentialStorage,
    private val cryptoEngine: CredentialCryptoEngine,
) : WebhookCredentialStore {

    override fun saveSecret(secret: String): Boolean {
        if (!WebhookConfiguration.isValidSecret(secret)) {
            return false
        }
        return try {
            val payload = cryptoEngine.encrypt(secret.toByteArray(StandardCharsets.UTF_8))
            val ivBase64 = Base64.getEncoder().encodeToString(payload.iv)
            val cipherBase64 = Base64.getEncoder().encodeToString(payload.ciphertext)
            storage.save(ivBase64, cipherBase64)
        } catch (_: Exception) {
            false
        }
    }

    override fun getSecret(): String? {
        val (ivBase64, cipherBase64) = storage.load() ?: return null
        return try {
            val iv = Base64.getDecoder().decode(ivBase64)
            val ciphertext = Base64.getDecoder().decode(cipherBase64)
            val decrypted = cryptoEngine.decrypt(iv, ciphertext)
            val secret = String(decrypted, StandardCharsets.UTF_8)
            if (WebhookConfiguration.isValidSecret(secret)) secret else null
        } catch (_: Exception) {
            null
        }
    }

    override fun hasSecret(): Boolean = storage.hasData()

    override fun clearSecret(): Boolean {
        val cleared = storage.clear()
        cryptoEngine.clearKey()
        return cleared
    }

    override fun toString(): String = "WebhookCredentialStore(hasSecret=${hasSecret()})"
}

class SharedPreferencesCredentialStorage(context: Context) : CredentialStorage {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun save(ivBase64: String, ciphertextBase64: String): Boolean {
        return preferences.edit()
            .putString(KEY_IV, ivBase64)
            .putString(KEY_CIPHER, ciphertextBase64)
            .commit()
    }

    override fun load(): Pair<String, String>? {
        val iv = preferences.getString(KEY_IV, null) ?: return null
        val cipher = preferences.getString(KEY_CIPHER, null) ?: return null
        return Pair(iv, cipher)
    }

    override fun clear(): Boolean {
        return preferences.edit()
            .remove(KEY_IV)
            .remove(KEY_CIPHER)
            .commit()
    }

    override fun hasData(): Boolean {
        return preferences.contains(KEY_IV) && preferences.contains(KEY_CIPHER)
    }

    companion object {
        private const val PREFS_NAME = "finance_bridge_secure_credentials"
        private const val KEY_IV = "webhook_secret_iv"
        private const val KEY_CIPHER = "webhook_secret_cipher"
    }
}

class InMemoryCredentialStorage : CredentialStorage {
    private var iv: String? = null
    private var cipher: String? = null

    override fun save(ivBase64: String, ciphertextBase64: String): Boolean {
        iv = ivBase64
        cipher = ciphertextBase64
        return true
    }

    override fun load(): Pair<String, String>? {
        val currentIv = iv ?: return null
        val currentCipher = cipher ?: return null
        return Pair(currentIv, currentCipher)
    }

    override fun clear(): Boolean {
        iv = null
        cipher = null
        return true
    }

    override fun hasData(): Boolean = iv != null && cipher != null
}

class AndroidKeystoreCryptoEngine(
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
) : CredentialCryptoEngine {

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existing = keyStore.getKey(keyAlias, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    override fun encrypt(plaintext: ByteArray): EncryptedPayload {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return EncryptedPayload(iv = cipher.iv, ciphertext = cipher.doFinal(plaintext))
    }

    override fun decrypt(iv: ByteArray, ciphertext: ByteArray): ByteArray {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    override fun clearKey() {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        if (keyStore.containsAlias(keyAlias)) {
            keyStore.deleteEntry(keyAlias)
        }
    }

    companion object {
        const val DEFAULT_KEY_ALIAS = "finance_bridge_webhook_secret_key"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
    }
}

class JvmAesGcmCryptoEngine : CredentialCryptoEngine {
    private var secretKey: SecretKey? = null

    private fun getOrCreateKey(): SecretKey {
        return secretKey ?: run {
            val keyGenerator = KeyGenerator.getInstance("AES")
            keyGenerator.init(256)
            val generated = keyGenerator.generateKey()
            secretKey = generated
            generated
        }
    }

    override fun encrypt(plaintext: ByteArray): EncryptedPayload {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        return EncryptedPayload(iv = cipher.iv, ciphertext = cipher.doFinal(plaintext))
    }

    override fun decrypt(iv: ByteArray, ciphertext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }

    override fun clearKey() {
        secretKey = null
    }
}
