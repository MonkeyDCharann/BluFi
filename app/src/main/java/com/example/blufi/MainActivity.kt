package com.example.blufi

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Set the content view to your XML layout
        setContentView(R.layout.activity_main)

        // 2. Set up edge-to-edge display correctly
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val mainView = findViewById<View>(android.R.id.content) // Get the root view
        ViewCompat.setOnApplyWindowInsetsListener(mainView) { view, insets ->
            val systemBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = systemBarInsets.left,
                top = systemBarInsets.top,
                right = systemBarInsets.right,
                bottom = systemBarInsets.bottom
            )
            insets
        }

        // 3. Find the button and set its click listener
        val startChatButton = findViewById<Button>(R.id.startChatBtn)
        startChatButton.setOnClickListener {
            // Create an Intent to navigate to the Device_List activity
            val intent = Intent(this, Device_List::class.java)
            startActivity(intent)
        }
    }
}