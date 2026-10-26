package com.battlesbudz.dinnerbot.feature.`import`

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The import fetch (Phase RI-2, scope B2, plan ruling 10): when the user
 * shares or pastes a link, the phone fetches that page ONCE, at her
 * action, to read the structured recipe data inside it. This is the
 * epic's only added network use and it lives in this package and
 * nowhere else. There is no Dinner Bot server and nothing is uploaded;
 * the request carries nothing about the household. Any failure at all
 * returns null and the import falls back to paste text, which needs no
 * connection.
 */
object RecipePageFetcher {

    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 8_000
    private const val MAX_CHARS = 1_500_000

    /** True when [text] reads as a single web address the user can import from. */
    fun looksLikeUrl(text: String): Boolean {
        val trimmed = text.trim()
        return (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)) &&
            !trimmed.contains(' ') && !trimmed.contains('\n')
    }

    /** The page's HTML, or null on any failure (no connection, refusal, timeout, bad URL). */
    suspend fun fetchHtml(urlText: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(urlText.trim())
            if (url.protocol != "http" && url.protocol != "https") return@runCatching null
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("Accept", "text/html")
                connection.setRequestProperty(
                    "User-Agent", "DinnerBot/0.5.1 (local-first meal planner)",
                )
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
                val charset = connection.contentType
                    ?.substringAfter("charset=", "")
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: "UTF-8"
                connection.inputStream.bufferedReader(
                    runCatching { charset(charset) }.getOrDefault(Charsets.UTF_8),
                ).use { reader ->
                    val out = StringBuilder()
                    val buffer = CharArray(8192)
                    while (out.length < MAX_CHARS) {
                        val read = reader.read(buffer)
                        if (read < 0) break
                        out.append(buffer, 0, read)
                    }
                    out.toString()
                }
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }
}
