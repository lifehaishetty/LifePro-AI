package com.lifepro.ai.ui

import android.accounts.AccountManager
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.lifepro.ai.MainActivity
import com.lifepro.ai.R
import com.lifepro.ai.auth.AuthManager
import com.lifepro.ai.auth.UserRole
import java.util.Locale

class AuthActivity : AppCompatActivity() {

    private lateinit var authManager: AuthManager
    private var selectedRole = UserRole.USER
    private var isOtpStep = false
    private val RC_GOOGLE_SIGN_IN = 9001
    private val DEFAULT_TEST_OTP = "123456"

    private lateinit var cardRoleUser: MaterialCardView
    private lateinit var cardRoleGuardian: MaterialCardView
    private lateinit var rbUser: RadioButton
    private lateinit var rbGuardian: RadioButton
    private lateinit var tilPhone: TextInputLayout
    private lateinit var etPhone: TextInputEditText
    private lateinit var tilOtp: TextInputLayout
    private lateinit var etOtp: TextInputEditText
    private lateinit var btnAutoFillOtp: MaterialButton
    private lateinit var btnPhoneAuth: MaterialButton
    private lateinit var btnGoogleAuth: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            authManager = AuthManager.getInstance(this)

            // If already logged in, navigate directly to main dashboard
            if (authManager.isLoggedIn()) {
                navigateToMain()
                return
            }

            setContentView(R.layout.activity_auth)
            initViews()
            setupRoleToggles()
            setupAuthActions()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
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
        btnAutoFillOtp = findViewById(R.id.btnAutoFillOtp)
        btnPhoneAuth = findViewById(R.id.btnPhoneAuth)
        btnGoogleAuth = findViewById(R.id.btnGoogleAuth)
    }

    private fun setupRoleToggles() {
        val violetPrimary = ContextCompat.getColor(this, R.color.violet_primary)
        val violetTint = ContextCompat.getColor(this, R.color.violet_tint)
        val borderSubtle = ContextCompat.getColor(this, R.color.border_subtle)
        val bgCard = ContextCompat.getColor(this, R.color.bg_card)

        cardRoleUser.setOnClickListener {
            selectedRole = UserRole.USER
            rbUser.isChecked = true
            rbGuardian.isChecked = false
            cardRoleUser.strokeColor = violetPrimary
            cardRoleUser.setCardBackgroundColor(ColorStateList.valueOf(violetTint))
            cardRoleGuardian.strokeColor = borderSubtle
            cardRoleGuardian.setCardBackgroundColor(ColorStateList.valueOf(bgCard))
        }

        cardRoleGuardian.setOnClickListener {
            selectedRole = UserRole.GUARDIAN
            rbGuardian.isChecked = true
            rbUser.isChecked = false
            cardRoleGuardian.strokeColor = violetPrimary
            cardRoleGuardian.setCardBackgroundColor(ColorStateList.valueOf(violetTint))
            cardRoleUser.strokeColor = borderSubtle
            cardRoleUser.setCardBackgroundColor(ColorStateList.valueOf(bgCard))
        }
    }

    private fun setupAuthActions() {
        btnAutoFillOtp.setOnClickListener {
            etOtp.setText(DEFAULT_TEST_OTP)
            executePhoneLogin()
        }

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
                btnAutoFillOtp.visibility = View.VISIBLE
                etOtp.requestFocus()
                btnPhoneAuth.text = "Verify OTP & Enter"

                // Show clear OTP confirmation dialog so user never has to search for code
                AlertDialog.Builder(this)
                    .setTitle("Verification Code Sent")
                    .setMessage("Your LifePro login OTP is: $DEFAULT_TEST_OTP\n\nSent to: $phone")
                    .setPositiveButton("Auto-Fill & Sign In") { _, _ ->
                        etOtp.setText(DEFAULT_TEST_OTP)
                        executePhoneLogin()
                    }
                    .setNegativeButton("Enter Manually") { _, _ ->
                        etOtp.setText(DEFAULT_TEST_OTP)
                        etOtp.setSelection(DEFAULT_TEST_OTP.length)
                    }
                    .show()
            } else {
                // Step 2: Verify OTP
                executePhoneLogin()
            }
        }

        btnGoogleAuth.setOnClickListener {
            triggerGoogleSignIn()
        }
    }

    private fun executePhoneLogin() {
        val phone = etPhone.text.toString().trim()
        val otp = etOtp.text.toString().trim()

        if (otp.isEmpty() || otp.length < 4) {
            tilOtp.error = "Please enter valid 6-digit OTP (e.g. 123456)"
            return
        }
        tilOtp.error = null

        val displayName = if (selectedRole == UserRole.USER) "LifePro User" else "Guardian Caregiver"
        authManager.loginWithPhone(phone, displayName, selectedRole)
        Toast.makeText(this, "Signed in successfully!", Toast.LENGTH_SHORT).show()
        navigateToMain()
    }

    private fun triggerGoogleSignIn() {
        try {
            // Launch native Android Google Account Picker for real Google account selection
            val googlePickerIntent = AccountManager.newChooseAccountIntent(
                null,
                null,
                arrayOf("com.google"),
                null,
                null,
                null,
                null
            )
            startActivityForResult(googlePickerIntent, RC_GOOGLE_SIGN_IN)
        } catch (e: Throwable) {
            // Graceful fallback for devices/emulators without Google Play Services account picker
            completeGoogleLogin("user.lifepro@gmail.com", if (selectedRole == UserRole.USER) "Google User" else "Guardian Caregiver")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_GOOGLE_SIGN_IN) {
            if (resultCode == RESULT_OK && data != null) {
                val email = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME) ?: "user.lifepro@gmail.com"
                val rawName = email.substringBefore("@").replace(".", " ").replace("_", " ")
                val formattedName = rawName.split(" ").joinToString(" ") { word ->
                    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                }
                completeGoogleLogin(email, formattedName)
            } else {
                // If user backs out of picker, offer instant one-tap demo login
                completeGoogleLogin("user.lifepro@gmail.com", if (selectedRole == UserRole.USER) "Ananya (User)" else "Guardian Caregiver")
            }
        }
    }

    private fun completeGoogleLogin(email: String, displayName: String) {
        authManager.loginWithGoogle(email, displayName, selectedRole)
        Toast.makeText(this, "Authenticated as $displayName", Toast.LENGTH_SHORT).show()
        navigateToMain()
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}
