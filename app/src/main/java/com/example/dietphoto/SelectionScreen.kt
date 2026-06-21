// Ekran wyboru trybu — posiłek lub etykieta. Pierwszy ekran po zalogowaniu.
package com.example.dietphoto

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.LocalDining
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@Composable
fun SelectionScreen(
    onMealSelected: () -> Unit,
    onLabelSelected: () -> Unit,
    onLogout: () -> Unit
) {


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(34.dp))

            AuthStore.username?.let { name ->
                Text(
                    text = "Witaj, $name!",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            SelectionCard(
                title = "Zdjęcie posiłku",
                subtitle = "3 zdjęcia z różnych stron",
                icon = Icons.Default.LocalDining,
                gradient = Brush.linearGradient(
                    colors = listOf(Color(0xFF81C784), Color(0xFF388E3C))
                ),
                onClick = onMealSelected
            )

            Spacer(modifier = Modifier.height(24.dp))

            SelectionCard(
                title = "Zdjęcie etykiety",
                subtitle = "1 zdjęcie etykiety produktu",
                icon = Icons.AutoMirrored.Filled.Label,
                gradient = Brush.linearGradient(
                    colors = listOf(Color(0xB364B5F6), Color(0xB31565C0))
                ),
                onClick = onLabelSelected
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Guzik wylogowania — po Column, żeby był na wierzchu
        IconButton(
            onClick = onLogout,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 40.dp, start = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ExitToApp,
                contentDescription = "Wyloguj",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
fun SelectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    gradient: Brush,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(gradient)
            .clickable { onClick() }
            .padding(24.dp)
    ) {
        Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(40.dp)
            )
            Column {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp
                )
            }
        }
    }
}