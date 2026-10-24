package vn.phuong.financebridge

import org.junit.Assert.*
import org.junit.Test

class WebhookConfigurationTest {
    private val endpoint = "https://finance.example.invalid/api/transaction"
    private val syntheticSecret = "a".repeat(64)

    @Test fun acceptsPrivateHttpsConfiguration() {
        assertTrue(WebhookConfiguration.isValid(endpoint, syntheticSecret))
    }

    @Test fun rejectsMissingMalformedAndInsecureUrls() {
        listOf("", "not a URL", endpoint.replace("https", "http"), "https://user:pass@finance.example.invalid/api/transaction", "$endpoint?secret=x", "$endpoint#fragment")
            .forEach { assertFalse(it, WebhookConfiguration.isValid(it, syntheticSecret)) }
    }

    @Test fun rejectsBlankDefaultAndPlaceholderSecrets() {
        listOf("", "short", "default".repeat(8), "placeholder".repeat(5), "changeme".repeat(6), "replace-with-" + "a".repeat(40))
            .forEach { assertFalse(WebhookConfiguration.isValid(endpoint, it)) }
    }
}
