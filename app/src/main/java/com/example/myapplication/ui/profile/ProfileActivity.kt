package com.example.myapplication.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.R
import com.example.myapplication.ui.auth.LoginActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textview.MaterialTextView
import kotlinx.coroutines.launch

class ProfileActivity : AppCompatActivity() {
    
    private val viewModel: ProfileViewModel by viewModels()
    
    private lateinit var txtUserEmail: MaterialTextView
    private lateinit var edtName: TextInputEditText
    private lateinit var edtAge: TextInputEditText
    private lateinit var edtWeight: TextInputEditText
    private lateinit var edtHeight: TextInputEditText
    
    private lateinit var btnSave: MaterialButton
    private lateinit var btnLogout: MaterialButton
    private lateinit var btnDeleteAccount: MaterialButton
    
    private lateinit var loadingView: View
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        
        initViews()
        setupListeners()
        observeViewModel()
        
        viewModel.loadUserProfile()
    }
    
    private fun initViews() {
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        
        txtUserEmail = findViewById(R.id.txtUserEmail)
        edtName = findViewById(R.id.edtName)
        edtAge = findViewById(R.id.edtAge)
        edtWeight = findViewById(R.id.edtWeight)
        edtHeight = findViewById(R.id.edtHeight)
        
        btnSave = findViewById(R.id.btnSave)
        btnLogout = findViewById(R.id.btnLogout)
        btnDeleteAccount = findViewById(R.id.btnDeleteAccount)
        
        loadingView = findViewById(R.id.loadingView)
    }
    
    private fun setupListeners() {
        btnSave.setOnClickListener {
            saveProfile()
        }
        
        btnLogout.setOnClickListener {
            showLogoutConfirmation()
        }
        
        btnDeleteAccount.setOnClickListener {
            showDeleteAccountConfirmation()
        }
    }
    
    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.profileState.collect { state ->
                when (state) {
                    is ProfileState.Loading -> {
                        showLoading(true)
                    }
                    is ProfileState.Success -> {
                        showLoading(false)
                        displayProfile(state.profile)
                    }
                    is ProfileState.Updated -> {
                        showLoading(false)
                        Toast.makeText(this@ProfileActivity, "Cập nhật thành công!", Toast.LENGTH_SHORT).show()
                    }
                    is ProfileState.Error -> {
                        showLoading(false)
                        Toast.makeText(this@ProfileActivity, state.message, Toast.LENGTH_LONG).show()
                    }
                    is ProfileState.LoggedOut -> {
                        navigateToLogin()
                    }
                    is ProfileState.Idle -> {
                        showLoading(false)
                    }
                }
            }
        }
    }
    
    private fun displayProfile(profile: UserProfile) {
        txtUserEmail.text = profile.email
        edtName.setText(profile.name)
        edtAge.setText(profile.age.toString())
        edtWeight.setText(profile.weight.toString())
        edtHeight.setText(profile.height.toString())
    }
    
    private fun saveProfile() {
        val name = edtName.text.toString().trim()
        val age = edtAge.text.toString().toIntOrNull() ?: 0
        val weight = edtWeight.text.toString().toFloatOrNull() ?: 0f
        val height = edtHeight.text.toString().toFloatOrNull() ?: 0f
        
        if (name.isEmpty()) {
            Toast.makeText(this, "Tên không được để trống", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (age <= 0 || weight <= 0 || height <= 0) {
            Toast.makeText(this, "Thông tin không hợp lệ", Toast.LENGTH_SHORT).show()
            return
        }
        
        viewModel.updateProfile(name, age, weight, height)
    }
    
    private fun showLogoutConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Đăng xuất")
            .setMessage("Bạn có chắc muốn đăng xuất?")
            .setPositiveButton("Đăng xuất") { _, _ ->
                viewModel.logout()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }
    
    private fun showDeleteAccountConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Xóa tài khoản")
            .setMessage("CẢNH BÁO: Hành động này không thể hoàn tác!\n\nTất cả dữ liệu của bạn sẽ bị xóa vĩnh viễn.")
            .setPositiveButton("Xóa") { _, _ ->
                viewModel.deleteAccount()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }
    
    private fun showLoading(show: Boolean) {
        loadingView.visibility = if (show) View.VISIBLE else View.GONE
        btnSave.isEnabled = !show
    }
    
    private fun navigateToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
