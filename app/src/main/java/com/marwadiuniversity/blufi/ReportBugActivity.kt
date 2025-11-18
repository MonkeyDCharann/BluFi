package com.marwadiuniversity.blufi

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Assuming this import is correctly configured in your Gradle/environment

class ReportBugActivity : AppCompatActivity() {

    private lateinit var etSubject: EditText
    private lateinit var etDescription: EditText
    private lateinit var btnAddScreenshot: ImageButton
    private lateinit var ivScreenshotPreview: ImageView
    private lateinit var btnSendFeedback: Button
    private lateinit var tvHelpCenter: TextView
    private lateinit var tvLearnMore: TextView

    private var screenshotUri: Uri? = null

    // Removed requestPermissionLauncher (GetContent doesn't need storage permissions on modern Android)

    // REPLACED: Launcher for selecting an image from storage (Gallery, Files)
    private val selectImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // Since this URI might be temporary, we need to make a persistent copy
            // to ensure the email client can read it later (ACTION_SEND requirement).
            copyUriToCache(it)?.let { cachedUri ->
                screenshotUri = cachedUri
                ivScreenshotPreview.setImageURI(cachedUri)
                ivScreenshotPreview.visibility = View.VISIBLE
                btnAddScreenshot.visibility = View.GONE
            } ?: run {
                Toast.makeText(this, "Failed to prepare image for attachment.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Removed takeScreenshotLauncher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report_bug)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        etSubject = findViewById(R.id.et_subject)
        etDescription = findViewById(R.id.et_description)
        btnAddScreenshot = findViewById(R.id.btn_add_screenshot)
        ivScreenshotPreview = findViewById(R.id.iv_screenshot_preview)
        btnSendFeedback = findViewById(R.id.btn_send_feedback)
        tvHelpCenter = findViewById(R.id.tv_help_center)
        tvLearnMore = findViewById(R.id.tv_learn_more)

        setupListeners()
        updateSendButtonState() // Initial check
    }

    private fun setupListeners() {
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateSendButtonState()
            }
        }

        etSubject.addTextChangedListener(textWatcher)
        etDescription.addTextChangedListener(textWatcher)

        btnAddScreenshot.setOnClickListener {
            // NEW: Launch the image selector instead of checking permissions/camera
            selectImageLauncher.launch("image/*")
        }

        ivScreenshotPreview.setOnClickListener {
            // Option to remove or view screenshot
            AlertDialog.Builder(this)
                .setTitle("Image Options")
                .setItems(arrayOf("View Image", "Remove Image")) { _, which ->
                    when (which) {
                        0 -> viewScreenshot()
                        1 -> removeScreenshot()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        btnSendFeedback.setOnClickListener {
            sendFeedbackEmail()
        }

        tvHelpCenter.setOnClickListener {
            openUrl("https://support.whatsapp.com/")
        }

        tvLearnMore.setOnClickListener {
            openUrl("https://www.google.com/search?q=app+privacy+policy+feedback+data")
        }
    }

    private fun updateSendButtonState() {
        btnSendFeedback.isEnabled = etSubject.text.isNotBlank() || etDescription.text.isNotBlank()
    }

    // Removed checkStoragePermissionAndTakeScreenshot() and takeScreenshotAndProceed()

    /**
     * Copies the content from a received content URI (from GetContent) into a private cache file.
     * This is necessary because the original URI might expire or be inaccessible to the email app.
     */
    private fun copyUriToCache(originalUri: Uri): Uri? {
        val mimeType = contentResolver.getType(originalUri) ?: "image/jpeg"
        val extension = when {
            mimeType.endsWith("png") -> "png"
            mimeType.endsWith("gif") -> "gif"
            else -> "jpg"
        }
        val fileName = "attachment_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.$extension"
        val cachePath = File(cacheDir, "attachments")
        cachePath.mkdirs()

        val file = File(cachePath, fileName)

        return try {
            contentResolver.openInputStream(originalUri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            // Return the stable, accessible FileProvider URI
            FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        } catch (e: Exception) {
            Log.e("ReportBugActivity", "Error copying image to cache", e)
            null
        }
    }

    // Removed getScreenShotFromView and simplified saveBitmapToFile (since we use copyUriToCache now)
    private fun saveBitmapToFile(bitmap: Bitmap): Uri? {
        // This function is now redundant but kept as a placeholder if needed
        return null
    }


    private fun viewScreenshot() {
        screenshotUri?.let { uri ->
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "image/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "No app found to view image.", Toast.LENGTH_SHORT).show()
            }
        } ?: Toast.makeText(this, "No image attached.", Toast.LENGTH_SHORT).show()
    }

    private fun removeScreenshot() {
        screenshotUri = null
        ivScreenshotPreview.visibility = View.GONE
        btnAddScreenshot.visibility = View.VISIBLE
        // Note: Actual file deletion from cache should be added here for a cleaner app
    }

    private fun sendFeedbackEmail() {
        val recipientEmail = "devicharandasari019@gmail.com"
        val subject = "[Bug Report] BluFi App: ${etSubject.text.toString().trim()}"
        val description = etDescription.text.toString().trim()

        val emailBody = StringBuilder()
        emailBody.append("Device: ${Build.MANUFACTURER} ${Build.MODEL}\n")
        emailBody.append("Android Version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")

        // Using BuildConfig (assuming you will fix the compilation issue via Gradle sync/rebuild)
        emailBody.append("App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n\n")

        emailBody.append("Bug Description:\n$description\n\n")
        emailBody.append("--- End of Report ---")

        val finalIntent = Intent(Intent.ACTION_SEND).apply {
            // *** FIX 1: Set Gmail as the target package ***
            setPackage("com.google.android.gm")

            putExtra(Intent.EXTRA_EMAIL, arrayOf(recipientEmail))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, emailBody.toString())

            if (screenshotUri != null) {
                type = "image/png" // Using image/png since we copied it to cache as such
                putExtra(Intent.EXTRA_STREAM, screenshotUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                type = "text/plain"
            }
        }


        if (finalIntent.resolveActivity(packageManager) != null) {
            startActivity(finalIntent)
            finish()
        } else {
            // Fallback for if Gmail is not installed
            Toast.makeText(this, "Gmail app not found. Please install Gmail or use another email client.", Toast.LENGTH_LONG).show()
        }
    }

    private fun openUrl(url: String) {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(browserIntent)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}