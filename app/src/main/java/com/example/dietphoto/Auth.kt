package com.example.dietphoto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import android.content.Context
import android.content.SharedPreferences

object AuthStore {
    @Volatile
    var accessToken: String? = null
    @Volatile
    var userId: Int? = null

    private const val PREFS_NAME = "auth_prefs"
    private const val KEY_TOKEN = "access_token"
    private const val KEY_USER_ID = "user_id"

    fun loadSavedToken(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val token = prefs.getString(KEY_TOKEN, null)
        accessToken = token
        return token
    }

    fun loadSavedUserId(context: Context): Int? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val id = prefs.getInt(KEY_USER_ID, -1).takeIf { it >= 0 }
        userId = id
        return id
    }

    fun persistToken(context: Context, token: String) {
        accessToken = token
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    fun persistTokenAndUser(context: Context, token: String, id: Int) {
        accessToken = token
        userId = id
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putInt(KEY_USER_ID, id)
            .apply()
    }

    fun clearToken(context: Context) {
        accessToken = null
        userId = null
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_TOKEN).remove(KEY_USER_ID).apply()
    }
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

suspend fun fetchUserId(token: String): Int = withContext(Dispatchers.IO) {
    val req = Request.Builder()
        .url("${BASE_URL}users/me")
        .get()
        .header("Authorization", "Bearer $token")
        .build()

    httpClient.newCall(req).execute().use { resp ->
        val raw = resp.body?.string().orEmpty()
        if (!resp.isSuccessful) {
            val detail = runCatching { JSONObject(raw).optString("detail") }.getOrNull()
            val msg = if (!detail.isNullOrBlank()) detail else "Fetch /users/me failed: HTTP ${resp.code}"
            error(msg)
        }
        val json = JSONObject(raw)
        json.getInt("id")
    }
}

suspend fun loginAndFetchUser(username: String, password: String): Pair<String, Int> {
    val token = loginToServer(username, password)
    val userId = fetchUserId(token)
    return token to userId
}
