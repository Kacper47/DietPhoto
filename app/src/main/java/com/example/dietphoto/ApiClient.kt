package com.example.dietphoto

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

const val BASE_URL = "http://192.168.x.x/api/"

val httpClient: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .writeTimeout(60, TimeUnit.SECONDS)
    .build()
