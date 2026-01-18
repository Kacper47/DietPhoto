package com.example.dietphoto

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

// Backend runs via nginx on host/port defined in bigos/.env (SERVER_HOST/SERVER_PORT).
const val BASE_URL = "http://192.168.0.206/api/"
// const val BASE_URL = "http://domena.pl/api/"

val httpClient: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .writeTimeout(60, TimeUnit.SECONDS)
    .build()
