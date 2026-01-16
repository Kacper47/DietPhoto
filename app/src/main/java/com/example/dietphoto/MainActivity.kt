package com.example.dietphoto

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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

class MainActivity : ComponentActivity() {

    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        cameraExecutor = Executors.newSingleThreadExecutor()

        setContent {
            DietPhotoTheme {
                AppRoot(cameraExecutor)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

@Composable
fun AppRoot(cameraExecutor: ExecutorService) {
    var isLoggedIn by rememberSaveable { mutableStateOf(AuthStore.accessToken != null) }

    if (isLoggedIn) {
        MainScreen(cameraExecutor)
    } else {
        LoginScreen(
            onLoginSuccess = { token ->
                AuthStore.accessToken = token
                isLoggedIn = true
            }
        )
    }
}

@Composable
fun LoginScreen(onLoginSuccess: (String) -> Unit) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Login", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            singleLine = true,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )

        if (error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val trimmedUser = username.trim()
                if (trimmedUser.isBlank() || password.isBlank()) {
                    error = "Enter username and password."
                    return@Button
                }

                isLoading = true
                error = null
                scope.launch {
                    try {
                        val token = loginToServer(trimmedUser, password)
                        onLoginSuccess(token)
                    } catch (e: Exception) {
                        error = e.message ?: "Login failed."
                    } finally {
                        isLoading = false
                    }
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isLoading) "Signing in..." else "Sign in")
        }
    }
}

@Composable
fun MainScreen(cameraExecutor: ExecutorService) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
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

    // Obiekt ImageCapture służy do robienia zdjęć
    val imageCapture = remember { ImageCapture.Builder().build() }

    // Stan tymczasowego zdjęcia (tylko do dialogu)
    var capturedPhotoUri by remember { mutableStateOf<Uri?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {

        // Podgląd kamery
        CameraPreviewView(
            modifier = Modifier.fillMaxSize(),
            imageCapture = imageCapture
        )

        // Przycisk robienia zdjęcia
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
                Icon(
                    Icons.Default.Camera,
                    contentDescription = "Zrób zdjęcie",
                    tint = Color.Black
                )
            }
        }

        // Okno decyzyjne po zrobieniu zdjęcia (Dialog)
        capturedPhotoUri?.let { uri ->
            PhotoActionDialog(
                photoUri = uri,
                onDismiss = {
                    // Usuń plik i zamknij dialog
                    deletePhoto(context, uri)
                    capturedPhotoUri = null
                },
                onUpload = {
                    uploadPhotoToServer(
                    context = context,
                    uri = uri,
                    onSuccess = {
                        deletePhoto(context, uri)
                        capturedPhotoUri = null
                        },
                    onError = {
                        // tu możesz zdecydować: zostawić plik i/lub zostawić dialog
                        capturedPhotoUri = null
                        }
                    )
                }

            )
        }
    }
}

// Komponent podglądu kamery
@Composable
fun CameraPreviewView(
    modifier: Modifier,
    imageCapture: ImageCapture
) {
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
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                Log.e("CameraX", "Błąd bindowania kamery", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }
}

// Okienko z podglądem i dwoma przyciskami
@Composable
fun PhotoActionDialog(
    photoUri: Uri,
    onDismiss: () -> Unit,
    onUpload: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                Text("Co zrobić ze zdjęciem?", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(16.dp))

                Image(
                    painter = rememberAsyncImagePainter(photoUri),
                    contentDescription = "Zrobione zdjęcie",
                    modifier = Modifier
                        .height(300.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // guzik "Usuń"
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        modifier = Modifier
                            .widthIn(min = 120.dp)
                            .padding(bottom = 8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text("Usuń")
                    }

                    // guzik "Wyślij na serwer"
                    Button(
                        onClick = onUpload,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Wyślij na serwer")
                    }
                }
            }
        }
    }
}



// Funkcja robiąca zdjęcie
fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    executor: ExecutorService,
    onImageCaptured: (Uri) -> Unit
) {
    // Tutaj jest plik zdjęcia
    val photoFile = File(
        context.getExternalFilesDir(null),
        SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
            .format(System.currentTimeMillis()) + ".jpg"
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
                // Uri wskazujące na plik zdjęcia
                val savedUri = Uri.fromFile(photoFile)

                // I to Uri niesiemy dalej do UI / uploadu:
                onImageCaptured(savedUri)
            }
        }
    )
}

// Symulacja wysyłania na serwer
fun uploadPhotoToServer(context: Context, uri: Uri) {
    Toast.makeText(context, "Wysyłanie zdjęcia na serwer...", Toast.LENGTH_SHORT).show()
    Log.d("SERVER_UPLOAD", "Rozpoczynam wysyłanie pliku: $uri")
    // tutaj można dodać dalsze operacje
}

// Usuwanie zdjęcia
fun deletePhoto(context: Context, uri: Uri) {
    try {
        // dla Uri.fromFile – usuwamy bezpośrednio plik
        uri.path?.let { path ->
            val file = File(path)
            if (file.exists()) {
                file.delete()
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
