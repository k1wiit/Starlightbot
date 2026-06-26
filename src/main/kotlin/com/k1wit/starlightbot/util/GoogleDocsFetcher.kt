package com.k1wit.starlightbot.util

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object GoogleDocsFetcher {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val docIdPattern = Pattern.compile("/document/d/([a-zA-Z0-9_-]+)")

    fun extractDocId(url: String): String? {
        val matcher = docIdPattern.matcher(url)
        return if (matcher.find()) matcher.group(1) else null
    }

    /**
     * Fetches a Google Doc as plain text.
     * The document must be publicly accessible ("Anyone with the link can view").
     */
    fun fetchAsText(docId: String): String {
        val exportUrl = "https://docs.google.com/document/d/$docId/export?format=txt"
        val request = Request.Builder()
            .url(exportUrl)
            .addHeader("User-Agent", "Mozilla/5.0 StarlightBot/1.0")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: Could not fetch document")
            }
            return response.body?.string()?.trim() ?: throw Exception("Empty response body")
        }
    }

    /**
     * Splits text into chunks of at most maxLength characters, breaking on newlines.
     */
    fun splitIntoChunks(text: String, maxLength: Int = 1900): List<String> {
        if (text.length <= maxLength) return listOf(text)

        val chunks = mutableListOf<String>()
        val lines = text.lines()
        val current = StringBuilder()

        for (line in lines) {
            val addition = if (current.isEmpty()) line else "\n$line"
            if (current.length + addition.length > maxLength) {
                if (current.isNotEmpty()) {
                    chunks.add(current.toString())
                    current.clear()
                }
                // If a single line is longer than maxLength, force split
                if (line.length > maxLength) {
                    var remaining = line
                    while (remaining.length > maxLength) {
                        chunks.add(remaining.substring(0, maxLength))
                        remaining = remaining.substring(maxLength)
                    }
                    if (remaining.isNotEmpty()) current.append(remaining)
                } else {
                    current.append(line)
                }
            } else {
                current.append(addition)
            }
        }

        if (current.isNotEmpty()) chunks.add(current.toString())
        return chunks
    }
}