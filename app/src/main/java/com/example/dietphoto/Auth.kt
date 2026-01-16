package com.example.dietphoto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object AuthStore {
    @Volatile
    var accessToken: String? = null
}

private val FORM_URL_ENCODED = "application/x-www-form-urlencoded".toMediaType()

suspend fun loginToServer(username: String, password: String): String = withContext(Dispatchers.IO) {
    val formBody = buildString {
        append("username=")
        append(URLEncoder.encode(username, StandardCharsets.UTF_8.name()))
        append("&password=")
        append(URLEncoder.encode(password, StandardCharsets.UTF_8.name()))
    }.toRequestBody(FORM_URL_ENCODED)

    val req = Request.Builder()
        .url("${BASE_URL}token")
        .post(formBody)
        .build()

    httpClient.newCall(req).execute().use { resp ->
        val raw = resp.body?.string().orEmpty()
        if (!resp.isSuccessful) {
            val detail = runCatching { JSONObject(raw).optString("detail") }.getOrNull()
            val msg = if (!detail.isNullOrBlank()) detail else "Login failed: HTTP ${resp.code}"
            error(msg)
        }
        val json = JSONObject(raw)
        json.getString("access_token")
    }
}
