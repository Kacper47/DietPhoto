// Ekran logowania. Obsługuje formularz, walidację i wywołanie API logowania.
package com.example.dietphoto

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onLoginSuccess: (String, Int) -> Unit) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Zaloguj się", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Nazwa użytkownika") },
            singleLine = true,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Hasło") },
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
                if (!isNetworkAvailable(context)) {
                    error = "Brak połączenia z internetem. Włącz Wi-Fi lub dane."
                    return@Button
                }
                val trimmedUser = username.trim()
                if (trimmedUser.isBlank() || password.isBlank()) {
                    error = "Podaj nazwę użytkownika i hasło"
                    return@Button
                }
                isLoading = true
                error = null
                scope.launch {
                    try {
                        val (token, userId) = loginAndFetchUser(trimmedUser, password)
                        onLoginSuccess(token, userId)
                    } catch (e: Exception) {
                        error = when {
                            e.message?.contains("Incorrect username", ignoreCase = true) == true ->
                                "Błędna nazwa użytkownika lub hasło"
                            e.message?.contains("Failed to connect", ignoreCase = true) == true ->
                                "Nie można połączyć się z serwerem. Sprawdź IP i internet."
                            else -> "Wystąpił nieoczekiwany błąd: ${e.localizedMessage}"
                        }
                    } finally {
                        isLoading = false
                    }
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isLoading) "Logowanie..." else "Zaloguj się")
        }
    }
}