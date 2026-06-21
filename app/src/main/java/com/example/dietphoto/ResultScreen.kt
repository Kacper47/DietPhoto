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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.exifinterface.media.ExifInterface
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt


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
    var sendError by remember { mutableStateOf<String?>(null) }
    var pollingTimedOut by remember { mutableStateOf(false) }
    var photoIdsForPolling by remember { mutableStateOf<List<String>>(emptyList()) }
    var classificationResults by remember { mutableStateOf<List<ClassificationResult>>(emptyList()) }
    var isPolling by remember { mutableStateOf(false) }
    var selectedLabel by remember { mutableStateOf<String?>(null) }
    var weightText by remember { mutableStateOf("100") }

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

    // Auto-upload po wejściu na ekran
    LaunchedEffect(Unit) {
        if (photos.isEmpty()) return@LaunchedEffect
        isSending = true
        if (isMealMode && photos.size >= 3) {
            uploadMealToServer(
                context = context,
                frontUri = photos[0],
                leftUri = photos[1],
                rightUri = photos[2],
                onSuccess = { photoIds ->
                    isSending = false
                    wasSent = true
                    photoIdsForPolling = photoIds
                },
                onError = { msg ->
                    isSending = false
                    sendError = msg
                }
            )
        } else if (!isMealMode) {
            uploadLabelToServer(
                context = context,
                photoUri = photos[0],
                onSuccess = { photoId ->
                    isSending = false
                    wasSent = true
                    photoIdsForPolling = listOf(photoId)
                },
                onError = { msg ->
                    isSending = false
                    sendError = msg
                }
            )
        }
    }

    LaunchedEffect(classificationResults) {
        if (selectedLabel == null && classificationResults.isNotEmpty()) {
            selectedLabel = combineClassificationResults(classificationResults).firstOrNull()?.first
        }
    }

    LaunchedEffect(photoIdsForPolling) {
        if (photoIdsForPolling.isEmpty()) return@LaunchedEffect
        val token = AuthStore.accessToken ?: return@LaunchedEffect
        isPolling = true
        pollingTimedOut = false
        try {
            repeat(30) {
                delay(2000)
                val results = photoIdsForPolling.mapNotNull { id ->
                    try { fetchClassification(id, token) } catch (e: Exception) { null }
                }
                if (results.isNotEmpty()) classificationResults = results
                if (results.all { it.classificationStatus != "pending" }) {
                    isPolling = false
                    return@LaunchedEffect
                }
            }
            pollingTimedOut = true
        } finally {
            isPolling = false
        }
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

                    if (isSending || isPolling) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Analizowanie zdjęcia...", fontSize = 14.sp, color = Color.Gray)
                        }
                    } else if (sendError != null) {
                        Text(
                            "Błąd wysyłania: $sendError",
                            fontSize = 14.sp,
                            color = Color(0xFFD32F2F)
                        )
                    } else if (pollingTimedOut) {
                        Text(
                            "Serwer nie odpowiedział na czas. Spróbuj ponownie.",
                            fontSize = 14.sp,
                            color = Color(0xFFD32F2F)
                        )
                    } else if (classificationResults.isNotEmpty()) {
                        if (isMealMode) {
                            val combinedPredictions = combineClassificationResults(classificationResults)
                            if (combinedPredictions.isNotEmpty()) {
                                val currentLabel = selectedLabel ?: combinedPredictions.first().first
                                val displayName = currentLabel.toFoodDisplayName()
                                val nutrition = nutritionFor(currentLabel)
                                val grams = weightText.toIntOrNull() ?: 0
                                val totalKcal = if (grams > 0) (nutrition.kcal * grams) / 100 else 0
                                val currentConfidence = combinedPredictions
                                    .firstOrNull { it.first == currentLabel }?.second ?: 0.0

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                displayName,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 20.sp,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Text(
                                                "Pewność: ${(currentConfidence * 100).toInt()}%",
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                            )
                                        }
                                        Text(
                                            text = "${nutrition.kcal}\nkcal/100g",
                                            fontSize = 12.sp,
                                            textAlign = TextAlign.Center,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    "Wprowadź gramaturę:",
                                    fontSize = 13.sp,
                                    color = Color.Gray
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    OutlinedTextField(
                                        value = weightText,
                                        onValueChange = { new ->
                                            weightText = new.filter { it.isDigit() }.take(5)
                                        },
                                        label = { Text("Gramatura") },
                                        suffix = { Text("g") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.width(140.dp),
                                        singleLine = true
                                    )
                                    Column {
                                        Text(
                                            "$totalKcal",
                                            fontSize = 36.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text("kcal", fontSize = 14.sp, color = Color.Gray)
                                    }
                                }

                                if (grams > 0) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    NutritionRow("Białko", "%.1f g".format(nutrition.proteinG * grams / 100f))
                                    NutritionRow("Tłuszcz", "%.1f g".format(nutrition.fatG * grams / 100f))
                                    NutritionRow("Węglowodany", "%.1f g".format(nutrition.carbsG * grams / 100f))
                                    NutritionRow("Błonnik", "%.1f g".format(nutrition.fiberG * grams / 100f))
                                    NutritionRow("Sól", "%.2f g".format(nutrition.saltG * grams / 100f))
                                }

                                if (combinedPredictions.size > 1) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    HorizontalDivider()
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "Inne możliwości",
                                        fontSize = 13.sp,
                                        color = Color.Gray,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    combinedPredictions.forEach { (label, conf) ->
                                        val isSelected = currentLabel == label
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                                    else Color.Transparent
                                                )
                                                .clickable { selectedLabel = label }
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                                contentDescription = null,
                                                tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                label.toFoodDisplayName(),
                                                modifier = Modifier.weight(1f),
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                "${(conf * 100).toInt()}%",
                                                fontSize = 14.sp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                                                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                            } else if (classificationResults.any { it.classificationStatus == "classification_failed" }) {
                                val errorDetail = classificationResults.firstOrNull { it.errorMessage != null }?.errorMessage
                                Text("Klasyfikacja nie powiodła się", fontSize = 14.sp, color = Color(0xFFD32F2F))
                                if (errorDetail != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(errorDetail, fontSize = 12.sp, color = Color.Gray)
                                }
                            } else if (classificationResults.all { it.classificationStatus == "disabled" }) {
                                Text("Klasyfikacja wyłączona na serwerze", fontSize = 14.sp, color = Color.Gray)
                            }
                        } else {
                            val result = classificationResults.firstOrNull()
                            if (result?.extractedText?.isNotEmpty() == true) {
                                val nutrition = parseNutritionFromOcr(result.extractedText)
                                val hasNutrition = listOf(
                                    nutrition.calories, nutrition.protein,
                                    nutrition.fat, nutrition.carbs
                                ).any { it != null }

                                if (hasNutrition) {
                                    Text("Wartości odżywcze", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    nutrition.calories?.let { NutritionRow("Kalorie", it) }
                                    nutrition.protein?.let { NutritionRow("Białko", it) }
                                    nutrition.fat?.let { NutritionRow("Tłuszcz", it) }
                                    nutrition.carbs?.let { NutritionRow("Węglowodany", it) }
                                    nutrition.fiber?.let { NutritionRow("Błonnik", it) }
                                    nutrition.salt?.let { NutritionRow("Sól", it) }
                                    Spacer(modifier = Modifier.height(20.dp))
                                }

                                Text(
                                    text = "Pełny tekst z etykiety",
                                    fontWeight = if (hasNutrition) FontWeight.Normal else FontWeight.Bold,
                                    fontSize = if (hasNutrition) 14.sp else 18.sp,
                                    color = if (hasNutrition) Color.Gray else Color.Unspecified
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                result.extractedText.forEach { line ->
                                    Text(line, fontSize = 13.sp, modifier = Modifier.padding(vertical = 3.dp))
                                    HorizontalDivider(color = Color(0xFFF0F0F0))
                                }
                            } else if (result?.classificationStatus == "classification_failed") {
                                Text("Błąd odczytu etykiety po stronie serwera", fontSize = 14.sp, color = Color(0xFFD32F2F))
                                result.errorMessage?.let {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(it, fontSize = 12.sp, color = Color.Gray)
                                }
                            } else if (result?.classificationStatus == "completed") {
                                Text("Nie udało się odczytać tekstu z etykiety", fontSize = 14.sp, color = Color.Gray)
                            }
                        }
                    }

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

data class NutritionFacts(
    val calories: String? = null,
    val protein: String? = null,
    val fat: String? = null,
    val carbs: String? = null,
    val fiber: String? = null,
    val salt: String? = null
)

fun combineClassificationResults(results: List<ClassificationResult>): List<Pair<String, Double>> {
    val completed = results.filter { it.classificationStatus == "completed" && it.predictedClass != null }
    if (completed.isEmpty()) return emptyList()

    // Voting: sprawdź czy 2+ zdjęcia zgadzają się na ten sam #1
    val topVotes = completed.groupingBy { it.predictedClass!! }.eachCount()
    val majorityWinner = topVotes.entries.firstOrNull { it.value >= 2 }?.key

    // Średnia confidence dla każdej etykiety ze wszystkich zdjęć
    val confidenceMap = mutableMapOf<String, MutableList<Double>>()
    completed.forEach { result ->
        result.topPredictions.forEach { (label, conf) ->
            confidenceMap.getOrPut(label) { mutableListOf() }.add(conf)
        }
    }
    val averaged = confidenceMap.map { (label, confs) ->
        label to confs.average()
    }.sortedByDescending { it.second }

    // Jeśli jest wyraźny winner z votingu — idzie na pierwsze miejsce
    return if (majorityWinner != null) {
        val winnerConf = averaged.firstOrNull { it.first == majorityWinner }?.second
            ?: completed.filter { it.predictedClass == majorityWinner }
                .mapNotNull { it.confidence }.average()
        listOf(majorityWinner to winnerConf) + averaged.filter { it.first != majorityWinner }
    } else {
        averaged
    }
}

fun parseNutritionFromOcr(lines: List<String>): NutritionFacts {
    var calories: String? = null
    var protein: String? = null
    var fat: String? = null
    var carbs: String? = null
    var fiber: String? = null
    var salt: String? = null

    val kcalRegex = Regex("""(\d+[,.]?\d*)\s*kcal""", RegexOption.IGNORE_CASE)
    val gramRegex = Regex("""(\d+[,.]?\d*)\s*g\b""", RegexOption.IGNORE_CASE)

    lines.forEach { line ->
        val lower = line.lowercase()
        when {
            calories == null && (lower.contains("kcal") || lower.contains("energet")) -> {
                calories = kcalRegex.find(line)?.groupValues?.get(1)?.let { "$it kcal" }
                    ?: gramRegex.find(line)?.groupValues?.get(1)
            }
            protein == null && lower.contains("białko") -> {
                protein = gramRegex.find(line)?.groupValues?.get(1)?.let { "$it g" }
            }
            fat == null && lower.contains("tłuszcz")
                && !lower.contains("nasycone")
                && !lower.contains("jednonienasycone")
                && !lower.contains("wielonienasycone") -> {
                fat = gramRegex.find(line)?.groupValues?.get(1)?.let { "$it g" }
            }
            carbs == null && lower.contains("węglowodan") && !lower.contains("cukr") -> {
                carbs = gramRegex.find(line)?.groupValues?.get(1)?.let { "$it g" }
            }
            fiber == null && lower.contains("błonnik") -> {
                fiber = gramRegex.find(line)?.groupValues?.get(1)?.let { "$it g" }
            }
            salt == null && lower.contains("sól") -> {
                salt = gramRegex.find(line)?.groupValues?.get(1)?.let { "$it g" }
            }
        }
    }

    return NutritionFacts(calories, protein, fat, carbs, fiber, salt)
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