package com.example.caculateapp.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
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
    
    // Activity Result Launcher for Google Sign-in
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
        
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        authManager = AuthManager(this)
        
        // Check if already signed in
        if (authManager.isSignedIn()) {
            navigateToHistory()
            return
        }
        
        setupClickListeners()
    }
    
    private fun setupClickListeners() {
        binding.btnGoogleSignIn.setOnClickListener {
            signInWithGoogle()
        }
    }
    
    private fun signInWithGoogle() {
        showLoading()
        val signInIntent = authManager.getSignInIntent()
        signInLauncher.launch(signInIntent)
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
                    
                    // TODO: Check and perform migration from Room if needed
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
        val intent = Intent(this, HistoryActivity::class.java)
        startActivity(intent)
        finish()
    }
    
    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnGoogleSignIn.isEnabled = false
    }
    
    private fun hideLoading() {
        binding.progressBar.visibility = View.GONE
        binding.btnGoogleSignIn.isEnabled = true
    }
}
