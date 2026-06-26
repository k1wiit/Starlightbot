package com.k1wit.starlightbot.util

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class MojangProfile(val name: String, val uuid: String)

object MojangAPI {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Returns MojangProfile if name exists, null if not found, throws on error.
     */
    fun getProfile(username: String): MojangProfile? {
        val request = Request.Builder()
            .url("https://api.mojang.com/users/profiles/minecraft/$username")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            return when (response.code) {
                200 -> {
                    val body = response.body?.string() ?: return null
                    val json = JSONObject(body)
                    val name = json.getString("name")
                    val rawUuid = json.getString("id")
                    // Format UUID: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
                    val formattedUuid = buildString {
                        append(rawUuid.substring(0, 8))
                        append("-")
                        append(rawUuid.substring(8, 12))
                        append("-")
                        append(rawUuid.substring(12, 16))
                        append("-")
                        append(rawUuid.substring(16, 20))
                        append("-")
                        append(rawUuid.substring(20))
                    }
                    MojangProfile(name, formattedUuid)
                }
                204, 404 -> null
                else -> throw Exception("Mojang API returned HTTP ${response.code}")
            }
        }
    }
}