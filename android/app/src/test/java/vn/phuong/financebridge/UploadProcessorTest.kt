package vn.phuong.financebridge

import java.io.IOException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class UploadProcessorTest {
    // An in-memory port, not an Android SQLite/WorkManager instrumentation test.
    private class StoredOutbox : UploadOutbox {
        private val text = "GD TK ACCOUNT-TEST -100000 VND"
        var event = PendingTransaction(EventIdentity.createEventId("VCB", 1L, text), "VCB", "com.VCB", "VCB", text, 1L, "PENDING")
        var attempts = 0
        var error: String? = null
        override fun get(eventId: String) = event.takeIf { it.eventId == eventId }
        override fun markAttempt(eventId: String) { attempts++; error = null }
        override fun markRetryable(eventId: String, errorCode: String) { event = event.copy(state = "PENDING"); error = errorCode }
        override fun markBlocked(eventId: String, errorCode: String) { event = event.copy(state = "BLOCKED"); error = errorCode }
        override fun markTerminal(eventId: String, resultStatus: String) { event = event.copy(state = resultStatus, title = "", text = ""); error = null }
        fun retryBlocked() { check(event.state == "BLOCKED"); event = event.copy(state = "PENDING"); error = null }
    }

    @Test fun networkFailureRetainsPayloadAndRetryClearsItOnlyAfterSuccess() {
        val store = StoredOutbox()
        val before = store.event
        val offline = UploadProcessor(store, { throw IOException("synthetic offline") }, { 200 })
        assertEquals(UploadOutcome.RETRY, offline.upload(before.eventId))
        assertEquals(before, store.event)
        assertEquals("NETWORK_UNAVAILABLE", store.error)
        // A new processor recovers the original payload from the same store.
        val online = UploadProcessor(store, { sent -> assertEquals(before, sent); WebhookResponse(200, "SAVED_TRANSACTION", null) }, { 200 })
        assertEquals(UploadOutcome.SUCCESS, online.upload(before.eventId))
        assertEquals("SAVED_TRANSACTION", store.event.state)
        assertEquals("", store.event.text)
        assertEquals("", store.event.title)
        assertEquals(before.postedAt, store.event.postedAt)
        assertEquals(before.eventId, store.event.eventId)
        assertEquals(2, store.attempts)
    }

    @Test fun fixedConfigurationFailuresRetainPayloadAndRequireExplicitRecovery() {
        val failures = listOf(
            WebhookResponse(0, null, "CLIENT_CONFIGURATION_ERROR"),
            WebhookResponse(401, null, "UNAUTHORIZED"),
            WebhookResponse(503, null, "SERVER_CONFIGURATION_ERROR"),
            WebhookResponse(503, null, "SHEET_SCHEMA_MISMATCH"),
            WebhookResponse(503, null, "SHEETS_PERMISSION_DENIED"),
            WebhookResponse(503, null, "SHEET_NOT_FOUND"),
        )
        failures.forEach { response ->
            val store = StoredOutbox()
            val before = store.event
            var posts = 0
            val blocked = UploadProcessor(store, { posts++; response }, { 200 })
            assertEquals(UploadOutcome.BLOCKED, blocked.upload(before.eventId))
            assertEquals(before.text, store.event.text)
            assertEquals(before.title, store.event.title)
            assertEquals("BLOCKED", store.event.state)
            assertEquals(UploadOutcome.BLOCKED, blocked.upload(before.eventId))
            assertEquals(1, posts)
            store.retryBlocked()
            val recovered = UploadProcessor(store, { WebhookResponse(200, "DUPLICATE_IGNORED", null) }, { 200 })
            assertEquals(UploadOutcome.SUCCESS, recovered.upload(before.eventId))
            assertEquals("DUPLICATE_IGNORED", store.event.state)
            assertEquals("", store.event.text)
        }
    }

    @Test fun terminalReplayNeverSendsAgain() {
        val store = StoredOutbox()
        store.markTerminal(store.event.eventId, "SAVED_TRANSACTION")
        assertEquals(UploadOutcome.SUCCESS, UploadProcessor(store, { error("must not post") }, { 0 }).upload(store.event.eventId))
        assertEquals(0, store.attempts)
    }

    @Test fun changedPayloadBlocksBeforeSending() {
        val store = StoredOutbox()
        store.event = store.event.copy(text = "changed")
        assertEquals(UploadOutcome.BLOCKED, UploadProcessor(store, { error("must not post") }, { 0 }).upload(store.event.eventId))
        assertEquals("EVENT_ID_MISMATCH", store.error)
        assertEquals("changed", store.event.text)
    }

    @Test fun oversizedPayloadIsRetained() {
        val store = StoredOutbox()
        assertEquals(UploadOutcome.BLOCKED, UploadProcessor(store, { error("must not post") }, { 16385 }).upload(store.event.eventId))
        assertEquals("PAYLOAD_TOO_LARGE", store.error)
        assertTrue(store.event.text.isNotEmpty())
    }

    @Test fun transientHttpErrorsRetryWithoutClearing() {
        listOf(408, 429, 500, 503).forEach { code ->
            val store = StoredOutbox()
            val before = store.event
            assertEquals(UploadOutcome.RETRY, UploadProcessor(store, { WebhookResponse(code, null, "UPSTREAM_UNAVAILABLE") }, { 200 }).upload(before.eventId))
            assertEquals(before, store.event)
        }
    }

    @Test fun malformedSuccessNeverDeletesPayload() {
        val store = StoredOutbox()
        assertEquals(UploadOutcome.BLOCKED, UploadProcessor(store, { WebhookResponse(200, "UNKNOWN", null) }, { 200 }).upload(store.event.eventId))
        assertTrue(store.event.text.isNotEmpty())
    }

    @Test fun cancellationRetainsPendingPayloadAndPropagates() {
        val store = StoredOutbox()
        val before = store.event
        try {
            UploadProcessor(store, { throw CancellationException() }, { 200 }).upload(before.eventId)
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { assertEquals(before, store.event) }
    }
}
