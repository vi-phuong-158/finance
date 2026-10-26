package vn.phuong.financebridge.ui.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import vn.phuong.financebridge.CategoryChoice
import vn.phuong.financebridge.CategoryMemoryIndex
import vn.phuong.financebridge.CategoryMemoryStore
import vn.phuong.financebridge.MemoryRule
import vn.phuong.financebridge.MemoryState
import java.io.File

class CategoryMemoryRepository(context: Context) {
    private val api = FinanceApi(context)
    private val store = CategoryMemoryStore(File(context.applicationContext.filesDir, "category_memory_v1.tsv"))

    suspend fun cached(): CategoryMemoryIndex? = withContext(Dispatchers.IO) { store.load() }

    // One bounded index read per screen entry; lookups afterwards are local and work offline.
    suspend fun refresh(): MemoryState {
        val result = api.read("category-memory") { parseMemoryIndex(it) }
        return withContext(Dispatchers.IO) { store.apply(result) }
    }
}

fun parseMemoryIndex(json: JSONObject): CategoryMemoryIndex? {
    require(json.getInt("version") == 1)
    if (!json.getBoolean("enabled")) return null
    val rules = json.getJSONArray("rules")
    val categories = json.getJSONArray("categories")
    return CategoryMemoryIndex(json.getString("generatedAt"), json.getBoolean("writesEnabled"),
        (0 until rules.length()).map { i ->
            val r = rules.getJSONObject(i)
            val recent = r.getJSONArray("recent")
            MemoryRule(r.getString("key"), r.getString("identityType"), r.getString("label"), r.getString("type"), r.getString("category"),
                r.getInt("count"), r.getInt("total"), r.getDouble("confidence"), r.getString("level"),
                (0 until recent.length()).map { recent.getString(it) }, r.getString("lastUsedAt"), r.getBoolean("pinned"))
        },
        (0 until categories.length()).map { i -> categories.getJSONObject(i).let { CategoryChoice(it.getString("name"), it.getString("type")) } })
        // A malformed index is rejected whole; the previous cache stays in use.
        .also { CategoryMemoryStore.encode(it) }
}
