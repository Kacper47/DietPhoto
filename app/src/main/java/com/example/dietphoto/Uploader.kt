package com.example.dietphoto
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File

private val JSON = "application/json; charset=utf-8".toMediaType()
private val JPEG = "image/jpeg".toMediaType()

private val uploadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

fun uploadPhotoToServer(
    context: Context,
    uri: Uri,
    onSuccess: () -> Unit = {},
    onError: (String) -> Unit = {}
) {
    uploadScope.launch {
        try {
            val file = uriToFileOrThrow(uri)
            val extension = file.extension.lowercase().ifBlank { "jpg" }

            // (opcjonalnie) weryfikacja: serwer oczekuje jpg → Content-Type image/jpeg
            val (w, h) = readImageSize(context, uri)

            // KROK 1: PRESIGN
            val presignBody = JSONObject()
                .put("extension", extension)
                .toString()
                .toRequestBody(JSON)

            val presignReq = Request.Builder()
                .url("${BASE_URL}photos/presign")
                .post(presignBody)
                .build()

            val (photoId, uploadUrl) = httpClient.newCall(presignReq).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("Presign failed: HTTP ${resp.code} $raw")

                val json = JSONObject(raw)
                val pid = json.getString("photo_id")
                val uurl = json.getString("upload_url")
                pid to uurl
            }

            // KROK 2: PUT (raw bytes, nie multipart)
            val putReq = Request.Builder()
                .url(uploadUrl) // użyj pełnego upload_url z presign!
                .put(file.asRequestBody(JPEG))
                .header("Content-Type", "image/jpeg")
                .build()

            httpClient.newCall(putReq).execute().use { resp ->
                if (resp.code !in 200..299) {
                    val raw = resp.body?.string().orEmpty()
                    error("Upload PUT failed: HTTP ${resp.code} $raw")
                }
            }

            // KROK 3: CONFIRM
            val confirmBody = JSONObject()
                .put("photo_id", photoId)
                .put("extension", extension)
                .put("width", w)
                .put("height", h)
                .toString()
                .toRequestBody(JSON)

            val confirmReq = Request.Builder()
                .url("${BASE_URL}photos/confirm")
                .post(confirmBody)
                .build()

            httpClient.newCall(confirmReq).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("Confirm failed: HTTP ${resp.code} $raw")
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Wysłano zdjęcie ✅", Toast.LENGTH_SHORT).show()
                onSuccess()
            }
        } catch (e: Exception) {
            Log.e("SERVER_UPLOAD", "Upload error", e)
            withContext(Dispatchers.Main) {
                val msg = e.message ?: "Nieznany błąd uploadu"
                Toast.makeText(context, "Błąd wysyłania: $msg", Toast.LENGTH_LONG).show()
                onError(msg)
            }
        }
    }
}

private fun uriToFileOrThrow(uri: Uri): File {
    // U Ciebie jest Uri.fromFile(photoFile), więc to wystarczy:
    val path = uri.path ?: error("Uri bez ścieżki: $uri")
    return File(path).also { if (!it.exists()) error("Plik nie istnieje: $path") }
}

private fun readImageSize(context: Context, uri: Uri): Pair<Int, Int> {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri).use { input ->
        if (input == null) return 0 to 0
        BitmapFactory.decodeStream(input, null, opts)
    }
    return (opts.outWidth.coerceAtLeast(0)) to (opts.outHeight.coerceAtLeast(0))
}
