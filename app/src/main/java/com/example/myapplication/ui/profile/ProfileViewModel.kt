package com.example.myapplication.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.api.BackendRepository
import com.example.myapplication.data.firebase.FirebaseAuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ProfileViewModel : ViewModel() {

    private val firebaseAuth = FirebaseAuthRepository()
    private val backendRepo = BackendRepository()

    private val _profileState = MutableStateFlow<ProfileState>(ProfileState.Idle)
    val profileState: StateFlow<ProfileState> = _profileState

    // ── Load profile ──────────────────────────────────────────────────────────

    fun loadUserProfile() {
        viewModelScope.launch {
            _profileState.value = ProfileState.Loading
            try {
                val result = backendRepo.getUserProfile()

                result.onSuccess { userData ->
                    val profile = UserProfile(
                        email = userData.email,
                        name = userData.name,
                        age = userData.age ?: 0,
                        weight = userData.weight ?: 0f,
                        height = userData.height ?: 0f
                    )
                    _profileState.value = ProfileState.Success(profile)
                }.onFailure { error ->
                    _profileState.value = ProfileState.Error("Không thể tải thông tin: ${error.message}")
                }
            } catch (e: Exception) {
                _profileState.value = ProfileState.Error(e.message ?: "Đã có lỗi xảy ra")
            }
        }
    }

    // ── Update profile ────────────────────────────────────────────────────────

    fun updateProfile(name: String, age: Int, weight: Float, height: Float) {
        viewModelScope.launch {
            _profileState.value = ProfileState.Loading
            try {
                val result = backendRepo.updateUserProfile(
                    name = name,
                    age = age,
                    weight = weight,
                    height = height
                )

                result.onSuccess {
                    _profileState.value = ProfileState.Updated
                }.onFailure { error ->
                    _profileState.value = ProfileState.Error("Cập nhật thất bại: ${error.message}")
                }
            } catch (e: Exception) {
                _profileState.value = ProfileState.Error(e.message ?: "Đã có lỗi xảy ra")
            }
        }
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    fun logout() {
        firebaseAuth.signOut()
        _profileState.value = ProfileState.LoggedOut
    }

    // ── Delete account ────────────────────────────────────────────────────────

    fun deleteAccount() {
        viewModelScope.launch {
            _profileState.value = ProfileState.Loading
            try {
                // Delete from backend first
                val backendResult = backendRepo.deleteUserAccount()

                // Then delete Firebase account
                val firebaseResult = firebaseAuth.deleteAccount()

                if (backendResult.isSuccess || firebaseResult.isSuccess) {
                    _profileState.value = ProfileState.LoggedOut
                } else {
                    _profileState.value = ProfileState.Error("Không thể xóa tài khoản")
                }
            } catch (e: Exception) {
                _profileState.value = ProfileState.Error(e.message ?: "Không thể xóa tài khoản")
            }
        }
    }
}