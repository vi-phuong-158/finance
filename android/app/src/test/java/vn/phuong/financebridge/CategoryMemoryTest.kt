package vn.phuong.financebridge

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

// Synthetic merchants/names only.
class CategoryMemoryTest {
    private val party = "COUNTERPARTY:NGUYEN VAN A"
    private fun rule(level: String, count: Int = 3, total: Int = 3, key: String = party, type: String = "CHI", category: String = "Gia đình",
        recent: List<String> = listOf(category), pinned: Boolean = false) =
        MemoryRule(key, key.substringBefore(':'), key.substringAfter(':'), type, category, count, total, count.toDouble() / total, level, recent,
            "2026-03-02T00:00:00.000Z", pinned)
    private fun index(vararg rules: MemoryRule, writes: Boolean = true) = CategoryMemoryIndex("2026-03-02T00:00:00.000Z", writes, rules.toList(),
        listOf(CategoryChoice("Gia đình", "CHI"), CategoryChoice("Ăn uống", "CHI"), CategoryChoice("Lương", "THU"), CategoryChoice("Chuyển nội bộ", "BOTH")))
    private fun tempStore(): Pair<CategoryMemoryStore, File> {
        val file = File(Files.createTempDirectory("memory").toFile(), "category_memory_v1.tsv")
        return CategoryMemoryStore(file) to file
    }

    @Test fun normalizationMatchesSharedVectorsWithBackend() {
        val text = requireNotNull(javaClass.classLoader!!.getResourceAsStream("category-memory-vectors.tsv")).bufferedReader(Charsets.UTF_8).readText()
        val lines = text.split('\n').filter { it.isNotEmpty() && !it.startsWith("#") }
        assertTrue(lines.size >= 15)
        val escape = Regex("\\\\u([0-9a-fA-F]{4})")
        lines.forEach { line ->
            val (input, expected) = line.split('\t').map { escape.replace(it) { m -> m.groupValues[1].toInt(16).toChar().toString() } }
            assertEquals(input, expected, CategoryMemory.normalize(input))
        }
        assertEquals("DESCRIPTION:AN SANG", CategoryMemory.descriptionKey("Ăn sáng"))
        assertNull(CategoryMemory.descriptionKey("12345 ***"))
    }

    @Test fun suggestionShownWithoutPreselectForSingleOrAmbiguousHistory() {
        val hint = CategoryMemory.suggest(index(rule("HINT", 1, 1)), party, "CHI")!!
        assertFalse(hint.preselect); assertEquals("Gợi ý: Gia đình", hint.headline); assertEquals("Đã dùng 1 lần", hint.detail)
        val tie = CategoryMemory.suggest(index(rule("AMBIGUOUS", 3, 6, recent = listOf("Gia đình", "Ăn uống"))), party, "CHI")!!
        assertFalse(tie.preselect); assertEquals("Gợi ý gần đây: Gia đình / Ăn uống", tie.headline)
        assertNull(CategoryMemory.suggest(index(rule("STRONG")), party, "THU"))
        assertNull(CategoryMemory.suggest(index(rule("STRONG")), "COUNTERPARTY:OTHER", "CHI"))
        assertFalse(listOf(hint.headline, hint.detail, tie.headline).any { it.contains("AI", ignoreCase = false) })
    }

    @Test fun categoryPreselectOnlyReplacesAutomaticOrUncategorized() {
        val strong = CategoryMemory.suggest(index(rule("STRONG", 6, 7)), party, "CHI")!!
        assertTrue(strong.preselect); assertEquals("Gợi ý từ lịch sử: Gia đình", strong.headline); assertEquals("Đã dùng 6/7 lần gần đây", strong.detail)
        assertEquals("Gia đình", CategoryMemory.preselectForEdit(strong, "BANK_NOTIFICATION", "0", "Siêu thị"))
        assertEquals("Gia đình", CategoryMemory.preselectForEdit(strong, "MANUAL", "0", "Chưa phân loại"))
        assertEquals("Ăn uống", CategoryMemory.preselectForEdit(strong, "MANUAL", "0", "Ăn uống"))
        assertEquals("Ăn uống", CategoryMemory.preselectForEdit(strong, "BANK_NOTIFICATION", "11111111-1111-4111-8111-111111111111", "Ăn uống"))
        val hint = CategoryMemory.suggest(index(rule("HINT", 1, 1)), party, "CHI")
        assertEquals("Chưa phân loại", CategoryMemory.preselectForEdit(hint, "BANK_NOTIFICATION", "0", "Chưa phân loại"))
        val tier3 = CategoryMemory.suggest(index(rule("PRESELECT", 2, 2, key = "DESCRIPTION:AN SANG")), "DESCRIPTION:AN SANG", "CHI")!!
        assertTrue(tier3.detail.endsWith("khớp theo nội dung"))
    }

