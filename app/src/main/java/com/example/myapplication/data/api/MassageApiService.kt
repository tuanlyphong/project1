package com.example.myapplication.data.api

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit API Interface for Backend Communication
 * Base URL: http://YOUR_IP:3000/api
 */
interface MassageApiService {
    
    // ========== Authentication ==========
    
    @POST("auth/firebase-register")
    suspend fun registerUserWithFirebase(
        @Header("Authorization") token: String,
        @Body request: RegisterWithFirebaseRequest
    ): Response<AuthResponse>
    
    @POST("auth/verify-firebase")
    suspend fun verifyFirebaseToken(
        @Body request: FirebaseTokenRequest
    ): Response<AuthResponse>
    
    @GET("auth/me")
    suspend fun getCurrentUser(
        @Header("Authorization") token: String
    ): Response<UserResponse>
    
    @PUT("auth/profile")
    suspend fun updateUserProfile(
        @Header("Authorization") token: String,
        @Body request: UpdateProfileRequest
    ): Response<ApiResponse>
    
    @DELETE("auth/account")
    suspend fun deleteUserAccount(
        @Header("Authorization") token: String
    ): Response<ApiResponse>
    
    // ========== Massage Sessions ==========
    
    @POST("sessions")
    suspend fun saveMassageSession(
        @Header("Authorization") token: String,
        @Body session: MassageSessionRequest
    ): Response<ApiResponse>
    
    @GET("sessions")
    suspend fun getMassageSessions(
        @Header("Authorization") token: String,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<MassageSessionsResponse>
    
    @GET("sessions/{id}")
    suspend fun getSessionById(
        @Header("Authorization") token: String,
        @Path("id") sessionId: Int
    ): Response<MassageSessionResponse>
    
    @PUT("sessions/{id}")
    suspend fun updateSession(
        @Header("Authorization") token: String,
        @Path("id") sessionId: Int,
        @Body update: SessionUpdateRequest
    ): Response<ApiResponse>
    
    @DELETE("sessions/{id}")
    suspend fun deleteSession(
        @Header("Authorization") token: String,
        @Path("id") sessionId: Int
    ): Response<ApiResponse>
    
    @GET("sessions/statistics")
    suspend fun getSessionStatistics(
        @Header("Authorization") token: String,
        @Query("days") days: Int = 30
    ): Response<SessionStatisticsResponse>
    
    // ========== User Preferences ==========
    
    @GET("preferences")
    suspend fun getUserPreferences(
        @Header("Authorization") token: String
    ): Response<PreferencesResponse>
    
    @PUT("preferences")
    suspend fun updatePreferences(
        @Header("Authorization") token: String,
        @Body preferences: PreferencesUpdateRequest
    ): Response<ApiResponse>
    
    // ========== Analytics ==========
    
    @GET("analytics/summary")
    suspend fun getAnalyticsSummary(
        @Header("Authorization") token: String
    ): Response<AnalyticsSummaryResponse>
}

// ========== Request Models ==========

data class FirebaseTokenRequest(
    val firebaseToken: String
)

data class MassageSessionRequest(
    val level: Int,
    val duration: Int,
    val heatEnabled: Boolean,
    val rotateEnabled: Boolean,
    val caloriesBurned: Int,
    val notes: String = "",
    val startedAt: Long,
    val endedAt: Long
)

data class SessionUpdateRequest(
    val notes: String
)

data class PreferencesUpdateRequest(
    val favoriteLevel: Int? = null,
    val defaultDuration: Int? = null,
    val enableHeatByDefault: Boolean? = null,
    val enableNotifications: Boolean? = null,
    val notificationTime: String? = null,
    val theme: String? = null,
    val language: String? = null
)

// ========== Response Models ==========

data class ApiResponse(
    val success: Boolean,
    val message: String,
    val data: Any? = null
)

data class AuthResponse(
    val success: Boolean,
    val token: String,
    val user: UserData
)

data class UserResponse(
    val success: Boolean,
    val user: UserData
)

data class UserData(
    @SerializedName("user_id") val userId: String,
    @SerializedName("firebase_uid") val firebaseUid: String? = null,
    val email: String,
    val name: String,
    val age: Int? = null,
    val weight: Float? = null,
    val height: Float? = null,
    val gender: String? = null
)

data class MassageSessionsResponse(
    val success: Boolean,
    val count: Int,
    val sessions: List<MassageSessionData>
)

data class MassageSessionResponse(
    val success: Boolean,
    val session: MassageSessionData
)

data class MassageSessionData(
    val sessionId: Int,
    val level: Int,
    val duration: Int,
    val heatEnabled: Boolean,
    val rotateEnabled: Boolean,
    val caloriesBurned: Int,
    val notes: String,
    val startedAt: String,
    val endedAt: String
)

data class SessionStatisticsResponse(
    val success: Boolean,
    val statistics: SessionStatsData
)

data class SessionStatsData(
    val totalSessions: Int,
    val totalMinutes: Int,
    val totalCalories: Int,
    val avgLevel: Float,
    val heatUsagePercent: Int,
    val dateRange: Int
)

data class PreferencesResponse(
    val success: Boolean,
    val preferences: PreferencesData
)

data class PreferencesData(
    val favoriteLevel: Int,
    val defaultDuration: Int,
    val enableHeatByDefault: Boolean,
    val enableNotifications: Boolean,
    val notificationTime: String,
    val theme: String,
    val language: String
)

data class AnalyticsSummaryResponse(
    val success: Boolean,
    val summary: AnalyticsSummaryData
)

data class AnalyticsSummaryData(
    val totalSessions: Int,
    val avgHeartRate: Float,
    val avgSpO2: Float,
    val mostUsedLevel: Int,
    val totalMinutes: Int,
    val totalCalories: Int
)
