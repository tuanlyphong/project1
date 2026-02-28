package com.example.myapplication.ui.profile

sealed class ProfileState {
    object Idle : ProfileState()
    object Loading : ProfileState()
    object Updated : ProfileState()
    object LoggedOut : ProfileState()
    data class Success(val profile: UserProfile) : ProfileState()
    data class Error(val message: String) : ProfileState()
}
