package com.example.dietphoto

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Url

interface ApiService {
    // 1. Pobranie linku do uploadu
    @POST("photos/presign")
    suspend fun getPresignUrl(@Body request: PresignRequest): Response<PresignResponse>

    // 2. Wysłanie samego pliku (binarnie) na otrzymany link
    @PUT
    suspend fun uploadImageBinary(@Url url: String, @Body image: RequestBody): Response<ResponseBody>

    // 3. Potwierdzenie zakończenia wysyłania
    @POST("photos/confirm")
    suspend fun confirmUpload(@Body request: ConfirmRequest): Response<ResponseBody>
}