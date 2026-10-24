package vn.phuong.financebridge

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class WebhookCredentialStoreTest {

    private lateinit var store: WebhookCredentialStore
    private val validSyntheticSecret = "s" + "x".repeat(63)

    @Before
    fun setUp() {
        store = WebhookCredentialStore.createForTesting()
    }

    @Test
    fun saveAndGetRoundTrip() {
        assertFalse(store.hasSecret())
        assertNull(store.getSecret())

        assertTrue(store.saveSecret(validSyntheticSecret))
        assertTrue(store.hasSecret())
        assertEquals(validSyntheticSecret, store.getSecret())
    }

    @Test
    fun emptySecretRejected() {
        assertFalse(store.saveSecret(""))
        assertFalse(store.hasSecret())
        assertNull(store.getSecret())
    }

    @Test
    fun invalidSecretRejected() {
        val invalidSecrets = listOf(
            "short",
            "   ",
            "a".repeat(31),
            "a".repeat(129),
            validSyntheticSecret + "!",
            validSyntheticSecret.replaceFirst("s", " "),
            "default".repeat(8),
            "placeholder".repeat(5),
            "changeme".repeat(6),
            "replace-with-" + "a".repeat(40),
            "mat-khau-bi-mat-12345678901234567890",
        )

        invalidSecrets.forEach { invalid ->
            assertFalse("Should reject: $invalid", store.saveSecret(invalid))
            assertFalse(store.hasSecret())
            assertNull(store.getSecret())
        }
    }

    @Test
    fun clearSecret() {
        assertTrue(store.saveSecret(validSyntheticSecret))
        assertTrue(store.hasSecret())

        assertTrue(store.clearSecret())
        assertFalse(store.hasSecret())
        assertNull(store.getSecret())
    }

    @Test
    fun secretNeverExposedInToStringOrErrors() {
        val secret = "Secret-Token-" + "k".repeat(40)
        store.saveSecret(secret)

        val stringRepresentation = store.toString()
        assertFalse("Secret leaked in toString()", stringRepresentation.contains(secret))
        assertTrue("Expected hasSecret=true in toString()", stringRepresentation.contains("hasSecret=true"))

        store.clearSecret()
        assertFalse(store.toString().contains(secret))
        assertTrue(store.toString().contains("hasSecret=false"))
    }

    @Test
    fun corruptedDataFailsSafely() {
        val storage = InMemoryCredentialStorage()
        storage.save("invalid-base-64!", "invalid-base-64!")
        val corruptedStore = WebhookCredentialStore.createForTesting(storage = storage)

        assertNull("Corrupted storage should return null without crashing", corruptedStore.getSecret())
    }
}
