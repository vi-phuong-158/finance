package vn.phuong.financebridge

import java.io.File
import java.text.Normalizer
import java.util.UUID

// Offline cache + suggestion rules for "Gợi ý từ lịch sử". Server derives the rules
// (vercel/lib/category-memory.ts); this side only looks them up. See docs/category-memory/DESIGN.md.
data class MemoryRule(val key: String, val identityType: String, val label: String, val type: String, val category: String,
    val count: Int, val total: Int, val confidence: Double, val level: String, val recent: List<String>, val lastUsedAt: String, val pinned: Boolean)

data class CategoryMemoryIndex(val generatedAt: String, val writesEnabled: Boolean, val rules: List<MemoryRule>, val categories: List<CategoryChoice>) {
    private val byKey = rules.associateBy { "${it.key}|${it.type}" }
    fun find(key: String?, type: String): MemoryRule? = key?.let { byKey["$it|$type"] }
}

data class CategorySuggestion(val rule: MemoryRule, val category: String, val preselect: Boolean, val headline: String, val detail: String)

data class MemoryState(val index: CategoryMemoryIndex?, val disabled: Boolean, val offline: Boolean)

data class MemoryCommand(val requestId: String, val action: String, val identityKey: String = "", val type: String = "", val category: String = "") {
    fun validate(index: CategoryMemoryIndex) {
        require(UUID.fromString(requestId).version() == 4)
        require(index.writesEnabled) { "Chỉnh sửa ghi nhớ chưa bật trên máy chủ." }
        when (action) {
            "RESET_ALL" -> require(identityKey.isEmpty() && type.isEmpty() && category.isEmpty())
            "DELETE" -> require(CategoryMemory.validKey(identityKey) && type in setOf("THU", "CHI") && category.isEmpty())
            "SET" -> require(CategoryMemory.validKey(identityKey) && type in setOf("THU", "CHI") && category != CategoryMemory.UNCATEGORIZED
                && index.categories.any { it.name == category && (it.type == type || it.type == "BOTH") }) { "Chọn danh mục có sẵn phù hợp Thu/Chi." }
            else -> throw IllegalArgumentException("INVALID_ACTION")
        }
    }
}

object CategoryMemory {
    const val UNCATEGORIZED = "Chưa phân loại"
    // Twin of vercel/lib/category-memory.ts TECHNICAL_TOKENS; shared/category-memory-vectors.tsv covers both.
    private val technical = setOf("MBVCB", "IBFT", "REF", "TRACE", "FT", "QR", "POS", "ECOM", "NAPAS", "CT", "CK", "GD", "TK", "ND", "VND", "DEN")
    // Twin of vercel/lib/category-memory.ts IDENTITY_KEY. ACCOUNT is a keyed HMAC of the account only (no bank).
    private val key = Regex("^(?:ACCOUNT:[a-f0-9]{32}|(?:COUNTERPARTY|DESCRIPTION):[A-Z0-9]+(?: [A-Z0-9]+){0,7})$")
    private val levels = setOf("HINT", "AMBIGUOUS", "PRESELECT", "STRONG")

