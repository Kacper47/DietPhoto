// Ekran kamery dla posiłku. Prowadzi użytkownika przez 3 zdjęcia (góra, lewa, prawa).
package com.example.dietphoto

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview as CameraPreview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import android.app.Activity
import android.content.pm.ActivityInfo

// Kolejne wskazówki dla użytkownika
val mealHints = listOf(
    "Zrób zdjęcie z góry",
    "Zrób zdjęcie z lewej strony",
    "Zrób zdjęcie z prawej strony"
)

@Composable
fun MealCameraScreen(
    cameraExecutor: ExecutorService,
    onBack: () -> Unit,
    onAllPhotosTaken: (List<Uri>) -> Unit
) {
    val context = LocalContext.current
    val imageCapture = remember { ImageCapture.Builder().build() }

    DisposableEffect(Unit) {
        val activity = context as Activity
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }



    // Lista zrobionych zdjęć (maks. 3)
    val takenPhotos = remember { mutableStateListOf<Uri>() }
    val currentStep = takenPhotos.size // 0, 1, 2

    Box(modifier = Modifier.fillMaxSize()) {

        // Podgląd kamery
        CameraPreviewView(
            modifier = Modifier.fillMaxSize(),
            imageCapture = imageCapture
        )

        // Przycisk powrotu
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 40.dp, start = 16.dp)
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Powrót", tint = Color.White)
        }

        // Wskazówka — subtelny chip na górze ekranu
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                fadeIn() + slideInVertically { -it } togetherWith fadeOut()
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp),
            label = "hint"
        ) { step ->
            if (step < mealHints.size) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.Black.copy(alpha = 0.45f),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = mealHints[step],
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                }
            }
        }

        // Pasek postępu — 3 kropki na dole nad przyciskiem
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 130.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            repeat(3) { index ->
                val done = index < takenPhotos.size
                val current = index == currentStep
                Surface(
                    shape = CircleShape,
                    color = when {
                        done -> MaterialTheme.colorScheme.primary
                        current -> Color.White
                        else -> Color.White.copy(alpha = 0.4f)
                    },
                    modifier = Modifier.size(if (current) 14.dp else 10.dp)
                ) {}
            }
        }

        // Przycisk migawki
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = {
                    takePhoto(context, imageCapture, cameraExecutor) { uri ->
                        takenPhotos.add(uri)
                        if (takenPhotos.size == 3) {
                            onAllPhotosTaken(takenPhotos.toList())
                        }
                    }
                },
                shape = CircleShape,
                modifier = Modifier
                    .size(85.dp)
                    .border(5.dp, Color.White, CircleShape)
                    .padding(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White)
            ) {}
        }
    }
}