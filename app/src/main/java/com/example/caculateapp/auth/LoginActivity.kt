package com.example.caculateapp.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.example.caculateapp.HistoryActivity
import com.example.caculateapp.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch

/**
 * LoginActivity - First screen when app opens
 * Handles Google Sign-in flow
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var authManager: AuthManager

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            handleSignInResult(result.data)
        } else {
            hideLoading()
            Toast.makeText(this, "Đăng nhập bị hủy", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.root.updatePadding(
                top = systemBars.top,
                bottom = systemBars.bottom
            )
            insets
        }

        authManager = AuthManager(this)

        if (authManager.isSignedIn()) {
            navigateToHistory()
            return
        }

        binding.btnGoogleSignIn.setOnClickListener {
            signInWithGoogle()
        }
    }

    private fun signInWithGoogle() {
        showLoading()
        signInLauncher.launch(authManager.getSignInIntent())
    }

    private fun handleSignInResult(data: Intent?) {
        lifecycleScope.launch {
            when (val result = authManager.handleSignInResult(data)) {
                is AuthResult.Success -> {
                    hideLoading()
                    Toast.makeText(
                        this@LoginActivity,
                        "Xin chào, ${result.user.displayName}!",
                        Toast.LENGTH_SHORT
                    ).show()
                    navigateToHistory()
                }

                is AuthResult.Error -> {
                    hideLoading()
                    Toast.makeText(
                        this@LoginActivity,
                        "Đăng nhập thất bại: ${result.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun navigateToHistory() {
        startActivity(Intent(this, HistoryActivity::class.java))
        finish()
    }

    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnGoogleSignIn.isEnabled = false
        binding.btnGoogleSignIn.text = "Đang kết nối Google..."
    }

    private fun hideLoading() {
        binding.progressBar.visibility = View.GONE
        binding.btnGoogleSignIn.isEnabled = true
        binding.btnGoogleSignIn.text = "Tiếp tục với Google"
    }
}
