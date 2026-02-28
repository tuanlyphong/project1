package com.example.myapplication.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.api.BackendRepository
import com.example.myapplication.data.firebase.FirebaseAuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {

    private val firebaseAuth = FirebaseAuthRepository()
    private val backendRepo = BackendRepository()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    // ── Check login ───────────────────────────────────────────────────────────

    fun isUserLoggedIn(): Boolean = firebaseAuth.isUserLoggedIn()

    // ── Login ─────────────────────────────────────────────────────────────────

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                // 1. Sign in with Firebase
                val result = firebaseAuth.signInWithEmail(email, password)

                result.onSuccess {
                    // 2. Verify with backend
                    val verifyResult = backendRepo.verifyFirebaseToken()

                    verifyResult.onSuccess {
                        _authState.value = AuthState.Success
                    }.onFailure { error ->
                        _authState.value = AuthState.Error("Đăng nhập thất bại: ${error.message}")
                    }
                }.onFailure { error ->
                    _authState.value = AuthState.Error(mapFirebaseError(error.message))
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(mapFirebaseError(e.message))
            }
        }
    }

    // ── Register ──────────────────────────────────────────────────────────────

    fun register(
        email: String,
        password: String,
        name: String,
        age: Int,
        weight: Float,
        height: Float
    ) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                // 1. Create Firebase account
                val result = firebaseAuth.signUpWithEmail(email, password)

                result.onSuccess {
                    // 2. Register user profile in backend
                    val registerResult = backendRepo.registerUser(
                        name = name,
                        age = age,
                        weight = weight,
                        height = height
                    )

                    registerResult.onSuccess {
                        _authState.value = AuthState.Success
                    }.onFailure {
                        // Firebase account created but backend failed - still proceed
                        _authState.value = AuthState.Success
                    }
                }.onFailure { error ->
                    _authState.value = AuthState.Error(mapFirebaseError(error.message))
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(mapFirebaseError(e.message))
            }
        }
    }

    // ── Reset password ────────────────────────────────────────────────────────

    fun resetPassword(email: String) {
        viewModelScope.launch {
            try {
                firebaseAuth.sendPasswordResetEmail(email)
            } catch (e: Exception) {
                _authState.value = AuthState.Error(mapFirebaseError(e.message))
            }
        }
    }

    // ── Reset state ───────────────────────────────────────────────────────────

    fun resetState() {
        _authState.value = AuthState.Idle
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private fun mapFirebaseError(message: String?): String {
        return when {
            message == null -> "Đã có lỗi xảy ra"
            message.contains("INVALID_EMAIL") || message.contains("invalid-email") ->
                "Email không hợp lệ"
            message.contains("WRONG_PASSWORD") || message.contains("wrong-password") ->
                "Mật khẩu không đúng"
            message.contains("USER_NOT_FOUND") || message.contains("user-not-found") ->
                "Tài khoản không tồn tại"
            message.contains("EMAIL_ALREADY_IN_USE") || message.contains("email-already-in-use") ->
                "Email đã được sử dụng"
            message.contains("WEAK_PASSWORD") || message.contains("weak-password") ->
                "Mật khẩu quá yếu"
            message.contains("NETWORK_ERROR") || message.contains("network") ->
                "Lỗi kết nối mạng"
            else -> message
        }
    }
}