    @Test fun userOverrideIsNeverReplacedBySuggestion() {
        val strong = CategoryMemory.suggest(index(rule("STRONG", 6, 7)), party, "CHI")
        assertEquals("Gia đình", CategoryMemory.preselectForRecord(strong, userPicked = false, current = "Ăn uống"))
        assertEquals("Ăn uống", CategoryMemory.preselectForRecord(strong, userPicked = true, current = "Ăn uống"))
        assertEquals("Ăn uống", CategoryMemory.preselectForRecord(null, userPicked = false, current = "Ăn uống"))
    }

    @Test fun localCacheSurvivesAppRestartAndOfflineRefresh() {
        val (store, file) = tempStore()
        val saved = index(rule("STRONG", 6, 7, pinned = true), rule("HINT", 1, 1, key = "DESCRIPTION:AN SANG", category = "Ăn uống"))
        assertEquals(saved, store.apply(Result.success(saved)).index)
        // "Restart": a new store instance over the same private file.
        val restarted = CategoryMemoryStore(file)
        assertEquals(saved, restarted.load())
        val offline = restarted.apply(Result.failure(IllegalStateException("offline")))
        assertTrue(offline.offline); assertFalse(offline.disabled)
        assertEquals("Gia đình", CategoryMemory.suggest(offline.index, party, "CHI")!!.category)
    }

    @Test fun cacheRefreshReplacesRulesAndCorruptFilesFailClosed() {
        val (store, file) = tempStore()
        store.apply(Result.success(index(rule("STRONG"))))
        val newer = index(rule("PRESELECT", 2, 3, category = "Ăn uống", recent = listOf("Ăn uống", "Gia đình")))
        store.apply(Result.success(newer))
        assertEquals("Ăn uống", CategoryMemory.suggest(store.load(), party, "CHI")!!.category)
        file.writeText(file.readText().replace("\tCHI\tĂn uống\t", "\tCHI\tĂn\nuống\t"))
        assertNull(store.load())
        file.writeText("garbage")
        assertNull(store.load())
        assertThrows(IllegalArgumentException::class.java) { CategoryMemoryStore.encode(index(rule("STRONG", category = "Bad\tTab"))) }
        assertThrows(IllegalArgumentException::class.java) { CategoryMemoryStore.encode(index(rule("STRONG", key = "COUNTERPARTY:lower"))) }
    }

    @Test fun featureFlagDisabledClearsCacheAndHidesSuggestions() {
        val (store, file) = tempStore()
        store.apply(Result.success(index(rule("STRONG"))))
        val state = store.apply(Result.success(null))
        assertTrue(state.disabled); assertNull(state.index); assertFalse(file.exists())
        assertNull(CategoryMemory.suggest(store.load(), party, "CHI"))
    }

    @Test fun memoryCommandsValidateAndRetryThroughTheExistingQueueStates() {
        val id = "11111111-1111-4111-8111-111111111111"
        MemoryCommand(id, "SET", party, "CHI", "Gia đình").validate(index())
        MemoryCommand(id, "DELETE", party, "CHI").validate(index())
        MemoryCommand(id, "RESET_ALL").validate(index())
        listOf(MemoryCommand(id, "SET", party, "CHI", "Lương"), MemoryCommand(id, "SET", party, "CHI", "Chưa phân loại"),
            MemoryCommand(id, "SET", "ACCOUNT:VCB:0123456789", "CHI", "Gia đình"), MemoryCommand(id, "DELETE", party, "BOTH"),
            MemoryCommand(id, "RESET_ALL", party), MemoryCommand("bad", "RESET_ALL"), MemoryCommand(id, "DROP"))
            .forEach { assertTrue(it.toString(), runCatching { it.validate(index()) }.isFailure) }
        assertTrue(runCatching { MemoryCommand(id, "RESET_ALL").validate(index(writes = false)) }.isFailure)
        assertEquals("SYNCED", manualSyncState(200, "SAVED_CATEGORY_MEMORY"))
        assertEquals("SYNCED", manualSyncState(200, "DUPLICATE_IGNORED"))
        assertEquals("PENDING", manualSyncState(503, "CATEGORY_MEMORY_UNAVAILABLE"))
        assertEquals("BLOCKED", manualSyncState(503, "CATEGORY_MEMORY_WRITES_DISABLED"))
        assertEquals("BLOCKED", manualSyncState(503, "CATEGORY_MEMORY_MIGRATION_REQUIRED"))
        assertEquals("BLOCKED", manualSyncState(409, "IDEMPOTENCY_CONFLICT"))
    }
}
