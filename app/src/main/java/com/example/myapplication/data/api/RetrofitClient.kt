package com.example.myapplication.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    
    // IMPORTANT: Change this to your computer's IP address
    // Find your IP: Windows: ipconfig | Mac/Linux: ifconfig
    // For localhost on emulator: use 10.0.2.2
    // For localhost on physical device: use your computer's IP (e.g., 192.168.1.100)

    
    // For physical device, use:
    private const val BASE_URL = "https://massagebe.onrender.com/api/"
    // Example: "http://192.168.1.100:3000/api/"
    
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }
    
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    val apiService: MassageApiService = retrofit.create(MassageApiService::class.java)
}
