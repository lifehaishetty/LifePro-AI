package com.lifepro.ai.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.lifepro.ai.MainActivity
import com.lifepro.ai.R
import com.lifepro.ai.auth.AuthManager
import com.lifepro.ai.auth.UserRole

class AuthActivity : AppCompatActivity() {

    private lateinit var authManager: AuthManager
    private var selectedRole = UserRole.USER
    private var isOtpStep = false

    private lateinit var cardRoleUser: MaterialCardView
    private lateinit var cardRoleGuardian: MaterialCardView
    private lateinit var rbUser: RadioButton
    private lateinit var rbGuardian: RadioButton
    private lateinit var tilPhone: TextInputLayout
    private lateinit var etPhone: TextInputEditText
    private lateinit var tilOtp: TextInputLayout
    private lateinit var etOtp: TextInputEditText
    private lateinit var btnPhoneAuth: MaterialButton
    private lateinit var btnGoogleAuth: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authManager = AuthManager.getInstance(this)

        // If already logged in, navigate directly to main dashboard
        if (authManager.isLoggedIn()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_auth)
        initViews()
        setupRoleToggles()
        setupAuthActions()
    }

    private fun initViews() {
        cardRoleUser = findViewById(R.id.cardRoleUser)
        cardRoleGuardian = findViewById(R.id.cardRoleGuardian)
        rbUser = findViewById(R.id.rbUser)
        rbGuardian = findViewById(R.id.rbGuardian)
        tilPhone = findViewById(R.id.tilPhone)
        etPhone = findViewById(R.id.etPhone)
        tilOtp = findViewById(R.id.tilOtp)
        etOtp = findViewById(R.id.etOtp)
        btnPhoneAuth = findViewById(R.id.btnPhoneAuth)
        btnGoogleAuth = findViewById(R.id.btnGoogleAuth)
    }

    private fun setupRoleToggles() {
        cardRoleUser.setOnClickListener {
            selectedRole = UserRole.USER
            rbUser.isChecked = true
            rbGuardian.isChecked = false
            cardRoleUser.strokeColor = getColor(R.color.violet_primary)
            cardRoleUser.setCardBackgroundColor(getColor(R.color.violet_tint))
            cardRoleGuardian.strokeColor = getColor(R.color.border_subtle)
            cardRoleGuardian.setCardBackgroundColor(getColor(R.color.bg_card))
        }

        cardRoleGuardian.setOnClickListener {
            selectedRole = UserRole.GUARDIAN
            rbGuardian.isChecked = true
            rbUser.isChecked = false
            cardRoleGuardian.strokeColor = getColor(R.color.violet_primary)
            cardRoleGuardian.setCardBackgroundColor(getColor(R.color.violet_tint))
            cardRoleUser.strokeColor = getColor(R.color.border_subtle)
            cardRoleUser.setCardBackgroundColor(getColor(R.color.bg_card))
        }
    }

    private fun setupAuthActions() {
        btnPhoneAuth.setOnClickListener {
            val phone = etPhone.text.toString().trim()
            if (phone.isEmpty()) {
                tilPhone.error = "Please enter a valid phone number"
                return@setOnClickListener
            }
            tilPhone.error = null

            if (!isOtpStep) {
                // Step 1: Send OTP
                isOtpStep = true
                tilOtp.visibility = View.VISIBLE
                etOtp.requestFocus()
                btnPhoneAuth.text = "Verify OTP & Enter"
                Toast.makeText(this, "OTP sent: Use 123456 for testing", Toast.LENGTH_LONG).show()
            } else {
                // Step 2: Verify OTP
                val otp = etOtp.text.toString().trim()
                if (otp.length < 4) {
                    tilOtp.error = "Enter valid OTP"
                    return@setOnClickListener
                }
                tilOtp.error = null

                // Successful authentication
                val profile = authManager.loginWithPhone(phone, if (selectedRole == UserRole.USER) "LifePro User" else "Guardian", selectedRole)
                Toast.makeText(this, "Welcome to LifePro-AI", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }

        btnGoogleAuth.setOnClickListener {
            // Google Sign In Flow
            val profile = authManager.loginWithGoogle("user@lifepro.ai", if (selectedRole == UserRole.USER) "Ananya (User)" else "Guardian", selectedRole)
            Toast.makeText(this, "Authenticated with Google", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
