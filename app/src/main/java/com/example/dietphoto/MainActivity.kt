// Punkt wejścia aplikacji. Zarządza stanem nawigacji między ekranami.
package com.example.dietphoto

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import com.example.dietphoto.ui.theme.DietPhotoTheme
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

enum class Screen {
    LOGIN, SELECTION, MEAL_CAMERA, LABEL_CAMERA, RESULT
}

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
    val context = LocalContext.current
// WERSJA ORYGINALNA I DOCELOWA: EKRAN LOGOWANIA NAJPIERW I DALEJ SESJA UŻYTKOWNIKA
    var screen by rememberSaveable {
        mutableStateOf(
            if (AuthStore.loadSavedToken(context) != null) Screen.SELECTION else Screen.LOGIN
        )
    }

    // WERSJA TYMCZASOWA: TYLKO DO WYSYŁANIA ZDJĘĆ DO TRENINGU BEZ LOGOWANIA
//    var screen by rememberSaveable {
//        mutableStateOf(Screen.SELECTION)
//    }


    var resultPhotos by rememberSaveable { mutableStateOf<List<Uri>>(emptyList()) }
    var isMealMode by rememberSaveable { mutableStateOf(true) }

    val onLogout: () -> Unit = {
        AuthStore.clearToken(context)
        screen = Screen.LOGIN
    }

    when (screen) {
        Screen.LOGIN -> LoginScreen(
            onLoginSuccess = { token, userId ->
                AuthStore.persistTokenAndUser(context, token, userId)
                screen = Screen.SELECTION
            }
        )
        Screen.SELECTION -> SelectionScreen(
            onMealSelected = {
                isMealMode = true
                screen = Screen.MEAL_CAMERA
            },
            onLabelSelected = {
                isMealMode = false
                screen = Screen.LABEL_CAMERA
            },
            onLogout = onLogout
        )
        Screen.MEAL_CAMERA -> MealCameraScreen(
            cameraExecutor = cameraExecutor,
            onBack = { screen = Screen.SELECTION },
            onAllPhotosTaken = { photos ->
                resultPhotos = photos
                screen = Screen.RESULT
            }
        )
        Screen.LABEL_CAMERA -> LabelCameraScreen(
            cameraExecutor = cameraExecutor,
            onBack = { screen = Screen.SELECTION },
            onPhotoTaken = { photo ->
                resultPhotos = listOf(photo)
                screen = Screen.RESULT
            }
        )
        Screen.RESULT -> ResultScreen(
            photos = resultPhotos,
            isMealMode = isMealMode,
            onBack = { screen = Screen.SELECTION }
        )
    }
}