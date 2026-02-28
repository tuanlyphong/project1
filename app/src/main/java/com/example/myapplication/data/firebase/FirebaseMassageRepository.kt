package com.example.myapplication.data.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseMassageRepository {
    
    private val firestore = FirebaseFirestore.getInstance()
    private val authRepository = FirebaseAuthRepository()
    
    // Save massage session
    suspend fun saveMassageSession(session: MassageSession): Result<String> {
        return try {
            val userId = authRepository.getUserId()
                ?: return Result.failure(Exception("User not logged in"))
            
            val data = hashMapOf(
                "level" to session.level,
                "duration" to session.duration,
                "heatEnabled" to session.heatEnabled,
                "rotateEnabled" to session.rotateEnabled,
                "startTime" to session.startTime,
                "endTime" to session.endTime,
                "caloriesBurned" to session.caloriesBurned,
                "notes" to session.notes,
                "createdAt" to System.currentTimeMillis()
            )
            
            val docRef = firestore
                .collection("users")
                .document(userId)
                .collection("massageSessions")
                .add(data)
                .await()
            
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Get massage sessions (real-time)
    fun getMassageSessionsFlow(limit: Int = 50): Flow<List<MassageSession>> = callbackFlow {
        val userId = authRepository.getUserId()
        if (userId == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        
        val registration = firestore
            .collection("users")
            .document(userId)
            .collection("massageSessions")
            .orderBy("startTime", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                val sessions = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        MassageSession(
                            id = doc.id,
                            level = doc.getLong("level")?.toInt() ?: 0,
                            duration = doc.getLong("duration")?.toInt() ?: 0,
                            heatEnabled = doc.getBoolean("heatEnabled") ?: false,
                            rotateEnabled = doc.getBoolean("rotateEnabled") ?: false,
                            startTime = doc.getLong("startTime") ?: 0L,
                            endTime = doc.getLong("endTime") ?: 0L,
                            caloriesBurned = doc.getLong("caloriesBurned")?.toInt() ?: 0,
                            notes = doc.getString("notes") ?: ""
                        )
                    } catch (e: Exception) {
                        null
                    }
                } ?: emptyList()
                
                trySend(sessions)
            }
        
        awaitClose { registration.remove() }
    }
    
    // Get session by ID
    suspend fun getSessionById(sessionId: String): Result<MassageSession> {
        return try {
            val userId = authRepository.getUserId()
                ?: return Result.failure(Exception("User not logged in"))
            
            val doc = firestore
                .collection("users")
                .document(userId)
                .collection("massageSessions")
                .document(sessionId)
                .get()
                .await()
            
            if (!doc.exists()) {
                return Result.failure(Exception("Session not found"))
            }
            
            val session = MassageSession(
                id = doc.id,
                level = doc.getLong("level")?.toInt() ?: 0,
                duration = doc.getLong("duration")?.toInt() ?: 0,
                heatEnabled = doc.getBoolean("heatEnabled") ?: false,
                rotateEnabled = doc.getBoolean("rotateEnabled") ?: false,
                startTime = doc.getLong("startTime") ?: 0L,
                endTime = doc.getLong("endTime") ?: 0L,
                caloriesBurned = doc.getLong("caloriesBurned")?.toInt() ?: 0,
                notes = doc.getString("notes") ?: ""
            )
            
            Result.success(session)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Update session notes
    suspend fun updateSessionNotes(sessionId: String, notes: String): Result<Unit> {
        return try {
            val userId = authRepository.getUserId()
                ?: return Result.failure(Exception("User not logged in"))
            
            firestore
                .collection("users")
                .document(userId)
                .collection("massageSessions")
                .document(sessionId)
                .update("notes", notes)
                .await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Delete session
    suspend fun deleteSession(sessionId: String): Result<Unit> {
        return try {
            val userId = authRepository.getUserId()
                ?: return Result.failure(Exception("User not logged in"))
            
            firestore
                .collection("users")
                .document(userId)
                .collection("massageSessions")
                .document(sessionId)
                .delete()
                .await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Get session statistics
    suspend fun getSessionStatistics(days: Int = 30): Result<SessionStatistics> {
        return try {
            val userId = authRepository.getUserId()
                ?: return Result.failure(Exception("User not logged in"))
            
            val cutoffTime = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
            
            val snapshot = firestore
                .collection("users")
                .document(userId)
                .collection("massageSessions")
                .whereGreaterThan("startTime", cutoffTime)
                .get()
                .await()
            
            val sessions = snapshot.documents.mapNotNull { doc ->
                try {
                    MassageSession(
                        id = doc.id,
                        level = doc.getLong("level")?.toInt() ?: 0,
                        duration = doc.getLong("duration")?.toInt() ?: 0,
                        heatEnabled = doc.getBoolean("heatEnabled") ?: false,
                        rotateEnabled = doc.getBoolean("rotateEnabled") ?: false,
                        startTime = doc.getLong("startTime") ?: 0L,
                        endTime = doc.getLong("endTime") ?: 0L,
                        caloriesBurned = doc.getLong("caloriesBurned")?.toInt() ?: 0,
                        notes = doc.getString("notes") ?: ""
                    )
                } catch (e: Exception) {
                    null
                }
            }
            
            val totalSessions = sessions.size
            val totalMinutes = sessions.sumOf { it.duration }
            val totalCalories = sessions.sumOf { it.caloriesBurned }
            val avgLevel = if (sessions.isNotEmpty()) sessions.map { it.level }.average() else 0.0
            val heatUsagePercent = if (sessions.isNotEmpty()) {
                (sessions.count { it.heatEnabled }.toDouble() / sessions.size * 100).toInt()
            } else 0
            
            val stats = SessionStatistics(
                totalSessions = totalSessions,
                totalMinutes = totalMinutes,
                totalCalories = totalCalories,
                avgLevel = avgLevel.toInt(),
                heatUsagePercent = heatUsagePercent,
                dateRange = days
            )
            
            Result.success(stats)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

data class MassageSession(
    val id: String = "",
    val level: Int = 0,
    val duration: Int = 0,
    val heatEnabled: Boolean = false,
    val rotateEnabled: Boolean = false,
    val startTime: Long = 0L,
    val endTime: Long = 0L,
    val caloriesBurned: Int = 0,
    val notes: String = ""
)

data class SessionStatistics(
    val totalSessions: Int = 0,
    val totalMinutes: Int = 0,
    val totalCalories: Int = 0,
    val avgLevel: Int = 0,
    val heatUsagePercent: Int = 0,
    val dateRange: Int = 0
)
