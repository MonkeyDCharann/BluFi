package com.example.blufi

import android.bluetooth.BluetoothClass
import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.widget.ScrollView
import androidx.annotation.NonNull
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

@SuppressLint("MissingPermission") // We handle permissions checking, so we can suppress this
class Device_List : AppCompatActivity() {

    private var bluetoothAdapter: BluetoothAdapter? = null
    private val paired = mutableListOf<BluetoothDevice>()
    private val discovered = mutableListOf<BluetoothDevice>()

    private lateinit var pairedAdapter: ArrayAdapter<String>
    private lateinit var discoveredAdapter: ArrayAdapter<String>
    private lateinit var progressBar: ProgressBar
    private lateinit var scanButton: Button

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var tvScanStatus: TextView

    private lateinit var tvPairedCount: TextView
    private lateinit var tvDiscoveredCount: TextView
    private lateinit var backButton: ImageButton

    // Flag to control discoverability request
    private var isInitialResume = true

    // Key for saving the flag state
    private val KEY_IS_INITIAL_RESUME = "key_is_initial_resume"

    companion object {
        private const val REQUEST_PERMISSIONS = 1001
        private const val REQUEST_ENABLE_BT = 1002
        private const val SCAN_PERIOD: Long = 15000
    }

    override fun onConfigurationChanged(@NonNull newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Force recreation to apply the new theme instantly
        recreate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. RESTORE STATE: Restore the flag from the saved instance state (if available)
        if (savedInstanceState != null) {
            isInitialResume = savedInstanceState.getBoolean(KEY_IS_INITIAL_RESUME, true)
        }

        // Set the content view BEFORE finding any views by ID
        setContentView(R.layout.activity_device_list)

        //  Now that the layout is set, you can safely find views and set up listeners
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val mainContent = findViewById<View>(R.id.main) // Use the root layout ID
        ViewCompat.setOnApplyWindowInsetsListener(mainContent) { view, insets ->
            val systemBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = systemBarInsets.left,
                top = systemBarInsets.top,
                right = systemBarInsets.right,
                bottom = systemBarInsets.bottom
            )
            insets
        }

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not supported on this device", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Initialize UI components here once
        initializeUI()
    }

    // 2. SAVE STATE: Save the flag before the Activity is destroyed
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_IS_INITIAL_RESUME, isInitialResume)
    }

    override fun onResume() {
        super.onResume()

        // Register receiver when the activity is resumed
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        registerReceiver(receiver, filter)

        // Check permissions and setup Bluetooth
        if (!hasPermissions()) {
            requestPermissions()
        } else {
            setupBluetooth()
        }
    }

    private fun isChattableDevice(device: BluetoothDevice): Boolean {
        val deviceClass = device.bluetoothClass.majorDeviceClass
        return when (deviceClass) {
            // Allow phones, computers, and uncategorized devices
            BluetoothClass.Device.Major.PHONE,
            BluetoothClass.Device.Major.COMPUTER,
            BluetoothClass.Device.Major.UNCATEGORIZED -> true
            // Reject all other types (audio, wearables, peripherals, etc.)
            else -> false
        }
    }

    override fun onPause() {
        super.onPause()
        // Unregister receiver when the activity is paused to prevent leaks and multiple triggers
        if (bluetoothAdapter?.isDiscovering == true) {
            bluetoothAdapter?.cancelDiscovery()
        }
        unregisterReceiver(receiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Final cleanup
        handler.removeCallbacksAndMessages(null)
    }

    private fun setupBluetooth() {
        if (bluetoothAdapter?.isEnabled == false) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        } else {
            // Only request discoverability on the very first resume OR the first resume after a proper launch
            if (isInitialResume) {
                requestDiscoverability()
                isInitialResume = false // Set the flag to false so it doesn't run again
            }
            checkLocationAndProceed()
        }
    }

    private fun checkLocationAndProceed() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                AlertDialog.Builder(this)
                    .setTitle("Location Services Required")
                    .setMessage("For Bluetooth scanning to work on this version of Android, you must enable Location services.")
                    .setPositiveButton("Go to Settings") { _, _ ->
                        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                        startActivity(intent)
                    }
                    .setNegativeButton("Cancel") { dialog, _ ->
                        dialog.dismiss()
                        Toast.makeText(this, "Location is required for scanning.", Toast.LENGTH_LONG).show()
                    }
                    .show()
                return
            }
        }
        // UI is already initialized, just load devices
        loadPairedDevices()
    }

    private fun initializeUI() {
        progressBar = findViewById(R.id.progressScanning)
        val pairedList = findViewById<ListView>(R.id.listPaired)
        val discoveredList = findViewById<ListView>(R.id.listDiscovered)
        scanButton = findViewById(R.id.btnScan)
        backButton = findViewById(R.id.backButton)

        backButton.setOnClickListener { finish() }

        tvPairedCount = findViewById(R.id.tvPairedCount)
        tvDiscoveredCount = findViewById(R.id.tvDiscoveredCount)
        tvScanStatus = findViewById(R.id.tvScanStatus)

        pairedAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1)
        discoveredAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1)
        pairedList.adapter = pairedAdapter
        discoveredList.adapter = discoveredAdapter

        scanButton.setOnClickListener {
            startDiscovery()
        }

        discoveredList.setOnItemClickListener { _, _, position, _ ->
            bluetoothAdapter?.cancelDiscovery()
            val device = discovered[position]
            Toast.makeText(this, "Pairing with ${device.name}...", Toast.LENGTH_SHORT).show()
            device.createBond()
        }

        pairedList.setOnItemClickListener { _, _, position, _ ->
            bluetoothAdapter?.cancelDiscovery()
            val device = paired[position]

            if (isChattableDevice(device)) {
                // It's a valid device, proceed to start the chat
                val myName = bluetoothAdapter?.name ?: ""
                val remoteName = device.name ?: ""

                if (myName.equals(remoteName, ignoreCase = true)) {
                    Toast.makeText(this, "Device names are Same! Please change your Bluetooth name and try again.", Toast.LENGTH_LONG).show()
                } else {
                    val isServer = myName.compareTo(remoteName) < 0
                    startChatActivity(device, isServer)
                }
            } else {
                // It's not a chattable device (like earbuds), so show a toast instead
                Toast.makeText(this, "'${device.name}' is not a device you can chat with.", Toast.LENGTH_SHORT).show()
            }
        }

        pairedList.setOnItemLongClickListener { _, _, position, _ ->
            val device = paired[position]
            showUnpairDialog(device)
            true
        }
    }

    private var isScanning = false

    private fun startDiscovery() {
        if (bluetoothAdapter?.isDiscovering == true) {
            bluetoothAdapter?.cancelDiscovery()
        }

        discovered.clear()
        discoveredAdapter.clear()
        updateDeviceCounts()

        progressBar.visibility = View.VISIBLE
        scanButton.isEnabled = false
        scanButton.text = "Scanning..."
        tvScanStatus.text = "Scanning for nearby devices..."
        isScanning = true

        bluetoothAdapter?.startDiscovery()

        handler.postDelayed({
            if (bluetoothAdapter?.isDiscovering == true) {
                bluetoothAdapter?.cancelDiscovery()
            }
        }, SCAN_PERIOD)
    }

    @SuppressLint("MissingPermission") // We check permissions before calling this
    private fun requestDiscoverability() {
        val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            // The device will be discoverable for 120 seconds. Max is 300.
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)
        }
        startActivity(discoverableIntent)
    }

    private fun loadPairedDevices() {
        paired.clear()
        pairedAdapter.clear()
        val bondedDevices = bluetoothAdapter?.bondedDevices
        bondedDevices?.forEach { device ->
            val deviceName = device.name ?: "Unknown Device"
            paired.add(device)
            pairedAdapter.add("$deviceName\n${device.address}")
        }
        updateDeviceCounts()
    }

    private fun updateDeviceCounts() {
        tvPairedCount.text = paired.size.toString()
        tvDiscoveredCount.text = discovered.size.toString()
    }

    private fun showUnpairDialog(device: BluetoothDevice) {
        AlertDialog.Builder(this)
            .setTitle("Unpair Device")
            .setMessage("Are you sure you want to unpair from ${device.name}?")
            .setPositiveButton("Unpair") { _, _ ->
                unpairDevice(device)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun unpairDevice(device: BluetoothDevice) {
        try {
            val method = device.javaClass.getMethod("removeBond")
            method.invoke(device)
            Toast.makeText(this, "Unpairing with ${device.name}", Toast.LENGTH_SHORT).show()
            Toast.makeText(this, "Unpaired with ${device.name}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to unpair device.", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun startChatActivity(device: BluetoothDevice, isServer: Boolean) {
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra("device_address", device.address)
            putExtra("device_name", device.name)
            putExtra("is_server", isServer)
        }
        startActivity(intent)
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }

                    Log.d("BluetoothScan", "Device found: ${device?.name ?: "No Name"} - ${device?.address}")

                    device?.let {
                        // Check if the device is a phone/computer before showing it
                        if (isChattableDevice(it)) {
                            // It's a valid device, add it to the list if it's not already there
                            if (!discovered.any { d -> d.address == it.address } &&
                                !paired.any { p -> p.address == it.address }) {
                                val deviceName = it.name ?: "Unnamed Device"
                                discovered.add(it)
                                discoveredAdapter.add("$deviceName\n${it.address}")
                                updateDeviceCounts()
                            }
                        } else {
                            // It's not a chattable device (e.g., headphones), show a toast
                            val deviceName = it.name ?: "Unsupported Device"
                            Toast.makeText(context, "'$deviceName' is not a device you can chat with.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    if (isScanning) {
                        isScanning = false
                        progressBar.visibility = View.GONE
                        scanButton.isEnabled = true
                        scanButton.text = "Start Scanning"
                        tvScanStatus.text = "Found ${discovered.size} new device/s."
                        Toast.makeText(context, "Scan finished.", Toast.LENGTH_SHORT).show()
                    }
                }

                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    loadPairedDevices()
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    if (device?.bondState == BluetoothDevice.BOND_BONDED) {
                        // Remove the newly paired device from the discovered list
                        discovered.removeAll { it.address == device.address }
                        discoveredAdapter.clear()
                        discovered.forEach { discoveredAdapter.add("${it.name}\n${it.address}") }
                        updateDeviceCounts()
                    }
                }
            }
        }
    }

    private fun hasPermissions(): Boolean {
        val hasFineLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12+
            val hasBluetoothScan = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            val hasBluetoothConnect = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            hasFineLocation && hasBluetoothScan && hasBluetoothConnect
        } else { // Android 11 and below
            val hasCoarseLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            hasFineLocation && hasCoarseLocation
        }
    }

    private fun requestPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
        ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSIONS)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // Permissions granted, onResume will handle the setup.
            } else {
                Toast.makeText(this, "All permissions are required to use this app.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode != RESULT_OK) {
                Toast.makeText(this, "Bluetooth must be enabled to continue.", Toast.LENGTH_LONG).show()
            }
        }
    }
}
