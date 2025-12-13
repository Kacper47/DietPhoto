package com.example.dietphoto

// Modele danych (DTO)

// KROK 1: Presign - odpowiedź z serwera
data class PresignRequest(
    val extension: String = "jpg"
)

data class PresignResponse(
    val photo_id: String,
    val upload_url: String
)

// KROK 3: Confirm - żądanie do serwera
data class ConfirmRequest(
    val photo_id: String,
    val extension: String = "jpg",
    val width: Int,
    val height: Int
)