    fun normalize(text: String): String {
        val tokens = Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("[\u0300-\u036f]"), "").replace(Regex("[đĐ]"), "D")
            .replace(Regex("[^A-Za-z0-9]+"), " ").uppercase().split(" ")
            .filter { it.isNotEmpty() && it.none(Char::isDigit) && it !in technical }
        return tokens.joinToString(" ").takeIf { it.length >= 2 }.orEmpty()
    }

    // Same rule as the server's memoryLabel: never truncate, longer notes are not an identity.
    fun descriptionKey(note: String): String? = normalize(note).takeIf { it.isNotEmpty() && it.split(" ").size <= 8 }?.let { "DESCRIPTION:$it" }

    fun validKey(value: String?): Boolean = value != null && key.matches(value)

    private fun safeText(value: String, max: Int) = value.isNotBlank() && value.length <= max && value.none { it == '\t' || it == '\n' || it == '\r' || it == '|' }

    fun validRule(r: MemoryRule): Boolean = validKey(r.key) && r.key.startsWith("${r.identityType}:") && r.identityType in setOf("ACCOUNT", "COUNTERPARTY", "DESCRIPTION")
        && safeText(r.label, 120) && r.type in setOf("THU", "CHI") && safeText(r.category, 80) && r.category != UNCATEGORIZED
        && r.total in 1..10 && r.count in 1..r.total && r.confidence in 0.0..1.0 && r.level in levels
        && r.recent.isNotEmpty() && r.recent.size <= 3 && r.recent.all { safeText(it, 80) } && r.category in r.recent && safeText(r.lastUsedAt, 40)

    fun suggest(index: CategoryMemoryIndex?, key: String?, type: String): CategorySuggestion? {
        val rule = index?.find(key, type) ?: return null
        val ratio = if (rule.total == 1) "Đã dùng 1 lần" else "Đã dùng ${rule.count}/${rule.total} lần gần đây"
        val basis = (if (rule.identityType == "DESCRIPTION") " · khớp theo nội dung" else "") + (if (rule.pinned) " · bạn đã chỉnh" else "")
        return when (rule.level) {
            "AMBIGUOUS" -> CategorySuggestion(rule, rule.category, false, "Gợi ý gần đây: ${rule.recent.take(2).joinToString(" / ")}", "Chưa đủ chắc để chọn sẵn$basis")
            "HINT" -> CategorySuggestion(rule, rule.category, false, "Gợi ý: ${rule.category}", "$ratio$basis")
            else -> CategorySuggestion(rule, rule.category, true, "Gợi ý từ lịch sử: ${rule.category}", "$ratio$basis")
        }
    }

    // A keyword category the bank notification got automatically may yield to the user's own history;
    // a category the user already chose (manual entry or an earlier edit) is never replaced.
    fun preselectForEdit(s: CategorySuggestion?, source: String, revision: String, current: String): String =
        if (s != null && s.preselect && (current == UNCATEGORIZED || (source == "BANK_NOTIFICATION" && revision == "0"))) s.category else current

    fun preselectForRecord(s: CategorySuggestion?, userPicked: Boolean, current: String): String =
        if (s != null && s.preselect && !userPicked) s.category else current
}

class CategoryMemoryStore(private val file: File) {
    fun load(): CategoryMemoryIndex? = if (!file.exists()) null else runCatching { decode(file.readText(Charsets.UTF_8)) }.getOrNull()

    fun save(index: CategoryMemoryIndex) {
        val text = encode(index)
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(text, Charsets.UTF_8)
        // Rename replaces atomically on Android; the fallback keeps desktop JVM tests portable.
        if (!temp.renameTo(file)) { file.delete(); check(temp.renameTo(file)) }
    }

    fun clear() { file.delete() }

    // Network failure keeps the last good cache (offline-first); a disabled server clears it.
    fun apply(result: Result<CategoryMemoryIndex?>): MemoryState = result.fold(
        onSuccess = { index -> if (index == null) { clear(); MemoryState(null, disabled = true, offline = false) } else { save(index); MemoryState(index, false, false) } },
        onFailure = { MemoryState(load(), disabled = false, offline = true) },
    )

    companion object {
        private const val HEADER = "category-memory-v1"
        fun encode(index: CategoryMemoryIndex): String = buildString {
            require(index.rules.all(CategoryMemory::validRule) && index.generatedAt.none { it == '\t' || it == '\n' })
            append(HEADER).append('\t').append(index.generatedAt).append('\t').append(if (index.writesEnabled) "1" else "0").append('\n')
            index.categories.forEach { append("C\t").append(it.name).append('\t').append(it.type).append('\n') }
            index.rules.forEach { r ->
                append(listOf("R", r.key, r.identityType, r.label, r.type, r.category, r.count, r.total, r.confidence, r.level,
                    r.recent.joinToString("|"), r.lastUsedAt, if (r.pinned) "1" else "0").joinToString("\t")).append('\n')
            }
        }

        fun decode(text: String): CategoryMemoryIndex {
            val lines = text.split('\n').filter { it.isNotEmpty() }
            val header = lines.firstOrNull()?.split('\t') ?: error("EMPTY")
            require(header.size == 3 && header[0] == HEADER && header[2] in setOf("0", "1"))
            val categories = mutableListOf<CategoryChoice>()
            val rules = mutableListOf<MemoryRule>()
            for (line in lines.drop(1)) {
                val f = line.split('\t')
                when {
                    f[0] == "C" && f.size == 3 && f[2] in setOf("THU", "CHI", "BOTH") && f[1].isNotBlank() -> categories += CategoryChoice(f[1], f[2])
                    f[0] == "R" && f.size == 13 && f[12] in setOf("0", "1") -> rules += MemoryRule(f[1], f[2], f[3], f[4], f[5], f[6].toInt(), f[7].toInt(),
                        f[8].toDouble(), f[9], f[10].split('|'), f[11], f[12] == "1").also { require(CategoryMemory.validRule(it)) }
                    else -> error("CORRUPT_MEMORY_CACHE")
                }
            }
            return CategoryMemoryIndex(header[1], header[2] == "1", rules, categories)
        }
    }
}
