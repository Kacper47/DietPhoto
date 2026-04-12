// Ekran kamery dla etykiety. Robi jedno zdjęcie i przekazuje dalej.
package com.example.dietphoto

import android.net.Uri
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.concurrent.ExecutorService
import android.app.Activity
import android.content.pm.ActivityInfo

@Composable
fun LabelCameraScreen(
    cameraExecutor: ExecutorService,
    onBack: () -> Unit,
    onPhotoTaken: (Uri) -> Unit
) {
    CameraPermissionWrapper {
        LabelCameraContent(cameraExecutor, onBack, onPhotoTaken)
    }
}

@Composable
private fun LabelCameraContent(
    cameraExecutor: ExecutorService,
    onBack: () -> Unit,
    onPhotoTaken: (Uri) -> Unit
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

    Box(modifier = Modifier.fillMaxSize()) {

        CameraPreviewView(
            modifier = Modifier.fillMaxSize(),
            imageCapture = imageCapture
        )

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 40.dp, start = 16.dp)
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Powrót", tint = Color.White)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = {
                    takePhoto(context, imageCapture, cameraExecutor) { uri ->
                        onPhotoTaken(uri)
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