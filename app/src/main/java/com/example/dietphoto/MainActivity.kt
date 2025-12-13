package com.example.dietphoto

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import coil.compose.rememberAsyncImagePainter
import com.example.dietphoto.ui.theme.DietPhotoTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody

class MainActivity : ComponentActivity() {
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        cameraExecutor = Executors.newSingleThreadExecutor()
        setContent {
            DietPhotoTheme {
                MainScreen(cameraExecutor)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

@Composable
fun MainScreen(cameraExecutor: ExecutorService) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasPermission) {
        CameraContent(cameraExecutor)
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Brak uprawnień do kamery")
        }
    }
}

@Composable
fun CameraContent(cameraExecutor: ExecutorService) {
    val context = LocalContext.current
    val imageCapture = remember { ImageCapture.Builder().build() }
    var capturedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var isUploading by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        CameraPreviewView(modifier = Modifier.fillMaxSize(), imageCapture = imageCapture)

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = {
                    takePhoto(context, imageCapture, cameraExecutor) { uri ->
                        capturedPhotoUri = uri
                    }
                },
                shape = CircleShape,
                modifier = Modifier.size(70.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White)
            ) {
                Icon(Icons.Default.Camera, contentDescription = "Zrób zdjęcie", tint = Color.Black)
            }
        }

        capturedPhotoUri?.let { uri ->
            PhotoActionDialog(
                photoUri = uri,
                isUploading = isUploading,
                onDismiss = {
                    if (!isUploading) {
                        deletePhoto(context, uri)
                        capturedPhotoUri = null
                    }
                },
                onUpload = {
                    isUploading = true
                    uploadPhotoToServer(context, uri) { success ->
                        isUploading = false
                        if (success) {
                            deletePhoto(context, uri)
                            capturedPhotoUri = null
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun CameraPreviewView(modifier: Modifier, imageCapture: ImageCapture) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val previewView = remember { PreviewView(context) }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    ) { view ->
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(view.surfaceProvider)
            }
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture)
            } catch (e: Exception) {
                Log.e("CameraX", "Błąd bindowania kamery", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }
}

@Composable
fun PhotoActionDialog(
    photoUri: Uri,
    isUploading: Boolean,
    onDismiss: () -> Unit,
    onUpload: () -> Unit
) {
    Dialog(onDismissRequest = { if (!isUploading) onDismiss() }) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().wrapContentHeight()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                Text(if (isUploading) "Wysyłanie..." else "Co zrobić ze zdjęciem?", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(16.dp))

                if (isUploading) {
                    CircularProgressIndicator()
                } else {
                    Image(
                        painter = rememberAsyncImagePainter(photoUri),
                        contentDescription = "Zrobione zdjęcie",
                        modifier = Modifier.height(300.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (!isUploading) {
                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                            modifier = Modifier.widthIn(min = 120.dp).padding(bottom = 8.dp)
                        ) { Text("Usuń") }

                        Button(
                            onClick = onUpload,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) { Text("Wyślij na serwer") }
                    }
                }
            }
        }
    }
}

fun takePhoto(context: Context, imageCapture: ImageCapture, executor: ExecutorService, onImageCaptured: (Uri) -> Unit) {
    val photoFile = File(
        context.getExternalFilesDir(null),
        SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis()) + ".jpg"
    )
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) {
                Log.e("CameraX", "Błąd robienia zdjęcia: ${exc.message}", exc)
            }
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                onImageCaptured(Uri.fromFile(photoFile))
            }
        }
    )
}

// === GŁÓWNA LOGIKA SIECIOWA ===
fun uploadPhotoToServer(context: Context, uri: Uri, onResult: (Boolean) -> Unit) {
    Toast.makeText(context, "Rozpoczynam proces...", Toast.LENGTH_SHORT).show()

    CoroutineScope(Dispatchers.IO).launch {
        try {
            val path = uri.path ?: return@launch
            val file = File(path)

            if (!file.exists()) {
                withContext(Dispatchers.Main) { Toast.makeText(context, "Błąd pliku", Toast.LENGTH_SHORT).show() }
                return@launch
            }

            // 0. Czytamy wymiary zdjęcia (Wymagane w kroku 3)
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            val imgWidth = options.outWidth
            val imgHeight = options.outHeight

            // KROK 1: Presign - pobranie URL i ID
            Log.d("UPLOAD", "Krok 1: Presign")
            val presignResp = RetrofitClient.api.getPresignUrl(PresignRequest("jpg"))
            if (!presignResp.isSuccessful || presignResp.body() == null) throw Exception("Błąd Presign: ${presignResp.code()}")

            val uploadUrl = presignResp.body()!!.upload_url
            val photoId = presignResp.body()!!.photo_id

            // KROK 2: Upload Binary - wysłanie pliku
            Log.d("UPLOAD", "Krok 2: Upload na $uploadUrl")
            val reqFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
            val uploadResp = RetrofitClient.api.uploadImageBinary(uploadUrl, reqFile)
            if (!uploadResp.isSuccessful) throw Exception("Błąd Binary Upload: ${uploadResp.code()}")

            // KROK 3: Confirm - potwierdzenie na serwerze
            Log.d("UPLOAD", "Krok 3: Confirm")
            val confirmResp = RetrofitClient.api.confirmUpload(ConfirmRequest(photoId, "jpg", imgWidth, imgHeight))
            if (!confirmResp.isSuccessful) throw Exception("Błąd Confirm: ${confirmResp.code()}")

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Sukces! Zdjęcie wysłane.", Toast.LENGTH_LONG).show()
                onResult(true)
            }

        } catch (e: Exception) {
            Log.e("UPLOAD", "Błąd procesu", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Błąd: ${e.message}", Toast.LENGTH_LONG).show()
                onResult(false)
            }
        }
    }
}

fun deletePhoto(context: Context, uri: Uri) {
    try {
        uri.path?.let { path ->
            val file = File(path)
            if (file.exists()) file.delete()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}