package com.example.myapplication.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.R
import com.example.myapplication.ui.main.MainActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textview.MaterialTextView
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private val viewModel: AuthViewModel by viewModels()

    private lateinit var edtEmail: TextInputEditText
    private lateinit var edtPassword: TextInputEditText
    private lateinit var edtName: TextInputEditText
    private lateinit var edtAge: TextInputEditText
    private lateinit var edtWeight: TextInputEditText
    private lateinit var edtHeight: TextInputEditText

    private lateinit var btnLogin: MaterialButton
    // btnRegister removed — btnLogin doubles as register button when isRegisterMode=true
    private lateinit var btnGoogleSignIn: MaterialButton
    private lateinit var btnToggleMode: MaterialTextView

    private lateinit var layoutRegisterFields: View
    private lateinit var loadingView: View

    private var isRegisterMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (viewModel.isUserLoggedIn()) {
            navigateToMain()
            return
        }

        setContentView(R.layout.activity_login)
        initViews()
        setupListeners()
        observeViewModel()
    }

    private fun initViews() {
        edtEmail = findViewById(R.id.edtEmail)
        edtPassword = findViewById(R.id.edtPassword)
        edtName = findViewById(R.id.edtName)
        edtAge = findViewById(R.id.edtAge)
        edtWeight = findViewById(R.id.edtWeight)
        edtHeight = findViewById(R.id.edtHeight)

        btnLogin = findViewById(R.id.btnLogin)
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn)
        btnToggleMode = findViewById(R.id.btnToggleMode)

        layoutRegisterFields = findViewById(R.id.layoutRegisterFields)
        loadingView = findViewById(R.id.loadingView)
    }

    private fun setupListeners() {
        btnLogin.setOnClickListener {
            if (isRegisterMode) register() else login()
        }

        btnGoogleSignIn.setOnClickListener {
            Toast.makeText(this, "Google Sign In - Coming soon", Toast.LENGTH_SHORT).show()
        }

        btnToggleMode.setOnClickListener { toggleMode() }

        findViewById<MaterialTextView>(R.id.btnForgotPassword).setOnClickListener {
            forgotPassword()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.authState.collect { state ->
                when (state) {
                    is AuthState.Loading -> showLoading(true)
                    is AuthState.Success -> {
                        showLoading(false)
                        Toast.makeText(this@LoginActivity, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show()
                        navigateToMain()
                    }
                    is AuthState.Error -> {
                        showLoading(false)
                        Toast.makeText(this@LoginActivity, state.message, Toast.LENGTH_LONG).show()
                    }
                    is AuthState.Idle -> showLoading(false)
                }
            }
        }
    }

    private fun login() {
        val email = edtEmail.text.toString().trim()
        val password = edtPassword.text.toString().trim()
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Vui lòng điền đầy đủ thông tin", Toast.LENGTH_SHORT).show()
            return
        }
        viewModel.login(email, password)
    }

    private fun register() {
        val email = edtEmail.text.toString().trim()
        val password = edtPassword.text.toString().trim()
        val name = edtName.text.toString().trim()
        val age = edtAge.text.toString().toIntOrNull() ?: 0
        val weight = edtWeight.text.toString().toFloatOrNull() ?: 0f
        val height = edtHeight.text.toString().toFloatOrNull() ?: 0f

        if (email.isEmpty() || password.isEmpty() || name.isEmpty()) {
            Toast.makeText(this, "Vui lòng điền đầy đủ thông tin", Toast.LENGTH_SHORT).show()
            return
        }
        if (password.length < 6) {
            Toast.makeText(this, "Mật khẩu phải có ít nhất 6 ký tự", Toast.LENGTH_SHORT).show()
            return
        }
        if (age <= 0 || weight <= 0 || height <= 0) {
            Toast.makeText(this, "Vui lòng nhập thông tin cơ thể hợp lệ", Toast.LENGTH_SHORT).show()
            return
        }
        viewModel.register(email, password, name, age, weight, height)
    }

    private fun forgotPassword() {
        val email = edtEmail.text.toString().trim()
        if (email.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập email", Toast.LENGTH_SHORT).show()
            return
        }
        viewModel.resetPassword(email)
        Toast.makeText(this, "Email khôi phục đã được gửi!", Toast.LENGTH_SHORT).show()
    }

    private fun toggleMode() {
        isRegisterMode = !isRegisterMode
        if (isRegisterMode) {
            layoutRegisterFields.visibility = View.VISIBLE
            btnLogin.text = "Đăng Ký"
            btnToggleMode.text = "Đã có tài khoản? Đăng nhập"
        } else {
            layoutRegisterFields.visibility = View.GONE
            btnLogin.text = "Đăng Nhập"
            btnToggleMode.text = "Chưa có tài khoản? Đăng ký"
        }
    }

    private fun showLoading(show: Boolean) {
        loadingView.visibility = if (show) View.VISIBLE else View.GONE
        btnLogin.isEnabled = !show
        btnGoogleSignIn.isEnabled = !show
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
