package vn.phuong.financebridge

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WebhookClientTest {
    @Test fun retriesTemporaryFailures() {
        listOf(408, 425, 429, 500, 502, 503).forEach {
            assertTrue(WebhookClient.isRetryable(WebhookResponse(it, null, "UPSTREAM_UNAVAILABLE")))
        }
    }

    @Test fun configurationFailuresRequireManualRecovery() {
        listOf(
            WebhookResponse(0, null, "CLIENT_CONFIGURATION_ERROR"),
            WebhookResponse(401, null, "UNAUTHORIZED"),
            WebhookResponse(503, null, "SERVER_CONFIGURATION_ERROR"),
            WebhookResponse(503, null, "SHEET_SCHEMA_MISMATCH"),
        ).forEach { assertFalse(WebhookClient.isRetryable(it)) }
    }

    @Test fun onlyRecognizedTerminalStatusesDeleteRawPayload() {
        listOf("SAVED_TRANSACTION", "SAVED_RAW_CAPTURE", "DUPLICATE_IGNORED", "DROPPED_SENSITIVE", "DROPPED_NON_TRANSACTION")
            .forEach { assertTrue(WebhookClient.isTerminalSuccess(WebhookResponse(200, it, null))) }
        listOf(null, "UNKNOWN", "VALIDATED_TRANSACTION").forEach {
            assertFalse(WebhookClient.isTerminalSuccess(WebhookResponse(200, it, null)))
        }
        assertFalse(WebhookClient.isTerminalSuccess(WebhookResponse(503, "SAVED_TRANSACTION", null)))
    }

    @Test fun missingOrInvalidSecretFailsClosedWithoutNetworkRequest() {
        val payloadJson = """{"test":"data"}"""
        val invalidSecrets = listOf(null, "", "short", "default".repeat(8), "a".repeat(31))
        invalidSecrets.forEach { secret ->
            val response = WebhookClient.postJson(payloadJson, secret = secret)
            assertEquals("CLIENT_CONFIGURATION_ERROR", response.errorCode)
            assertEquals(0, response.httpCode)
        }
    }
}
