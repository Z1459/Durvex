package openpass.security.data.autofill

import java.net.URI

object DomainParser {
    fun getBaseDomain(url: String?): String? {
        if (url.isNullOrEmpty()) return null
        return try {
            val uri = URI(url)
            var domain = uri.host ?: return url
            if (domain.startsWith("www.")) {
                domain = domain.substring(4)
            }
            val parts = domain.split('.')
            if (parts.size > 2) {
                // Heuristic for common patterns like .co.uk
                if (parts[parts.size - 2].length <= 3 && parts[parts.size - 1].length <= 3) {
                    parts.takeLast(3).joinToString(".")
                } else {
                    parts.takeLast(2).joinToString(".")
                }
            } else {
                domain
            }
        } catch (e: Exception) {
            // Fallback for non-standard URIs
            val parts = url.split('.').takeLast(2)
            if (parts.size == 2) parts.joinToString(".") else url
        }
    }
}
