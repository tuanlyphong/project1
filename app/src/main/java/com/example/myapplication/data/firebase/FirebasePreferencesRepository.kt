package com.example.myapplication.data.firebase

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebasePreferencesRepository {
    
    private val firestore = FirebaseFirestore.getInstance()
    private val authRepository = FirebaseAuthRepository()
    
    // Save user preferences
    suspend fun savePreferences(preferences: UserPreferences): Result<Unit> {
        return try {
            val userId = authRepository.getUserId()
                ?: return Result.failure(Exception("User not logged in"))
            
            val data = hashMapOf(
                "favoriteLevel" to preferences.favoriteLevel,
                "defaultDuration" to preferences.defaultDuration,
                "enableHeatByDefault" to preferences.enableHeatByDefault,
                "enableNotifications" to preferences.enableNotifications,
                "notificationTime" to preferences.notificationTime,
                "theme" to preferences.theme,
                "language" to preferences.language,
                "updatedAt" to System.currentTimeMillis()
            )
            
            firestore
                .collection("users")
                .document(userId)
                .collection("preferences")
                .document("settings")
                .set(data)
                .await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Get user preferences (real-time)
    fun getPreferencesFlow(): Flow<UserPreferences> = callbackFlow {
        val userId = authRepository.getUserId()
        if (userId == null) {
            trySend(UserPreferences())
            close()
            return@callbackFlow
        }
        
        val registration = firestore
            .collection("users")
            .document(userId)
            .collection("preferences")
            .document("settings")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                val preferences = if (snapshot?.exists() == true) {
                    try {
                        UserPreferences(
                            favoriteLevel = snapshot.getLong("favoriteLevel")?.toInt() ?: 3,
                            defaultDuration = snapshot.getLong("defaultDuration")?.toInt() ?: 15,
                            enableHeatByDefault = snapshot.getBoolean("enableHeatByDefault") ?: false,
                            enableNotifications = snapshot.getBoolean("enableNotifications") ?: true,
                            notificationTime = snapshot.getString("notificationTime") ?: "20:00",
                            theme = snapshot.getString("theme") ?: "light",
                            language = snapshot.getString("language") ?: "vi"
                        )
                    } catch (e: Exception) {
                        UserPreferences()
                    }
                } else {
                    UserPreferences()
                }
                
                trySend(preferences)
            }
        
        awaitClose { registration.remove() }
    }
    
    // Get user preferences (one-time)
    suspend fun getPreferences(): Result<UserPreferences> {
        return try {
            val userId = authRepository.getUserId()
                ?: return Result.failure(Exception("User not logged in"))
            
            val doc = firestore
                .collection("users")
                .document(userId)
                .collection("preferences")
                .document("settings")
                .get()
                .await()
            
            val preferences = if (doc.exists()) {
                UserPreferences(
                    favoriteLevel = doc.getLong("favoriteLevel")?.toInt() ?: 3,
                    defaultDuration = doc.getLong("defaultDuration")?.toInt() ?: 15,
                    enableHeatByDefault = doc.getBoolean("enableHeatByDefault") ?: false,
                    enableNotifications = doc.getBoolean("enableNotifications") ?: true,
                    notificationTime = doc.getString("notificationTime") ?: "20:00",
                    theme = doc.getString("theme") ?: "light",
                    language = doc.getString("language") ?: "vi"
                )
            } else {
                UserPreferences()
            }
            
            Result.success(preferences)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Update specific preference
    suspend fun updatePreference(key: String, value: Any): Result<Unit> {
        return try {
            val userId = authRepository.getUserId()
                ?: return Result.failure(Exception("User not logged in"))
            
            firestore
                .collection("users")
                .document(userId)
                .collection("preferences")
                .document("settings")
                .update(
                    key, value,
                    "updatedAt", System.currentTimeMillis()
                )
                .await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Save custom preset
    suspend fun saveCustomPreset(preset: CustomPreset): Result<String> {
        return try {
            val userId = authRepository.getUserId()
                ?: return Result.failure(Exception("User not logged in"))
            
            val data = hashMapOf(
                "name" to preset.name,
                "level" to preset.level,
                "duration" to preset.duration,
                "heatEnabled" to preset.heatEnabled,
                "createdAt" to System.currentTimeMillis()
            )
            
            val docRef = firestore
                .collection("users")
                .document(userId)
                .collection("customPresets")
                .add(data)
                .await()
            
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Get custom presets
    fun getCustomPresetsFlow(): Flow<List<CustomPreset>> = callbackFlow {
        val userId = authRepository.getUserId()
        if (userId == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        
        val registration = firestore
            .collection("users")
            .document(userId)
            .collection("customPresets")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                val presets = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        CustomPreset(
                            id = doc.id,
                            name = doc.getString("name") ?: "",
                            level = doc.getLong("level")?.toInt() ?: 3,
                            duration = doc.getLong("duration")?.toInt() ?: 15,
                            heatEnabled = doc.getBoolean("heatEnabled") ?: false
                        )
                    } catch (e: Exception) {
                        null
                    }
                } ?: emptyList()
                
                trySend(presets)
            }
        
        awaitClose { registration.remove() }
    }
    
    // Delete custom preset
    suspend fun deleteCustomPreset(presetId: String): Result<Unit> {
        return try {
            val userId = authRepository.getUserId()
                ?: return Result.failure(Exception("User not logged in"))
            
            firestore
                .collection("users")
                .document(userId)
                .collection("customPresets")
                .document(presetId)
                .delete()
                .await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

data class UserPreferences(
    val favoriteLevel: Int = 3,
    val defaultDuration: Int = 15,
    val enableHeatByDefault: Boolean = false,
    val enableNotifications: Boolean = true,
    val notificationTime: String = "20:00",
    val theme: String = "light", // "light", "dark", "auto"
    val language: String = "vi" // "vi", "en"
)

data class CustomPreset(
    val id: String = "",
    val name: String = "",
    val level: Int = 3,
    val duration: Int = 15,
    val heatEnabled: Boolean = false
)
