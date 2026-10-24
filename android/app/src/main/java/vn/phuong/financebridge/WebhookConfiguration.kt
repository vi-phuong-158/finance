package vn.phuong.financebridge

import java.net.URI

object WebhookConfiguration {
    private val secretPattern = Regex("[A-Za-z0-9_-]{32,128}")
    private val placeholder = Regex("replace|placeholder|change-?me|default|your[-_]|example|test[-_]|sample|mat-khau", RegexOption.IGNORE_CASE)

    fun isValidEndpoint(endpoint: String): Boolean {
        val uri = runCatching { URI(endpoint) }.getOrNull() ?: return false
        return uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.rawUserInfo == null
            && uri.rawQuery == null && uri.rawFragment == null && uri.path == "/api/transaction"
    }

    fun isValidSecret(secret: String): Boolean {
        return secretPattern.matches(secret) && !placeholder.containsMatchIn(secret)
    }

    fun isValid(endpoint: String, secret: String): Boolean {
        return isValidEndpoint(endpoint) && isValidSecret(secret)
    }
}
