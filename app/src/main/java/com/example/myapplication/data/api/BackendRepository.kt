package com.example.myapplication.data.api

import android.util.Log
import com.example.myapplication.data.firebase.FirebaseAuthRepository
import kotlinx.coroutines.tasks.await

class BackendRepository {

    private val apiService = RetrofitClient.apiService
    private val authRepository = FirebaseAuthRepository()

    companion object {
        private const val TAG = "BackendRepository"
    }

    // Get authorization token from Firebase
    private suspend fun getAuthToken(): String? {
        val user = authRepository.getCurrentUser()
        return try {
            user?.getIdToken(false)?.await()?.token
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get Firebase token", e)
            null
        }
    }

    // Helper function to add Bearer prefix
    private fun bearerToken(token: String?): String = "Bearer ${token ?: ""}"

    // ========== Authentication ==========

    suspend fun registerUser(
        name: String,
        age: Int,
        weight: Float,
        height: Float,
        gender: String = "male"
    ): Result<Unit> {
        return try {
            val token = getAuthToken() ?: return Result.failure(Exception("Not authenticated"))
            
            val response = apiService.registerUserWithFirebase(
                bearerToken(token),
                RegisterWithFirebaseRequest(
                    name = name,
                    age = age,
                    weight = weight,
                    height = height,
                    gender = gender
                )
            )

            if (response.isSuccessful) {
                Log.d(TAG, "User registered in backend")
                Result.success(Unit)
            } else {
                Result.failure(Exception("Registration failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error registering user", e)
            Result.failure(e)
        }
    }

    suspend fun verifyFirebaseToken(): Result<AuthResponse> {
        return try {
            val token = getAuthToken() ?: return Result.failure(Exception("Not authenticated"))

            val request = FirebaseTokenRequest(firebaseToken = token)
            val response = apiService.verifyFirebaseToken(request)

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Token verification failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying Firebase token", e)
            Result.failure(e)
        }
    }

    // ========== User Profile ==========

    suspend fun getUserProfile(): Result<UserData> {
        return try {
            val token = getAuthToken() ?: return Result.failure(Exception("Not authenticated"))

            val response = apiService.getCurrentUser(bearerToken(token))

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.user)
            } else {
                Result.failure(Exception("Failed to get profile: ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user profile", e)
            Result.failure(e)
        }
    }

    suspend fun updateUserProfile(
        name: String,
        age: Int,
        weight: Float,
        height: Float
    ): Result<Unit> {
        return try {
            val token = getAuthToken() ?: return Result.failure(Exception("Not authenticated"))

            val response = apiService.updateUserProfile(
                bearerToken(token),
                UpdateProfileRequest(
                    name = name,
                    age = age,
                    weight = weight,
                    height = height
                )
            )

            if (response.isSuccessful) {
                Log.d(TAG, "Profile updated")
                Result.success(Unit)
            } else {
                Result.failure(Exception("Update failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating profile", e)
            Result.failure(e)
        }
    }

    suspend fun deleteUserAccount(): Result<Unit> {
        return try {
            val token = getAuthToken() ?: return Result.failure(Exception("Not authenticated"))

            val response = apiService.deleteUserAccount(bearerToken(token))

            if (response.isSuccessful) {
                Log.d(TAG, "Account deleted from backend")
                Result.success(Unit)
            } else {
                Result.failure(Exception("Delete failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting account", e)
            Result.failure(e)
        }
    }

    // ========== Massage Sessions ==========

    suspend fun saveMassageSession(
        level: Int,
        duration: Int,
        heatEnabled: Boolean,
        rotateEnabled: Boolean,
        startedAt: Long,
        endedAt: Long,
        caloriesBurned: Int = 0,
        notes: String = ""
    ): Result<Unit> {
        return try {
            val token = getAuthToken() ?: return Result.failure(Exception("Not authenticated"))

            val request = MassageSessionRequest(
                level = level,
                duration = duration,
                heatEnabled = heatEnabled,
                rotateEnabled = rotateEnabled,
                caloriesBurned = caloriesBurned,
                notes = notes,
                startedAt = startedAt,
                endedAt = endedAt
            )

            val response = apiService.saveMassageSession(bearerToken(token), request)

            if (response.isSuccessful) {
                Log.d(TAG, "Massage session saved to backend")
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to save session: ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving massage session", e)
            Result.failure(e)
        }
    }

    suspend fun getMassageSessions(limit: Int = 50): Result<List<MassageSessionData>> {
        return try {
            val token = getAuthToken() ?: return Result.failure(Exception("Not authenticated"))

            val response = apiService.getMassageSessions(bearerToken(token), limit)

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.sessions)
            } else {
                Result.failure(Exception("Failed to fetch sessions: ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching massage sessions", e)
            Result.failure(e)
        }
    }

    suspend fun getSessionStatistics(days: Int = 30): Result<SessionStatsData> {
        return try {
            val token = getAuthToken() ?: return Result.failure(Exception("Not authenticated"))

            val response = apiService.getSessionStatistics(bearerToken(token), days)

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.statistics)
            } else {
                Result.failure(Exception("Failed to fetch statistics: ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching session statistics", e)
            Result.failure(e)
        }
    }

    // ========== Preferences ==========

    suspend fun getUserPreferences(): Result<PreferencesData> {
        return try {
            val token = getAuthToken() ?: return Result.failure(Exception("Not authenticated"))

            val response = apiService.getUserPreferences(bearerToken(token))

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.preferences)
            } else {
                Result.failure(Exception("Failed to fetch preferences: ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching preferences", e)
            Result.failure(e)
        }
    }

    suspend fun updatePreferences(
        favoriteLevel: Int? = null,
        defaultDuration: Int? = null,
        enableHeatByDefault: Boolean? = null,
        enableNotifications: Boolean? = null,
        notificationTime: String? = null,
        theme: String? = null,
        language: String? = null
    ): Result<Unit> {
        return try {
            val token = getAuthToken() ?: return Result.failure(Exception("Not authenticated"))

            val request = PreferencesUpdateRequest(
                favoriteLevel = favoriteLevel,
                defaultDuration = defaultDuration,
                enableHeatByDefault = enableHeatByDefault,
                enableNotifications = enableNotifications,
                notificationTime = notificationTime,
                theme = theme,
                language = language
            )

            val response = apiService.updatePreferences(bearerToken(token), request)

            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to update preferences: ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating preferences", e)
            Result.failure(e)
        }
    }
}

// New request models
data class RegisterWithFirebaseRequest(
    val name: String,
    val age: Int,
    val weight: Float,
    val height: Float,
    val gender: String
)

data class UpdateProfileRequest(
    val name: String,
    val age: Int,
    val weight: Float,
    val height: Float
)
