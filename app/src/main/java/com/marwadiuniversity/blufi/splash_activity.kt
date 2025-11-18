package com.marwadiuniversity.blufi

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class splash_activity : AppCompatActivity() {

    // Define the duration for the splash screen in milliseconds
    private val SPLASH_DELAY_MS: Long = 1500 // 1.5 seconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Set the content view to your XML layout
        setContentView(R.layout.splash_activity)

        // Use a Handler to delay the launch of the next activity
        Handler(Looper.getMainLooper()).postDelayed({
            // 1. Create an Intent to start the next activity (Device_List is a good starting point)
            val intent = Intent(this, Device_List::class.java)

            // 2. Start the next activity
            startActivity(intent)

            // 3. Close this splash activity so the user can't navigate back to it
            finish()
        }, SPLASH_DELAY_MS)
    }
}