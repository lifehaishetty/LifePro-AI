package com.lifepro.ai

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity() {

    // =========================================================================
    // SERVER URL CONFIGURATION:
    // - For Render cloud deployment: "https://your-service-name.onrender.com/api/chat"
    // - For Android Emulator talking to local PC: "http://10.0.2.2:3000/api/chat"
    // - For Real Phone on same Wi-Fi: "http://<YOUR_COMPUTER_IP>:3000/api/chat" (e.g. "http://192.168.1.15:3000/api/chat")
    // =========================================================================
    private val SERVER_URL = "http://10.0.2.2:3000/api/chat"

    private val httpClient = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etMessage = findViewById<EditText>(R.id.etMessage)
        val btnSend = findViewById<Button>(R.id.btnSend)
        val tvReply = findViewById<TextView>(R.id.tvReply)
        val tvAction = findViewById<TextView>(R.id.tvAction)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)

        btnSend.setOnClickListener {
            val userText = etMessage.text.toString().trim()
            if (userText.isNotEmpty()) {
                // Clear input box
                etMessage.text.clear()

                // Update UI state
                tvStatus.text = "Sending to Life Pro AI..."
                btnSend.isEnabled = false

                sendMessageToServer(userText, tvReply, tvAction, tvStatus, btnSend)
            }
        }
    }

    private fun sendMessageToServer(
        message: String,
        tvReply: TextView,
        tvAction: TextView,
        tvStatus: TextView,
        btnSend: Button
    ) {
        val jsonPayload = JSONObject().apply {
            put("message", message)
        }

        val requestBody = jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(SERVER_URL)
            .post(requestBody)
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    btnSend.isEnabled = true
                    tvStatus.text = "Connection failed."
                    tvReply.text = "Error connecting to server:\n${e.localizedMessage}\n\nCheck if server is running and SERVER_URL is correct."
                    tvAction.text = "[Action: error]"
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: ""
                runOnUiThread {
                    btnSend.isEnabled = true
                    tvStatus.text = "Reply received!"

                    try {
                        val json = JSONObject(responseBody)
                        val reply = json.optString("reply", "No reply received")
                        val action = json.optString("action", "none")
                        val contact = json.optString("contact", "")

                        tvReply.text = reply
                        tvAction.text = if (contact.isNotEmpty()) {
                            "[Action: $action -> $contact]"
                        } else {
                            "[Action: $action]"
                        }
                    } catch (e: Exception) {
                        tvReply.text = "Raw response:\n$responseBody"
                        tvAction.text = "[Action: none]"
                    }
                }
            }
        })
    }
}
