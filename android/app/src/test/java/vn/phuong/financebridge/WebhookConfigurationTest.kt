package vn.phuong.financebridge

import org.junit.Assert.*
import org.junit.Test

class WebhookConfigurationTest {
    private val endpoint = "https://finance.example.invalid/api/transaction"
    private val syntheticSecret = "a".repeat(64)

    @Test fun acceptsPrivateHttpsConfiguration() {
        assertTrue(WebhookConfiguration.isValid(endpoint, syntheticSecret))
        assertTrue(WebhookConfiguration.isValidEndpoint(endpoint))
        assertTrue(WebhookConfiguration.isValidSecret(syntheticSecret))
    }

    @Test fun rejectsMissingMalformedAndInsecureUrls() {
        listOf(
            "",
            "not a URL",
            endpoint.replace("https", "http"),
            "https://user:pass@finance.example.invalid/api/transaction",
            "$endpoint?secret=x",
            "$endpoint#fragment",
            "https://finance.example.invalid/other-path",
        ).forEach {
            assertFalse(it, WebhookConfiguration.isValid(it, syntheticSecret))
            assertFalse(it, WebhookConfiguration.isValidEndpoint(it))
        }
    }

    @Test fun rejectsBlankDefaultAndPlaceholderSecrets() {
        listOf("", "short", "default".repeat(8), "placeholder".repeat(5), "changeme".repeat(6), "replace-with-" + "a".repeat(40))
            .forEach {
                assertFalse(it, WebhookConfiguration.isValid(endpoint, it))
                assertFalse(it, WebhookConfiguration.isValidSecret(it))
            }
    }

    @Test fun buildConfigDoesNotContainWebhookSecret() {
        val fields = BuildConfig::class.java.declaredFields.map { it.name }
        assertFalse("BuildConfig must not embed WEBHOOK_SECRET", fields.contains("WEBHOOK_SECRET"))
        assertTrue("BuildConfig must contain WEBHOOK_URL", fields.contains("WEBHOOK_URL"))
        assertTrue("BuildConfig.WEBHOOK_URL must be a valid endpoint", WebhookConfiguration.isValidEndpoint(BuildConfig.WEBHOOK_URL))
    }
}
