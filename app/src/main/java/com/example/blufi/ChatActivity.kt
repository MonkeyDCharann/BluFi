package com.example.blufi

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

// FIX 1: Define the SocketManager object. I'm assuming it should be a simple object
// to hold the static reference across activities/launches.
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
        // Call recreate() to destroy and immediately restart the activity.
        // This forces the system to apply the new theme resources instantly.
        recreate()
    }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // Permission for a file being SENT isn't usually needed, as we copy it internally.
            // Removing the persistence attempt here.

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

        // This is still needed to make the status bar icons light/dark
        updateSystemBarsTheme()

        val rootView = findViewById<View>(R.id.root)
        val statusBarBackground = findViewById<View>(R.id.statusBarBackground)

        applySavedWallpaper()

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            // Get the height of the system status bar
            val systemBarsTop = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top

            // Set our spacer view's height to match the status bar height
            statusBarBackground.layoutParams.height = systemBarsTop
            statusBarBackground.requestLayout()

            // Handle the keyboard and navigation bar at the bottom
            val systemBarsBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val bottomPadding = if (imeBottom > systemBarsBottom) imeBottom else systemBarsBottom

            // Apply ONLY bottom padding to the root view for the keyboard/nav bar
            view.updatePadding(bottom = bottomPadding)

            WindowInsetsCompat.CONSUMED
        }

        // --- The rest of your setup code remains the same ---
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
            // Make RecyclerView transparent to see the root background
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

        // FIX: Provide the required lambda function (onFileClick) to the ChatAdapter constructor
        chatAdapter = ChatAdapter(chatMessages, connectedDeviceName) { uri, mimeType ->
            openFile(uri, mimeType)
        }

        recyclerView.adapter = chatAdapter
        val layoutManager = LinearLayoutManager(this)
        recyclerView.layoutManager = layoutManager

        updateSendButtonState(false)
        updateConnectionStatus("Initializing...")

        // Set the device name in the header
        deviceNameTextView.text = connectedDeviceName

        recyclerView = findViewById(R.id.chatRecyclerView)
    }

    private fun setupListeners() {
        backButton.setOnClickListener { finish() }
        sendButton.setOnClickListener { sendMessage() }
        menuButton.setOnClickListener { showMenuOptions() }
        attachButton.setOnClickListener { filePickerLauncher.launch("*/*") }
        scrollToBottomButton.setOnClickListener {
            recyclerView.smoothScrollToPosition(chatMessages.size - 1)
        }

        messageBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Connection status check is now handled by handleConnectionLost
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

    // New function for refreshing chat history (Option 1)
    private fun refreshChatHistory() {
        // Clear the current list in memory
        chatMessages.clear()

        // Reload all messages from the database for the connected device
        deviceAddress?.let { address ->
            activityScope.launch {
                val history = withContext(Dispatchers.IO) {
                    db.chatMessageDao().getMessagesForDevice(address)
                }
                chatMessages.addAll(history)
                updateChatView() // Notifies the adapter and updates the RecyclerView
                recyclerView.scrollToPosition(chatMessages.size - 1) // Scroll to the latest message
            }
        }

        Toast.makeText(this, "Chat history refreshed", Toast.LENGTH_SHORT).show()
    }

    private fun addMessage(message: ChatMessage) {
        val wasAtBottom = isRecyclerAtBottom()
        chatMessages.add(message)
        updateChatView()
        if (wasAtBottom) {
            recyclerView.scrollToPosition(chatMessages.size - 1)
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

        // FIX 2 & 3: Fix Unresolved references to SocketManager and isConnected, and the type mismatch.
        // We must check our local 'isConnected' and the 'activeSocket' from the newly defined object.
        val existingSocket = SocketManager.activeSocket
        if (existingSocket != null && existingSocket.isConnected) { // BluetoothSocket.isConnected is available in the API but often not reliable
            // We use the local 'isConnected' for state management. When a socket is established, we set isConnected = true.
            // When connection is lost (IOException), we set isConnected = false.
            // Since we rely on the state being managed, we trust the existing socket is good.
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

    /**
     * Opens a file given its URI and MIME type.
     * It handles two cases:
     * 1. Public content:// URIs (for received files from MediaStore).
     * 2. Private file:// URIs (for sent files stored in app's external files dir, opened via FileProvider).
     */
    private fun openFile(uri: Uri, mimeType: String?) {
        // --- 1. Handle Public Content URIs (Received Files) ---
        if (uri.scheme == "content") {
            try {
                // Public content URIs can often be opened directly as the OS handles permissions.
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    // Grant temporary permission to the viewing app (essential for content URIs)
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
        // This logic is for files that were copied to getExternalFilesDir() when sending.
        val fileAuthority = "${applicationContext.packageName}.fileprovider"
        val fileToOpen: File?

        // Check if the URI is a simple file:// path (This is what is stored for sent files)
        if (uri.scheme == "file" && uri.path != null) {
            // Rebuild the path to the file in the app's private external folder.
            val chatMessage = chatMessages.find { it.fileUri == uri.toString() }
            val savedFileName = chatMessage?.fileName

            if (savedFileName != null) {
                // Rebuild the path to the file in the app's private external folder.
                fileToOpen = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), savedFileName)
            } else {
                Log.e("ChatActivity", "Failed to retrieve filename for private file URI.")
                Toast.makeText(this, "File data corrupted (missing filename).", Toast.LENGTH_LONG).show()
                return
            }
        } else {
            // URI is neither public content nor private file.
            Toast.makeText(this, "Unsupported file URI scheme: ${uri.scheme}", Toast.LENGTH_LONG).show()
            return
        }


        // --- 3. CONVERT PRIVATE FILE TO FILEPROVIDER URI AND OPEN ---

        if (fileToOpen.exists()) {
            try {
                // Generate the secure content:// URI via FileProvider
                val fileProviderUri = FileProvider.getUriForFile(
                    this,
                    fileAuthority,
                    fileToOpen
                )

                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(fileProviderUri, mimeType)
                    // VITAL: Grant temporary permission to the viewing app
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                startActivity(intent)
            } catch (e: IllegalArgumentException) {
                // This happens if the file path is not correctly defined in file_paths.xml
                Log.e("ChatActivity", "FileProvider failed: Check file_paths.xml. ${e.message}")
                Toast.makeText(this, "File configuration error. Cannot open.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.e("ChatActivity", "Failed to open file via FileProvider: ${e.message}")
                Toast.makeText(this, "Cannot open file: No application found.", Toast.LENGTH_LONG).show()
            }
        } else {
            // This indicates the file was physically deleted from storage
            Toast.makeText(this, "Error: File no longer exists on device storage.", Toast.LENGTH_LONG).show()
            Log.e("ChatActivity", "File not found at expected location: ${fileToOpen.absolutePath}")
        }
    }
    private fun sendMessage() {
        val messageText = messageBox.text.toString().trim()
        if (messageText.isNotEmpty() && isConnected) {
            Thread {
                try {
                    // FIX: Append a newline character (\n) as a delimiter for the receiver's BufferedReader.
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
                    // CRITICAL FIX: If writing fails, the connection is broken. Handle it immediately.
                    handleConnectionLost(e.message)
                }
            }.start()
        }
    }

    private fun sendFile(fileUri: Uri) {
        // These variables need to be declared and initialized before they are used.
        var fileName: String? = null
        var fileSize: Long = 0
        val mimeType = contentResolver.getType(fileUri)

        // Get file details on the main thread
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
        // We still save the SENT file privately so the user can open it later even if the original app that provided the URI is gone.
        val newFile = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName!!)
        val newFileUri: Uri

        try {
            // 1. Copy the content from the temporary external URI (fileUri) to the new stable file location
            contentResolver.openInputStream(fileUri)?.use { input ->
                FileOutputStream(newFile).use { output ->
                    input.copyTo(output)
                }
            }

            // 2. Use the stable file:// URI of the newly created file for the database
            // This URI's path is now stable and accessible via FileProvider.
            newFileUri = Uri.fromFile(newFile)

        } catch (e: Exception) {
            Log.e("ChatActivity", "Failed to copy and stabilize sent file: ${e.message}")
            handler.post { Toast.makeText(this, "Failed to prepare file for sending.", Toast.LENGTH_LONG).show() }
            return
        }
        // --- CRITICAL FIX END ---

        // Now, create the ChatMessage object using the stable newFileUri.
        deviceAddress?.let { address ->
            val fileMessage = ChatMessage(
                remoteDeviceAddress = address,
                fileUri = newFileUri.toString(), // Use the stable PRIVATE file:// URI
                fileName = fileName,
                fileMimeType = mimeType,
                isUser = true,
                timestamp = System.currentTimeMillis()
            )
            // Add the message to the list and update the UI
            handler.post { addMessage(fileMessage) }
        }

        // The rest of the file sending logic runs on a background thread.
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

                // FIX: Send the header string followed by a newline delimiter (\n)
                val headerWithDelimiter = header.toString() + "\n"
                outputStream?.write(headerWithDelimiter.toByteArray())
                outputStream?.flush()
                Thread.sleep(100)

                // Use the stable file path for the Bluetooth input stream
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
                // CRITICAL FIX: If writing fails during file transfer, the connection is broken.
                handleConnectionLost("Failed to send file.")
            }
        }.start()
    }

    private fun startListening() {
        listeningThread = Thread {
            // Use a BufferedReader to easily read newline-terminated strings (messages or headers)
            try {
                val reader = BufferedReader(InputStreamReader(inputStream!!))

                while (isConnected) {
                    val receivedLine = reader.readLine()

                    if (receivedLine == null) {
                        throw IOException("Remote device disconnected.")
                    }

                    if (receivedLine.isNullOrEmpty()) continue

                    // 1. Check if the received line is a JSON file transfer header
                    if (receivedLine.startsWith("{") && receivedLine.endsWith("}")) {
                        try {
                            val header = JSONObject(receivedLine)
                            if (header.getString("type") == "file_transfer") {
                                // --- File Transfer Logic ---
                                val fileName = header.getString("fileName")
                                val fileSize = header.getLong("fileSize")
                                val mimeType = header.getString("mimeType")

                                // >>> START PUBLIC STORAGE FIX: Use MediaStore for public access <<<
                                val fileOutputStream: OutputStream?
                                val fileUri: Uri?

                                // 1. Determine the correct MediaStore collection based on API level
                                @Suppress("DEPRECATION")
                                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    MediaStore.Downloads.EXTERNAL_CONTENT_URI
                                } else {
                                    // Use the generic external storage URI for older versions (requires WRITE_EXTERNAL_STORAGE permission)
                                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                                    // Note: While Media.EXTERNAL_CONTENT_URI is typically for images,
                                    // it is the most reliable cross-API-level public collection URI fallback.
                                }

                                // 2. Create ContentValues for the MediaStore entry
                                val contentValues = ContentValues().apply {
                                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)

                                    // Set the target directory to DOWNLOADS for public access (API 29+)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                                    } else {
                                        // For older APIs, the file will be saved to the root of external storage
                                        // or a directory specified in the path, but we rely on MediaStore to handle it.
                                    }
                                }

                                // 3. Insert a new entry into MediaStore and get the public content:// URI
                                val resolver = contentResolver
                                fileUri = resolver.insert(collection, contentValues)

                                if (fileUri == null) {
                                    handler.post { addSystemMessage("Error: Could not create MediaStore file.", isVisibleToUser = true) }
                                    continue
                                }

                                // 4. Open the stream to the public storage location
                                fileOutputStream = resolver.openOutputStream(fileUri)

                                if (fileOutputStream == null) {
                                    handler.post { addSystemMessage("Error: Could not open public file stream.", isVisibleToUser = true) }
                                    resolver.delete(fileUri, null, null) // Clean up partial MediaStore entry
                                    continue
                                }
                                // >>> END PUBLIC STORAGE FIX <<<

                                var bytesReceived: Long = 0
                                val fileBuffer = ByteArray(4096)
                                while (bytesReceived < fileSize) {
                                    val remainingBytes = (fileSize - bytesReceived).toInt()
                                    val bytesToRead = minOf(fileBuffer.size, remainingBytes)
                                    val bytesRead = inputStream?.read(fileBuffer, 0, bytesToRead) ?: -1
                                    if (bytesRead == -1) {
                                        // Handle premature end of stream during transfer
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
                                        fileUri = fileUri.toString(), // The public content:// URI stored in DB
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
        // FIX 4: Use the newly defined SocketManager object
        SocketManager.activeSocket = socket
        inputStream = socket.inputStream
        outputStream = socket.outputStream
        isConnected = true
        updateConnectionStatus("Connected")
        updateSendButtonState(true)
        startListening()
    }

    // CRITICAL FIX: Centralized and safer connection loss handler
    private fun handleConnectionLost(reason: String? = null) {
        handler.post {
            // Only perform cleanup and status update if we were previously connected
            if (isConnected) {
                isConnected = false
                updateSendButtonState(false)

                // Immediately update status before attempting socket closure
                val displayReason = reason?.let { ": $it" } ?: ""
                updateConnectionStatus("Disconnected")
//                addSystemMessage("Connection lost$displayReason. Attempting cleanup...", isVisibleToUser = true)
            }

            // Initiate cleanup regardless of previous state to ensure all resources are closed.
            // Run cleanup on a separate thread to prevent blocking the UI/handler thread.
            Thread {
                try {
                    bluetoothSocket?.close()
                    bluetoothServerSocket?.close()
                } catch (e: IOException) {
                    // Ignore, since we are already dealing with a broken connection
                } finally {
                    // Nullify all references for garbage collection and reconnection attempts
                    bluetoothSocket = null
                    bluetoothServerSocket = null
                    inputStream = null
                    outputStream = null
                    // FIX 5: Use the newly defined SocketManager object
                    SocketManager.activeSocket = null

                    // Stop any existing threads
                    listeningThread?.interrupt()
                    serverThread?.interrupt()
                    clientThread?.interrupt()
                }
            }.start()
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(deviceAddress: String) {
        // First, ensure any existing connection is properly closed before attempting a new one
        disconnect()

        clientThread = Thread {
            try {
                val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
                // Use a temporary socket variable in case the thread is interrupted
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
                // Immediately close the failed socket
                try { bluetoothSocket?.close() } catch (e: IOException) { /* ignore */ }
            } catch (e: SecurityException) {
                handler.post { updateConnectionStatus("Permission Denied") }
            }
        }
        clientThread?.start()
    }

    @SuppressLint("MissingPermission")
    private fun startAsServer() {
        // First, ensure any existing connection is properly closed before attempting to listen
        disconnect()

        serverThread = Thread {
            try {
                bluetoothServerSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord("BluFiChat", MY_UUID)
                handler.post { updateConnectionStatus("Waiting for connection...") }

                // .accept() will block until connection or close() is called on the server socket
                val socket = bluetoothServerSocket?.accept()

                // If the socket is non-null, connection succeeded
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

    // FIX: Simplified and more effective disconnect
    private fun disconnect() {
        if (!isConnected && bluetoothSocket == null && bluetoothServerSocket == null) return

        // 1. Immediately update state and UI
        val wasConnected = isConnected
        isConnected = false
        handler.post {
            updateConnectionStatus("Disconnected")
            updateSendButtonState(false)
            if (wasConnected) {
                addSystemMessage("Disconnected manually.", isVisibleToUser = true)
            }
        }

        // 2. Stop running threads to prevent further I/O operations
        listeningThread?.interrupt()
        serverThread?.interrupt()
        clientThread?.interrupt()

        // 3. Close the sockets on a background thread to prevent blocking the UI
        Thread {
            try {
                bluetoothSocket?.close()
                bluetoothServerSocket?.close()
            } catch (e: IOException) {
                // Ignore failure to close an already broken socket
            } finally {
                // 4. Clean up references
                bluetoothSocket = null
                bluetoothServerSocket = null
                inputStream = null
                outputStream = null
                // FIX 6: Use the newly defined SocketManager object
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
        // This notifies the adapter that the data set has changed.
        chatAdapter.notifyDataSetChanged()
    }

    private fun updateSystemBarsTheme() {
        // Get the modern window insets controller
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)

        // Check if the system is currently in night mode (dark theme)
        val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        // Set the status bar icons to be light or dark.
        // isAppearanceLightStatusBars = true means icons are dark (for light themes)
        // isAppearanceLightStatusBars = false means icons are light (for dark themes)
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
        statusIcon.setColorFilter(color) // Ensures the icon also takes the color
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
                R.id.action_refresh -> { // Handle the new Refresh option
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
        // Create an instance of your new BottomSheetFragment
        val helpSheet = HelpBottomSheetFragment.newInstance()

        // Show it
        helpSheet.show(supportFragmentManager, HelpBottomSheetFragment.TAG)
    }
}