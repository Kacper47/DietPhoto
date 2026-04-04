// Ekran wyników. Pokazuje miniatury/pełne zdjęcia, wartości odżywcze i opcję zapisu/wysyłki.
package com.example.dietphoto

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.exifinterface.media.ExifInterface
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt
import android.util.Log

@Composable
fun ResultScreen(
    photos: List<Uri>,
    isMealMode: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    // Zaznaczone zdjęcia (wspólne dla zapisu i wysyłki)
    val checkedPhotos = remember { mutableStateMapOf<Int, Boolean>() }
    var photosVisible by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }
    var wasSent by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val toSave = photos.filterIndexed { i, _ -> checkedPhotos[i] == true }
            toSave.forEach { savePhotoToGallery(context, it) }
            Toast.makeText(context, "Zapisano ${toSave.size} zdjecie(a)", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Brak uprawnien do zapisu", Toast.LENGTH_SHORT).show()
        }
    }

    val panelFraction = remember { Animatable(0f) }
    // Czy panel jest zwinięty (prawie schowany)
    val isPanelCollapsed by remember { derivedStateOf { panelFraction.value < 0.15f } }

    LaunchedEffect(Unit) {
        delay(300)
        photosVisible = true
        delay(400)
        panelFraction.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {
        val fullPanelHeight = maxHeight * 0.65f
        val handleHeight = 36.dp
        val panelHeightPx = with(density) { fullPanelHeight.toPx() }
        val handleHeightPx = with(density) { handleHeight.toPx() }

        val offsetY by remember {
            derivedStateOf {
                val hidden = panelHeightPx - handleHeightPx
                (hidden * (1f - panelFraction.value)).roundToInt()
            }
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 40.dp, start = 16.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Powrot")
        }

        // Zdjęcia — duże w kolumnie gdy panel zwinięty, małe w rzędzie gdy rozwinięty
        if (isPanelCollapsed) {
            // Tryb pełny — duże zdjęcia w kolumnie ze scrollem
            val labels = if (isMealMode)
                listOf("Z gory", "Z lewej", "Z prawej")
            else
                listOf("Etykieta")

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 100.dp, bottom = handleHeight + 8.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                photos.forEachIndexed { index, uri ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box {
                            Image(
                                painter = rememberAsyncImagePainter(uri),
                                contentDescription = labels.getOrElse(index) { "Zdjecie" },
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(4f / 3f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .border(
                                        width = if (checkedPhotos[index] == true) 2.dp else 0.dp,
                                        color = if (checkedPhotos[index] == true)
                                            MaterialTheme.colorScheme.primary
                                        else Color.Transparent,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .clickable {
                                        checkedPhotos[index] = checkedPhotos[index] != true
                                    }
                            )
                            Checkbox(
                                checked = checkedPhotos[index] == true,
                                onCheckedChange = { checkedPhotos[index] = it },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                            )
                        }
                        Text(
                            text = labels.getOrElse(index) { "Zdjecie ${index + 1}" },
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        } else {
            // Tryb miniatur — małe zdjęcia w rzędzie
            val labels = if (isMealMode)
                listOf("Z gory", "Z lewej", "Z prawej")
            else
                listOf("Etykieta")

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 100.dp, start = 16.dp, end = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    photos.forEachIndexed { index, uri ->
                        val scale by animateFloatAsState(
                            targetValue = if (photosVisible) 1f else 0.3f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            label = "photoScale$index"
                        )

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box {
                                Image(
                                    painter = rememberAsyncImagePainter(uri),
                                    contentDescription = labels.getOrElse(index) { "Zdjecie" },
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .scale(scale)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(
                                            width = if (checkedPhotos[index] == true) 2.dp else 0.dp,
                                            color = if (checkedPhotos[index] == true)
                                                MaterialTheme.colorScheme.primary
                                            else Color.Transparent,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            checkedPhotos[index] = checkedPhotos[index] != true
                                        }
                                )
                                Checkbox(
                                    checked = checkedPhotos[index] == true,
                                    onCheckedChange = { checkedPhotos[index] = it },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = labels.getOrElse(index) { "Zdjecie ${index + 1}" },
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }

        // Przeciągany panel
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(fullPanelHeight)
                .align(Alignment.BottomCenter)
                .offset { IntOffset(0, offsetY) },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = Color.White,
            shadowElevation = 16.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // Strefa uchwytu
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    scope.launch {
                                        if (panelFraction.value > 0.5f) {
                                            panelFraction.animateTo(
                                                1f,
                                                animationSpec = spring(stiffness = Spring.StiffnessMedium)
                                            )
                                        } else {
                                            panelFraction.animateTo(
                                                0f,
                                                animationSpec = spring(stiffness = Spring.StiffnessMedium)
                                            )
                                        }
                                    }
                                }
                            ) { _, dragAmount ->
                                scope.launch {
                                    val delta = -dragAmount / panelHeightPx
                                    panelFraction.snapTo(
                                        (panelFraction.value + delta).coerceIn(0f, 1f)
                                    )
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFFE0E0E0))
                    )
                }

                // Treść panelu
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    val anyChecked = checkedPhotos.values.any { it }

                    // Przycisk wysyłki — na górze
                    Button(
                        onClick = {
                            Log.d("UPLOAD_DEBUG", "Kliknięto przycisk, siec: ${isNetworkAvailable(context)}")
                            Log.d("UPLOAD_DEBUG", "Zaznaczone: ${checkedPhotos.filter { it.value }.keys}")

                            if (!isNetworkAvailable(context)) {
                                Toast.makeText(context, "Brak internetu!", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val selectedIndices = checkedPhotos.filter { it.value }.keys.sorted()
                            val selectedUris = selectedIndices.map { photos[it] }

                            isSending = true
                            if (isMealMode) {
                                Log.d("UPLOAD_DEBUG", "Wywoluje uploadMealToServer")
                                val front = selectedUris.getOrNull(0) ?: photos[0]
                                val left = selectedUris.getOrNull(1) ?: photos[1]
                                val right = selectedUris.getOrNull(2) ?: photos[2]
                                uploadMealToServer(
                                    context = context,
                                    frontUri = front,
                                    leftUri = left,
                                    rightUri = right,
                                    onSuccess = {
                                        isSending = false
                                        wasSent = true
                                                },
                                    onError = { isSending = false }
                                )
                            } else {
                                Log.d("UPLOAD_DEBUG", "isMealMode: $isMealMode, wywoluje uploadLabelToServer")
                                val photo = selectedUris.firstOrNull() ?: photos[0]
                                Log.d("UPLOAD_DEBUG", "photo uri: $photo")
                                uploadLabelToServer(
                                    context = context,
                                    photoUri  = photo,
                                    onSuccess = {
                                        isSending = false
                                        wasSent = true
                                                },
                                    onError = { isSending = false }
                                )
                            }
                        },
                        enabled = anyChecked && !isSending,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isSending) "Wysyłanie..." else "Wyślij zdjęcia do treningu")
                    }

                    // Komunikat po wysłaniu
                    if (wasSent) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Dziękujemy za dane uczące dla naszego systemu!",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary,
                            lineHeight = 20.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Wartości odżywcze (wkrótce)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    NutritionRow("Kalorie", "— kcal")
                    NutritionRow("Białko", "— g")
                    NutritionRow("...", "...")

                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedButton(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(android.Manifest.permission.READ_MEDIA_IMAGES)
                            } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                                permissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            } else {
                                val toSave = photos.filterIndexed { i, _ -> checkedPhotos[i] == true }
                                toSave.forEach { savePhotoToGallery(context, it) }
                                Toast.makeText(context, "Zapisano ${toSave.size} zdjecie(a)", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = anyChecked,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Zapisz zaznaczone do galerii")
                    }
                }
            }
        }
    }
}

@Composable
fun NutritionRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 15.sp, color = Color.DarkGray)
        Text(text = value, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
    HorizontalDivider(color = Color(0xFFF0F0F0))
}

fun savePhotoToGallery(context: Context, uri: Uri) {
    try {
        val file = File(uri.path ?: return)
        if (!file.exists()) return

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/DietPhoto")
            }
        }

        val resolver = context.contentResolver
        val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        imageUri?.let { dest ->
            resolver.openOutputStream(dest)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            }
            val sourceExif = ExifInterface(file)
            resolver.openFileDescriptor(dest, "rw")?.use { pfd ->
                val destExif = ExifInterface(pfd.fileDescriptor)
                val orientation = sourceExif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                destExif.setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                destExif.saveAttributes()
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}