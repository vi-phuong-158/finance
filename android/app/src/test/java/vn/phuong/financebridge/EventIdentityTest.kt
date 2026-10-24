package vn.phuong.financebridge

import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class EventIdentityTest {
    @Test fun replayHasSameId() {
        assertEquals(EventIdentity.createEventId("VCB", 1792815300000L, "GD TK +100,000 VND"),
            EventIdentity.createEventId("VCB", 1792815300000L, "GD TK +100,000 VND"))
    }

    @Test fun differentPostTimeHasDifferentId() {
        assertNotEquals(EventIdentity.createEventId("VCB", 1792815300000L, "GD TK +100,000 VND"),
            EventIdentity.createEventId("VCB", 1792815300001L, "GD TK +100,000 VND"))
    }

    @Test fun matchesSharedBackendVectors() {
        val vectors = requireNotNull(javaClass.classLoader!!.getResourceAsStream("event-id-vectors.tsv"))
            .bufferedReader(Charsets.UTF_8).use { it.readLines() }
        assertTrue(vectors.size >= 5)
        vectors.forEach { row ->
            val (bank, timestamp, encodedText, expected) = row.split('\t')
            val text = String(Base64.getDecoder().decode(encodedText), Charsets.UTF_8)
            assertEquals(expected, EventIdentity.createEventId(bank, timestamp.toLong(), text))
        }
    }
}
