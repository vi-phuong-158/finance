package vn.phuong.financebridge

import java.security.MessageDigest
import java.text.Normalizer

object EventIdentity {
    private val whitespace = Regex("[\\u0009-\\u000d\\u0020\\u00a0\\u1680\\u2000-\\u200a\\u2028\\u2029\\u202f\\u205f\\u3000\\ufeff]+")

    fun normalizeText(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFC).replace(whitespace, " ").trim(' ')

    fun createEventId(bank: String, postedAt: Long, text: String): String {
        val canonical = "$bank:$postedAt:${normalizeText(text)}"
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
