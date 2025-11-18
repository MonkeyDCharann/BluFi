package com.marwadiuniversity.blufi

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import java.io.FileInputStream
import android.bluetooth.BluetoothSocket
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.NonNull
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.*
import androidx.core.content.FileProvider
import java.text.SimpleDateFormat

// FIX 1: Define the SocketManager object.
object SocketManager {
    var activeSocket: BluetoothSocket? = null
}

class ChatActivity : AppCompatActivity(), SettingsSheetFragment.SettingsListener {

    // --- UI Views ---
    private lateinit var messageBox: EditText
    private lateinit var deviceNameTextView: TextView
    private lateinit var backButton: ImageButton
    private lateinit var sendButton: ImageButton
    private lateinit var menuButton: ImageView
    private lateinit var connectionStatus: TextView
    private lateinit var statusIcon: ImageView
    private lateinit var attachButton: ImageButton
    private lateinit var progressLayout: LinearLayout
    private lateinit var progressText: TextView
    private lateinit var fileProgressBar: ProgressBar
    private lateinit var recyclerView: RecyclerView
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var scrollToBottomButton: FloatingActionButton

    // --- Bluetooth Properties ---
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }
    private var bluetoothSocket: BluetoothSocket? = null
    private var bluetoothServerSocket: BluetoothServerSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var isConnected = false
    private var isServer = false
    private var listeningThread: Thread? = null
    private var serverThread: Thread? = null
    private var clientThread: Thread? = null

    // --- Chat State ---
    private var connectedDeviceName: String = "Friend"
    private var deviceAddress: String? = null
    private val chatMessages = mutableListOf<ChatMessage>()
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var db: ChatDatabase
    private val activityScope = CoroutineScope(Dispatchers.Main)

    companion object {
        private val MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val REQUEST_BLUETOOTH_PERMISSIONS = 100
    }

    override fun onConfigurationChanged(@NonNull newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        recreate()
    }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            if (isConnected) {
                sendFile(it)
            } else {
                Toast.makeText(this, "Cannot send file, not connected.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_chat)

        updateSystemBarsTheme()

        val rootView = findViewById<View>(R.id.root)
        val statusBarBackground = findViewById<View>(R.id.statusBarBackground)

        applySavedWallpaper()

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val systemBarsTop = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            statusBarBackground.layoutParams.height = systemBarsTop
            statusBarBackground.requestLayout()

            val systemBarsBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val bottomPadding = if (imeBottom > systemBarsBottom) imeBottom else systemBarsBottom

            view.updatePadding(bottom = bottomPadding)

            WindowInsetsCompat.CONSUMED
        }

        db = ChatDatabase.getDatabase(this)
        isServer = intent.getBooleanExtra("is_server", false)
        connectedDeviceName = intent.getStringExtra("device_name") ?: "Friend"
        deviceAddress = intent.getStringExtra("device_address")
        initViews()
        setupListeners()
        loadChatHistory()
        initializeBluetoothChat()
    }

    private fun applySavedWallpaper() {
        val colorResId = WallpaperPrefs.getWallpaperColor(this)
        if (colorResId != 0) {
            val color = ContextCompat.getColor(this, colorResId)
            val rootView = findViewById<View>(R.id.root)
            val chatRecyclerView = findViewById<RecyclerView>(R.id.chatRecyclerView)

            rootView.setBackgroundColor(color)
            chatRecyclerView.background = null
        }
    }

    override fun onWallpaperColorSelected(colorResId: Int) {
        WallpaperPrefs.saveWallpaperColor(this, colorResId)
        applySavedWallpaper()
    }

    private fun initViews() {
        messageBox = findViewById(R.id.messageBox)
        sendButton = findViewById(R.id.sendButton)
        menuButton = findViewById(R.id.menuButton)
        backButton = findViewById(R.id.backButton)
        deviceNameTextView = findViewById(R.id.deviceNameTextView)
        connectionStatus = findViewById(R.id.connectionStatus)
        statusIcon = findViewById(R.id.statusIcon)
        attachButton = findViewById(R.id.attachButton)
        progressLayout = findViewById(R.id.progressLayout)
        progressText = findViewById(R.id.progressText)
        fileProgressBar = findViewById(R.id.fileProgressBar)
        scrollToBottomButton = findViewById(R.id.scrollToBottomButton)

        recyclerView = findViewById(R.id.chatRecyclerView)

        chatAdapter = ChatAdapter(chatMessages, connectedDeviceName) { uri, mimeType ->
            openFile(uri, mimeType)
        }

        recyclerView.adapter = chatAdapter
        val layoutManager = LinearLayoutManager(this)
        recyclerView.layoutManager = layoutManager

        updateSendButtonState(false)
        updateConnectionStatus("Initializing...")

        deviceNameTextView.text = connectedDeviceName

        recyclerView = findViewById(R.id.chatRecyclerView)
    }

    private fun setupListeners() {
        backButton.setOnClickListener { finish() }
        sendButton.setOnClickListener { sendMessage() }
        menuButton.setOnClickListener { showMenuOptions() }
        attachButton.setOnClickListener { filePickerLauncher.launch("*/*") }
        scrollToBottomButton.setOnClickListener {
            // Note: chatMessages.size should be used here, even though adapter size is different,
            // as the adapter will scroll to the final position which corresponds to the last message.
            recyclerView.smoothScrollToPosition(chatAdapter.itemCount - 1)
        }

        messageBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateSendButtonState(!s.isNullOrBlank() && isConnected)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()
                val totalItemCount = layoutManager.itemCount

                if (totalItemCount - lastVisibleItemPosition > 3) {
                    scrollToBottomButton.show()
                } else {
                    scrollToBottomButton.hide()
                }
            }
        })
    }


    private fun loadChatHistory() {
        deviceAddress?.let { address ->
            activityScope.launch {
                val history = withContext(Dispatchers.IO) {
                    db.chatMessageDao().getMessagesForDevice(address)
                }
                chatMessages.addAll(history)
                updateChatView()
            }
        }
    }

    private fun refreshChatHistory() {
        chatMessages.clear()
        deviceAddress?.let { address ->
            activityScope.launch {
                val history = withContext(Dispatchers.IO) {
                    db.chatMessageDao().getMessagesForDevice(address)
                }
                chatMessages.addAll(history)
                updateChatView()
                recyclerView.scrollToPosition(chatAdapter.itemCount - 1)
            }
        }

        Toast.makeText(this, "Chat history refreshed", Toast.LENGTH_SHORT).show()
    }

    private fun addMessage(message: ChatMessage) {
        val wasAtBottom = isRecyclerAtBottom()
        chatMessages.add(message)
        updateChatView()
        if (wasAtBottom) {
            recyclerView.scrollToPosition(chatAdapter.itemCount - 1)
        }
        activityScope.launch(Dispatchers.IO) {
            db.chatMessageDao().insertMessage(message)
        }
    }

    private fun isRecyclerAtBottom(): Boolean {
        if (recyclerView.adapter == null || recyclerView.adapter!!.itemCount == 0) {
            return true
        }
        val layoutManager = recyclerView.layoutManager as LinearLayoutManager
        val lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()
        val totalItemCount = layoutManager.itemCount
        return lastVisibleItemPosition >= totalItemCount - 2
    }

    private fun addSystemMessage(text: String, isVisibleToUser: Boolean = false) {
        if (!isVisibleToUser) return
        deviceAddress?.let {
            val systemMessage = ChatMessage(
                remoteDeviceAddress = it,
                text = text,
                isUser = false,
                timestamp = System.currentTimeMillis(),
                isSystem = true
            )
            addMessage(systemMessage)
        }
    }

    private fun clearChat() {
        chatMessages.clear()
        updateChatView()
        deviceAddress?.let { address ->
            activityScope.launch(Dispatchers.IO) {
                db.chatMessageDao().clearChatForDevice(address)
            }
        }
        Toast.makeText(this, "Chat cleared", Toast.LENGTH_SHORT).show()
    }

    private fun initializeBluetoothChat() {
        addSystemMessage("Chat started...", isVisibleToUser = false)
        if (!hasBluetoothPermissions()) {
            requestBluetoothPermissions()
            return
        }
        addSystemMessage("Role: ${if (isServer) "Server" else "Client"}", isVisibleToUser = false)

        val existingSocket = SocketManager.activeSocket
        if (existingSocket != null && existingSocket.isConnected) {
            onConnectionEstablished(existingSocket)
            return
        }
        if (isServer) {
            startAsServer()
        } else if (deviceAddress != null) {
            connectToDevice(deviceAddress!!)
        } else {
            addSystemMessage("No device address provided!", isVisibleToUser = true)
            updateConnectionStatus("Error: No address")
        }
    }

    private fun handleTextMessage(text: String) {
        handler.post {
            deviceAddress?.let {
                val message = ChatMessage(
                    remoteDeviceAddress = it,
                    text = text,
                    isUser = false,
                    timestamp = System.currentTimeMillis()
                )
                addMessage(message)
            }
        }
    }

    private fun openFile(uri: Uri, mimeType: String?) {
        // --- 1. Handle Public Content URIs (Received Files) ---
        if (uri.scheme == "content") {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            } catch (e: Exception) {
                Log.e("ChatActivity", "Failed to open public content URI: ${e.message}")
                Toast.makeText(this, "Cannot open file: No application found for type $mimeType.", Toast.LENGTH_LONG).show()
            }
            return
        }

        // --- 2. Handle Private File URIs (Sent Files) via FileProvider ---
        val fileAuthority = "${applicationContext.packageName}.fileprovider"
        val fileToOpen: File?

        if (uri.scheme == "file" && uri.path != null) {
            val chatMessage = chatMessages.find { it.fileUri == uri.toString() }
            val savedFileName = chatMessage?.fileName

            if (savedFileName != null) {
                fileToOpen = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), savedFileName)
            } else {
                Log.e("ChatActivity", "Failed to retrieve filename for private file URI.")
                Toast.makeText(this, "File data corrupted (missing filename).", Toast.LENGTH_LONG).show()
                return
            }
        } else {
            Toast.makeText(this, "Unsupported file URI scheme: ${uri.scheme}", Toast.LENGTH_LONG).show()
            return
        }


        // --- 3. CONVERT PRIVATE FILE TO FILEPROVIDER URI AND OPEN ---

        if (fileToOpen.exists()) {
            try {
                val fileProviderUri = FileProvider.getUriForFile(
                    this,
                    fileAuthority,
                    fileToOpen
                )

                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(fileProviderUri, mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                startActivity(intent)
            } catch (e: IllegalArgumentException) {
                Log.e("ChatActivity", "FileProvider failed: Check file_paths.xml. ${e.message}")
                Toast.makeText(this, "File configuration error. Cannot open.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.e("ChatActivity", "Failed to open file via FileProvider: ${e.message}")
                Toast.makeText(this, "Cannot open file: No application found.", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "Error: File no longer exists on device storage.", Toast.LENGTH_LONG).show()
            Log.e("ChatActivity", "File not found at expected location: ${fileToOpen.absolutePath}")
        }
    }
    private fun sendMessage() {
        val messageText = messageBox.text.toString().trim()
        if (messageText.isNotEmpty() && isConnected) {
            Thread {
                try {
                    val dataToSend = messageText + "\n"
                    outputStream?.write(dataToSend.toByteArray())
                    outputStream?.flush()
                    handler.post {
                        deviceAddress?.let {
                            val userMessage = ChatMessage(
                                remoteDeviceAddress = it,
                                text = messageText,
                                isUser = true,
                                timestamp = System.currentTimeMillis()
                            )
                            addMessage(userMessage)
                            messageBox.text.clear()
                        }
                    }
                } catch (e: IOException) {
                    handleConnectionLost(e.message)
                }
            }.start()
        }
    }

    private fun sendFile(fileUri: Uri) {
        var fileName: String? = null
        var fileSize: Long = 0
        val mimeType = contentResolver.getType(fileUri)

        contentResolver.query(fileUri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                fileName = cursor.getString(nameIndex)
                fileSize = cursor.getLong(sizeIndex)
            }
        }

        if (fileName == null || fileSize == 0L || mimeType == null) {
            handler.post { Toast.makeText(this, "Error: Could not get file details.", Toast.LENGTH_SHORT).show() }
            return
        }

        // --- CRITICAL FIX START: Copy file to private storage (for stable sending) ---
        val newFile = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName!!)
        val newFileUri: Uri

        try {
            contentResolver.openInputStream(fileUri)?.use { input ->
                FileOutputStream(newFile).use { output ->
                    input.copyTo(output)
                }
            }
            newFileUri = Uri.fromFile(newFile)

        } catch (e: Exception) {
            Log.e("ChatActivity", "Failed to copy and stabilize sent file: ${e.message}")
            handler.post { Toast.makeText(this, "Failed to prepare file for sending.", Toast.LENGTH_LONG).show() }
            return
        }
        // --- CRITICAL FIX END ---

        deviceAddress?.let { address ->
            val fileMessage = ChatMessage(
                remoteDeviceAddress = address,
                fileUri = newFileUri.toString(),
                fileName = fileName,
                fileMimeType = mimeType,
                isUser = true,
                timestamp = System.currentTimeMillis()
            )
            handler.post { addMessage(fileMessage) }
        }

        Thread {
            try {
                if (!isConnected) {
                    handler.post {
                        Toast.makeText(this, "Connection lost before sending.", Toast.LENGTH_SHORT).show()
                    }
                    return@Thread
                }

                val header = JSONObject().apply {
                    put("type", "file_transfer")
                    put("fileName", fileName)
                    put("fileSize", fileSize)
                    put("mimeType", mimeType)
                }

                val headerWithDelimiter = header.toString() + "\n"
                outputStream?.write(headerWithDelimiter.toByteArray())
                outputStream?.flush()
                Thread.sleep(100)

                val fileInputStream = FileInputStream(newFile)
                val buffer = ByteArray(4096)
                var bytesRead: Int
                var bytesSent: Long = 0
                handler.post { updateProgress(true, "Sending $fileName...", 0) }
                while (fileInputStream.read(buffer).also { bytesRead = it } != -1) {
                    if (!isConnected) {
                        throw IOException("Disconnected during file transfer")
                    }
                    outputStream?.write(buffer, 0, bytesRead)
                    bytesSent += bytesRead
                    val progress = ((bytesSent * 100) / fileSize).toInt()
                    handler.post { updateProgress(true, "Sending $fileName...", progress) }
                }
                outputStream?.flush()
                fileInputStream.close()
                handler.post {
                    updateProgress(false)
                    addSystemMessage("You sent file: $fileName", isVisibleToUser = true)
                }
            } catch (e: Exception) {
                handleConnectionLost("Failed to send file.")
            }
        }.start()
    }

    private fun startListening() {
        listeningThread = Thread {
            try {
                val reader = BufferedReader(InputStreamReader(inputStream!!))

                while (isConnected) {
                    val receivedLine = reader.readLine()

                    if (receivedLine == null) {
                        throw IOException("Remote device disconnected.")
                    }

                    if (receivedLine.isNullOrEmpty()) continue

                    if (receivedLine.startsWith("{") && receivedLine.endsWith("}")) {
                        try {
                            val header = JSONObject(receivedLine)
                            if (header.getString("type") == "file_transfer") {
                                val fileName = header.getString("fileName")
                                val fileSize = header.getLong("fileSize")
                                val mimeType = header.getString("mimeType")

                                val fileOutputStream: OutputStream?
                                val fileUri: Uri?

                                @Suppress("DEPRECATION")
                                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    MediaStore.Downloads.EXTERNAL_CONTENT_URI
                                } else {
                                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                                }

                                val contentValues = ContentValues().apply {
                                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)

                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                                    }
                                }

                                val resolver = contentResolver
                                fileUri = resolver.insert(collection, contentValues)

                                if (fileUri == null) {
                                    handler.post { addSystemMessage("Error: Could not create MediaStore file.", isVisibleToUser = true) }
                                    continue
                                }

                                fileOutputStream = resolver.openOutputStream(fileUri)

                                if (fileOutputStream == null) {
                                    handler.post { addSystemMessage("Error: Could not open public file stream.", isVisibleToUser = true) }
                                    resolver.delete(fileUri, null, null)
                                    continue
                                }

                                var bytesReceived: Long = 0
                                val fileBuffer = ByteArray(4096)
                                while (bytesReceived < fileSize) {
                                    val remainingBytes = (fileSize - bytesReceived).toInt()
                                    val bytesToRead = minOf(fileBuffer.size, remainingBytes)
                                    val bytesRead = inputStream?.read(fileBuffer, 0, bytesToRead) ?: -1
                                    if (bytesRead == -1) {
                                        fileOutputStream.close()
                                        resolver.delete(fileUri, null, null)
                                        throw IOException("File stream ended prematurely.")
                                    }
                                    fileOutputStream.write(fileBuffer, 0, bytesRead)
                                    bytesReceived += bytesRead
                                    val progress = ((bytesReceived * 100) / fileSize).toInt()
                                    handler.post { updateProgress(true, "Receiving $fileName...", progress) }
                                }
                                fileOutputStream.close()

                                handler.post {
                                    updateProgress(false)
                                    val fileMessage = ChatMessage(
                                        remoteDeviceAddress = deviceAddress!!,
                                        text = null,
                                        isUser = false,
                                        timestamp = System.currentTimeMillis(),
                                        isSystem = false,
                                        fileUri = fileUri.toString(),
                                        fileName = fileName,
                                        fileMimeType = mimeType
                                    )
                                    addMessage(fileMessage)
                                }
                            } else {
                                handleTextMessage(receivedLine)
                            }
                        } catch (e: JSONException) {
                            handleTextMessage(receivedLine)
                        }
                    } else {
                        handleTextMessage(receivedLine)
                    }
                }
            } catch (e: IOException) {
                handleConnectionLost(e.message)
            }
        }
        listeningThread?.start()
    }

    private fun onConnectionEstablished(socket: BluetoothSocket) {
        bluetoothSocket = socket
        SocketManager.activeSocket = socket
        inputStream = socket.inputStream
        outputStream = socket.outputStream
        isConnected = true
        updateConnectionStatus("Connected")
        updateSendButtonState(true)
        startListening()
    }

    private fun handleConnectionLost(reason: String? = null) {
        handler.post {
            if (isConnected) {
                isConnected = false
                updateSendButtonState(false)
                val displayReason = reason?.let { ": $it" } ?: ""
                updateConnectionStatus("Disconnected")
            }

            Thread {
                try {
                    bluetoothSocket?.close()
                    bluetoothServerSocket?.close()
                } catch (e: IOException) {
                } finally {
                    bluetoothSocket = null
                    bluetoothServerSocket = null
                    inputStream = null
                    outputStream = null
                    SocketManager.activeSocket = null

                    listeningThread?.interrupt()
                    serverThread?.interrupt()
                    clientThread?.interrupt()
                }
            }.start()
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(deviceAddress: String) {
        disconnect()

        clientThread = Thread {
            try {
                val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
                var tempSocket: BluetoothSocket? = null
                tempSocket = device?.createRfcommSocketToServiceRecord(MY_UUID)

                tempSocket?.let { socket ->
                    bluetoothAdapter?.cancelDiscovery()
                    handler.post { updateConnectionStatus("Connecting...") }
                    socket.connect()
                    handler.post { onConnectionEstablished(socket) }
                }
            } catch (e: IOException) {
                handler.post { updateConnectionStatus("Connection Failed! Try Reconnecting...") }
                try { bluetoothSocket?.close() } catch (e: IOException) { /* ignore */ }
            } catch (e: SecurityException) {
                handler.post { updateConnectionStatus("Permission Denied") }
            }
        }
        clientThread?.start()
    }

    @SuppressLint("MissingPermission")
    private fun startAsServer() {
        disconnect()

        serverThread = Thread {
            try {
                bluetoothServerSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord("BluFiChat", MY_UUID)
                handler.post { updateConnectionStatus("Waiting for connection...") }

                val socket = bluetoothServerSocket?.accept()

                handler.post {
                    socket?.let { onConnectionEstablished(it) }
                }
            } catch (e: IOException) {
                handler.post { updateConnectionStatus("Server Failed: ${e.message}") }
                try { bluetoothServerSocket?.close() } catch (e: IOException) { /* ignore */ }
            } catch (e: SecurityException) {
                handler.post { updateConnectionStatus("Permission Denied") }
            }
        }
        serverThread?.start()
    }

    private fun disconnect() {
        if (!isConnected && bluetoothSocket == null && bluetoothServerSocket == null) return

        val wasConnected = isConnected
        isConnected = false
        handler.post {
            updateConnectionStatus("Disconnected")
            updateSendButtonState(false)
            if (wasConnected) {
                addSystemMessage("Disconnected manually.", isVisibleToUser = true)
            }
        }

        listeningThread?.interrupt()
        serverThread?.interrupt()
        clientThread?.interrupt()

        Thread {
            try {
                bluetoothSocket?.close()
                bluetoothServerSocket?.close()
            } catch (e: IOException) {
            } finally {
                bluetoothSocket = null
                bluetoothServerSocket = null
                inputStream = null
                outputStream = null
                SocketManager.activeSocket = null
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnect()
    }

    private fun updateProgress(show: Boolean, text: String = "", progress: Int = 0) {
        if (show) {
            progressLayout.visibility = View.VISIBLE
            progressText.text = "$text ($progress%)"
            fileProgressBar.progress = progress
        } else {
            progressLayout.visibility = View.GONE
        }
    }

    private fun updateChatView() {
        // FIX: Call the new update function on the adapter to recalculate date headers
        chatAdapter.updateMessages(chatMessages)
    }

    private fun updateSystemBarsTheme() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        windowInsetsController.isAppearanceLightStatusBars = !isNightMode
    }

    private fun updateSendButtonState(enabled: Boolean) {
        sendButton.isEnabled = enabled
        sendButton.alpha = if (enabled) 1.0f else 0.5f
    }

    private fun updateConnectionStatus(status: String) {
        connectionStatus.text = "Status: $status"
        val color: Int
        val icon: Int

        when (status) {
            "Connected" -> {
                color = getColor(R.color.status_connected)
                icon = R.drawable.ic_bluetooth_connected
            }
            "Disconnected", "Connection Failed", "Server Failed" -> {
                color = getColor(R.color.status_disconnected)
                icon = R.drawable.ic_bluetooth_disconnected
            }
            else -> {
                color = getColor(R.color.status_connecting)
                icon = R.drawable.ic_bluetooth_searching
            }
        }
        connectionStatus.setTextColor(color)
        statusIcon.setImageResource(icon)
        statusIcon.setColorFilter(color)
    }

    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) &&
                    (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
        } else {
            (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
        }
    }

    private fun requestBluetoothPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        ActivityCompat.requestPermissions(this, permissions, REQUEST_BLUETOOTH_PERMISSIONS)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            initializeBluetoothChat()
        } else {
            Toast.makeText(this, "Bluetooth permissions are required for chat.", Toast.LENGTH_LONG).show()
        }
    }

    private fun showMenuOptions() {
        val popup = PopupMenu(this, menuButton)
        popup.menuInflater.inflate(R.menu.chat_menu, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_refresh -> {
                    refreshChatHistory()
                    true
                }
                R.id.action_clear_chat -> {
                    AlertDialog.Builder(this)
                        .setTitle("Clear Chat")
                        .setMessage("Are you sure you want to permanently delete all messages in this chat?")
                        .setPositiveButton("Clear") { _, _ -> clearChat() }
                        .setNegativeButton("Cancel", null)
                        .show()
                    true
                }
                R.id.action_disconnect -> { disconnect(); true }
                R.id.action_reconnect -> { reconnect(); true }
                R.id.action_settings -> {
                    val settingsSheet = SettingsSheetFragment()
                    settingsSheet.show(supportFragmentManager, "SettingsSheetFragment")
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun reconnect() {
        if (isConnected) return
        if (isServer) {
            startAsServer()
        } else if (deviceAddress != null) {
            connectToDevice(deviceAddress!!)
        }
    }

    private fun showHelp() {
        val helpSheet = HelpBottomSheetFragment.newInstance()
        helpSheet.show(supportFragmentManager, HelpBottomSheetFragment.TAG)
    }

    private fun formatMessageDate(timestamp: Long): String {
        val messageCalendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        val nowCalendar = Calendar.getInstance()

        return if (messageCalendar.get(Calendar.YEAR) == nowCalendar.get(Calendar.YEAR) &&
            messageCalendar.get(Calendar.DAY_OF_YEAR) == nowCalendar.get(Calendar.DAY_OF_YEAR)) {
            "Today"
        } else {
            val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            dateFormat.format(Date(timestamp))
        }
    }
}