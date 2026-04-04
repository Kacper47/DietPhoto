// Logika wysyłania zdjęć na serwer. Obsługuje flow dla posiłku (3 zdjęcia) i etykiety (1 zdjęcie).
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

private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
private val JPEG_MEDIA = "image/jpeg".toMediaType()
private val uploadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

// Dane jednego zdjęcia po presign + uploadzie
data class UploadedPhoto(
    val photoId: String,
    val width: Int,
    val height: Int,
    val extension: String = "jpg"
)

// Presign + PUT dla jednego zdjęcia, zwraca UploadedPhoto
private suspend fun presignAndUpload(
    context: Context,
    uri: Uri,
    folder: String,
    token: String
): UploadedPhoto = withContext(Dispatchers.IO) {
    val file = uriToFileOrThrow(context, uri)
    val extension = file.extension.lowercase().ifBlank { "jpg" }
    val (w, h) = readImageSize(context, uri)

    // KROK 1: Presign
    val presignBody = JSONObject()
        .put("extension", extension)
        .put("folder", folder)
        .toString()
        .toRequestBody(JSON_MEDIA)

    val presignReq = Request.Builder()
        .url("${BASE_URL}photos/presign")
        .post(presignBody)
        .header("Authorization", "Bearer $token")
        .build()

    val (photoId, uploadUrl) = httpClient.newCall(presignReq).execute().use { resp ->
        val raw = resp.body?.string().orEmpty()
        if (!resp.isSuccessful) error("Presign failed: HTTP ${resp.code} $raw")
        val json = JSONObject(raw)
        json.getString("photo_id") to json.getString("upload_url")
    }

    // KROK 2: PUT
    val putReq = Request.Builder()
        .url(uploadUrl)
        .put(file.asRequestBody(JPEG_MEDIA))
        .header("Content-Type", "image/jpeg")
        .build()

    httpClient.newCall(putReq).execute().use { resp ->
        if (resp.code !in 200..299) {
            val raw = resp.body?.string().orEmpty()
            error("Upload PUT failed: HTTP ${resp.code} $raw")
        }
    }

    UploadedPhoto(photoId, w, h, extension)
}

// Wysyłanie posiłku — 3 zdjęcia w kolejności: przód, lewa, prawa
fun uploadMealToServer(
    context: Context,
    frontUri: Uri,
    leftUri: Uri,
    rightUri: Uri,
    onSuccess: () -> Unit = {},
    onError: (String) -> Unit = {}
) {
    val token = AuthStore.accessToken
    if (token.isNullOrBlank()) {
        onError("Brak tokenu. Zaloguj się ponownie.")
        return
    }

    uploadScope.launch {
        try {
            // Upload 3 zdjęć równolegle (kolejno żeby nie przeciążać)
            val front = presignAndUpload(context, frontUri, "meals", token)
            val left = presignAndUpload(context, leftUri, "meals", token)
            val right = presignAndUpload(context, rightUri, "meals", token)

            // KROK 3: POST /meals
            fun photoJson(p: UploadedPhoto) = JSONObject()
                .put("photo_id", p.photoId)
                .put("width", p.width)
                .put("height", p.height)
                .put("extension", p.extension)

            val confirmBody = JSONObject()
                .put("front", photoJson(front))
                .put("left", photoJson(left))
                .put("right", photoJson(right))
                .toString()
                .toRequestBody(JSON_MEDIA)

            val confirmReq = Request.Builder()
                .url("${BASE_URL}meals")
                .post(confirmBody)
                .header("Authorization", "Bearer $token")
                .build()

            httpClient.newCall(confirmReq).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("Meals confirm failed: HTTP ${resp.code} $raw")
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Posiłek wysłany na serwer", Toast.LENGTH_SHORT).show()
                onSuccess()
            }
        } catch (e: Exception) {
            Log.e("UPLOAD_MEAL", "Error", e)
            withContext(Dispatchers.Main) {
                val msg = e.message ?: "Nieznany błąd"
                Toast.makeText(context, "Błąd: $msg", Toast.LENGTH_LONG).show()
                onError(msg)
            }
        }
    }
}

// Wysyłanie etykiety — 1 zdjęcie
fun uploadLabelToServer(
    context: Context,
    photoUri: Uri,
    onSuccess: () -> Unit = {},
    onError: (String) -> Unit = {}
) {
    val token = AuthStore.accessToken
    if (token.isNullOrBlank()) {
        onError("Brak tokenu. Zaloguj się ponownie.")
        return
    }

    uploadScope.launch {
        try {
            val photo = presignAndUpload(context, photoUri, "labels", token)

            // KROK 3: POST /labels
            val confirmBody = JSONObject()
                .put("photo", JSONObject()
                    .put("photo_id", photo.photoId)
                    .put("width", photo.width)
                    .put("height", photo.height)
                    .put("extension", photo.extension)
                )
                .toString()
                .toRequestBody(JSON_MEDIA)

            val confirmReq = Request.Builder()
                .url("${BASE_URL}labels")
                .post(confirmBody)
                .header("Authorization", "Bearer $token")
                .build()

            httpClient.newCall(confirmReq).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("Labels confirm failed: HTTP ${resp.code} $raw")
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Etykieta wysłana na serwer", Toast.LENGTH_SHORT).show()
                onSuccess()
            }
        } catch (e: Exception) {
            Log.e("UPLOAD_LABEL", "Error", e)
            withContext(Dispatchers.Main) {
                val msg = e.message ?: "Nieznany błąd"
                Toast.makeText(context, "Błąd: $msg", Toast.LENGTH_LONG).show()
                onError(msg)
            }
        }
    }
}

private fun uriToFileOrThrow(context: Context, uri: Uri): File {
    // Jeśli to file:// — bezpośrednia ścieżka
    if (uri.scheme == "file") {
        val path = uri.path ?: error("Uri bez ścieżki: $uri")
        return File(path).also { if (!it.exists()) error("Plik nie istnieje: $path") }
    }
    // Jeśli to content:// — kopiuj do pliku tymczasowego
    val tempFile = File.createTempFile("upload_", ".jpg", context.cacheDir)
    context.contentResolver.openInputStream(uri)?.use { input ->
        tempFile.outputStream().use { output -> input.copyTo(output) }
    } ?: error("Nie można otworzyć Uri: $uri")
    return tempFile
}

private fun readImageSize(context: Context, uri: Uri): Pair<Int, Int> {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri).use { input ->
        if (input == null) return 0 to 0
        BitmapFactory.decodeStream(input, null, opts)
    }
    return (opts.outWidth.coerceAtLeast(0)) to (opts.outHeight.coerceAtLeast(0))
}