package com.example.jci_andriod12

import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.net.wifi.WifiManager
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class FullAdminActivity : ComponentActivity() {
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, KioskDeviceAdminReceiver::class.java)
        
        // Handle back button to return to kiosk
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
        
        setupLayout()
    }
    
    private fun setupLayout() {
        val scrollView = android.widget.ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#1a1a2e"))
        }
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(24), dpToPx(32), dpToPx(24), dpToPx(32))
        }

        // Header
        val title = TextView(this).apply {
            text = "Admin Panel"
            textSize = 32f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        
        val pkgInfo = packageManager.getPackageInfo(packageName, 0)
        val updateDate = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
            .format(java.util.Date(pkgInfo.lastUpdateTime))
        val versionInfo = TextView(this).apply {
            text = "v${pkgInfo.versionName} • $updateDate"
            textSize = 14f
            setTextColor(Color.parseColor("#888888"))
            gravity = Gravity.CENTER
        }

        layout.addView(title, createLayoutParams(dpToPx(4)))
        layout.addView(versionInfo, createLayoutParams(dpToPx(32)))

        // Configuration Section
        layout.addView(createSectionHeader("Configuration"))
        layout.addView(createModernButton("⚙️  General Settings", "#3D5AFE") { showSettingsDialog() })
        layout.addView(createModernButton("📶  WiFi Settings", "#3D5AFE") { showWifiDialog() })
        layout.addView(createModernButton("🔐  Change Password", "#3D5AFE") { showPasswordChangeDialog() })
        
        // Updates Section
        layout.addView(createSectionHeader("Updates"))
        layout.addView(createModernButton("🔄  Check for Updates", "#00C853") { checkForUpdates() })
        layout.addView(createModernButton("⏪  Rollback Version", "#FF9800") { showRollbackDialog() })
        
        // System Section
        layout.addView(createSectionHeader("System"))
        layout.addView(createModernButton("🔃  Restart Application", "#607D8B") { restartApp() })
        layout.addView(createModernButton("🚪  Exit Kiosk Mode", "#F44336") { exitKioskMode() })
        
        // Return button at bottom
        val returnBtn = Button(this).apply {
            text = "Return to Kiosk"
            textSize = 18f
            setTextColor(Color.WHITE)
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dpToPx(12).toFloat()
                setColor(Color.parseColor("#4FC3F7"))
            }
            setPadding(dpToPx(24), dpToPx(18), dpToPx(24), dpToPx(18))
            setOnClickListener { finish() }
        }
        layout.addView(returnBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, dpToPx(32), 0, 0) })

        scrollView.addView(layout)
        setContentView(scrollView)
    }
    
    private fun createSectionHeader(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(Color.parseColor("#888888"))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dpToPx(4), dpToPx(24), 0, dpToPx(12))
        }
    }
    
    private fun createModernButton(text: String, color: String, action: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dpToPx(12).toFloat()
                setColor(Color.parseColor(color))
            }
            setPadding(dpToPx(20), dpToPx(18), dpToPx(20), dpToPx(18))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dpToPx(12)) }
            setOnClickListener { action() }
        }
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 
            dp.toFloat(), 
            resources.displayMetrics
        ).toInt()
    }

    
    private fun createLayoutParams(margin: Int): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, margin) }
    }
    
    private fun exitKioskMode() {
        if (devicePolicyManager.isDeviceOwnerApp(packageName)) {
            try { stopLockTask() } catch (_: Exception) { }
            try { devicePolicyManager.setStatusBarDisabled(adminComponent, false) } catch (_: Exception) { }
            try { devicePolicyManager.setKeyguardDisabled(adminComponent, false) } catch (_: Exception) { }
            try { devicePolicyManager.clearPackagePersistentPreferredActivities(adminComponent, packageName) } catch (_: Exception) { }
            try { devicePolicyManager.clearDeviceOwnerApp(packageName) } catch (_: Exception) { }
        }
        finishAffinity()
        System.exit(0)
    }
    
    private fun restartApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
    
    private fun checkForUpdates() {
        Toast.makeText(this, "Checking for updates...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val updateManager = UpdateManager(this@FullAdminActivity)
            when (val result = updateManager.checkForUpdate()) {
                is UpdateManager.UpdateResult.Available -> {
                    val update = result.info
                    AlertDialog.Builder(this@FullAdminActivity)
                        .setTitle("Update Available")
                        .setMessage("Version ${update.versionName} is available. Install now?")
                        .setPositiveButton("Install") { _, _ ->
                            Toast.makeText(this@FullAdminActivity, "Downloading update...", Toast.LENGTH_SHORT).show()
                            lifecycleScope.launch {
                                val success = updateManager.downloadAndInstall(update)
                                if (!success) {
                                    Toast.makeText(this@FullAdminActivity, "Update failed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        .setNegativeButton("Later", null)
                        .show()
                }
                is UpdateManager.UpdateResult.UpToDate -> {
                    Toast.makeText(this@FullAdminActivity, "App is up to date", Toast.LENGTH_SHORT).show()
                }
                is UpdateManager.UpdateResult.NoInternet -> {
                    AlertDialog.Builder(this@FullAdminActivity)
                        .setTitle("No Internet Connection")
                        .setMessage("Cannot check for updates. Please connect to WiFi first.")
                        .setPositiveButton("WiFi Settings") { _, _ -> showWifiDialog() }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
                is UpdateManager.UpdateResult.Error -> {
                    Toast.makeText(this@FullAdminActivity, "Error: ${result.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun showWifiDialog() {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
        }
        
        // WiFi status
        val statusText = TextView(this).apply {
            text = if (wifiManager.isWifiEnabled) "WiFi: Enabled" else "WiFi: Disabled"
            textSize = 18f
            setTextColor(Color.BLACK)
            typeface = Typeface.DEFAULT_BOLD
        }
        
        layout.addView(statusText, createLayoutParams(dpToPx(16)))
        
        val infoText = TextView(this).apply {
            text = "Use buttons below to configure WiFi"
            textSize = 14f
            setTextColor(Color.GRAY)
        }
        layout.addView(infoText, createLayoutParams(dpToPx(8)))
        
        AlertDialog.Builder(this)
            .setTitle("WiFi Settings")
            .setView(layout)
            .setPositiveButton("Open WiFi Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            }
            .setNeutralButton("Network Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
            }
            .setNegativeButton("Close", null)
            .show()
    }
    
    private fun showRollbackDialog() {
        val updateManager = UpdateManager(this)
        val versions = updateManager.getStoredVersions()
        
        if (versions.isEmpty()) {
            Toast.makeText(this, "No previous versions available", Toast.LENGTH_SHORT).show()
            return
        }
        
        val currentVersion = packageManager.getPackageInfo(packageName, 0).longVersionCode.toInt()
        val items = versions.map { v ->
            val date = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
                .format(java.util.Date(v.date))
            val current = if (v.code == currentVersion) " (current)" else ""
            "${v.name}$current - $date"
        }.toTypedArray()
        
        AlertDialog.Builder(this)
            .setTitle("Select Version to Install")
            .setItems(items) { _, which ->
                val selected = versions[which]
                if (selected.code == currentVersion) {
                    Toast.makeText(this, "Already running this version", Toast.LENGTH_SHORT).show()
                    return@setItems
                }
                lifecycleScope.launch {
                    Toast.makeText(this@FullAdminActivity, "Installing ${selected.name}...", Toast.LENGTH_SHORT).show()
                    val success = updateManager.installStoredVersion(selected.code)
                    if (!success) {
                        Toast.makeText(this@FullAdminActivity, "Install failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPasswordChangeDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
        }

        val currentPasswordEdit = EditText(this).apply {
            hint = "Current Password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        val newPasswordEdit = EditText(this).apply {
            hint = "New Password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        val confirmPasswordEdit = EditText(this).apply {
            hint = "Confirm New Password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        layout.addView(currentPasswordEdit, createLayoutParams(dpToPx(8)))
        layout.addView(newPasswordEdit, createLayoutParams(dpToPx(8)))
        layout.addView(confirmPasswordEdit, createLayoutParams(dpToPx(8)))

        AlertDialog.Builder(this)
            .setTitle("Change Admin Password")
            .setView(layout)
            .setPositiveButton("Change") { _, _ ->
                val currentPassword = currentPasswordEdit.text.toString()
                val newPassword = newPasswordEdit.text.toString()
                val confirmPassword = confirmPasswordEdit.text.toString()

                if (currentPassword != getStoredPassword()) {
                    Toast.makeText(this, "Current password is incorrect", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (newPassword.isNotEmpty() && newPassword.length < 4) {
                    Toast.makeText(this, "Password must be at least 4 characters", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (newPassword != confirmPassword) {
                    Toast.makeText(this, "New passwords don't match", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                savePassword(newPassword)
                Toast.makeText(this, "Password changed successfully", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun getStoredPassword(): String {
        val prefs = getSharedPreferences("kiosk_settings", Context.MODE_PRIVATE)
        return prefs.getString("admin_password", "") ?: ""
    }

    private fun savePassword(password: String) {
        val prefs = getSharedPreferences("kiosk_settings", Context.MODE_PRIVATE)
        prefs.edit().putString("admin_password", password).apply()
    }

    private fun showSettingsDialog() {
        val prefs = getSharedPreferences("kiosk_settings", Context.MODE_PRIVATE)
        val isTimeoutEnabled = prefs.getBoolean("timeout_enabled", true)
        val timeoutMinutes = prefs.getInt("timeout_minutes", 5)
        val isAlwaysOnEnabled = prefs.getBoolean("always_on_display", true)
        val shared = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val currentHome = shared.getString("default_url", "http://192.168.10.12/sdcard/cpt/app/signin.php") ?: "http://192.168.10.12/sdcard/cpt/app/signin.php"

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
        }

        // Always-on Display Setting
        val alwaysOnLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val alwaysOnLabel = TextView(this).apply {
            text = "Always-On Display"
            textSize = 16f
            setTextColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val alwaysOnSwitch = Switch(this).apply {
            isChecked = isAlwaysOnEnabled
        }
        alwaysOnLayout.addView(alwaysOnLabel)
        alwaysOnLayout.addView(alwaysOnSwitch)
        layout.addView(alwaysOnLayout, createLayoutParams(dpToPx(16)))

        // Enable Timeout Setting
        val enableTimeoutLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val enableTimeoutLabel = TextView(this).apply {
            text = "Enable Auto-Refresh"
            textSize = 16f
            setTextColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val enableTimeoutSwitch = Switch(this).apply {
            isChecked = isTimeoutEnabled
        }

        enableTimeoutLayout.addView(enableTimeoutLabel)
        enableTimeoutLayout.addView(enableTimeoutSwitch)

        // Auto-Refresh Timeout Setting
        val timeoutLabel = TextView(this).apply {
            text = "Refresh Interval: $timeoutMinutes minutes"
            textSize = 16f
            setTextColor(Color.BLACK)
        }

        val timeoutSeekBar = SeekBar(this).apply {
            max = 59 // 1-60 minutes (0-59 + 1)
            progress = timeoutMinutes - 1
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    timeoutLabel.text = "Refresh Interval: ${progress + 1} minutes"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        layout.addView(enableTimeoutLayout, createLayoutParams(dpToPx(16)))
        layout.addView(timeoutLabel, createLayoutParams(dpToPx(8)))
        layout.addView(timeoutSeekBar, createLayoutParams(dpToPx(16)))

        // Removed in favor of directing users to Android Display settings

        val homeLabel = TextView(this).apply {
            text = "Home URL/IP"
            textSize = 16f
            setTextColor(Color.BLACK)
        }
        val homeEdit = EditText(this).apply {
            hint = "http://192.168.1.10"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setText(currentHome)
        }

        layout.addView(homeLabel, createLayoutParams(dpToPx(8)))
        layout.addView(homeEdit, createLayoutParams(dpToPx(16)))

        // Auto-Login Settings
        val autoLoginEnabled = prefs.getBoolean("auto_login_enabled", true)
        val autoLoginUser = prefs.getString("auto_login_username", "admin") ?: "admin"
        val autoLoginPass = prefs.getString("auto_login_password", "f1ref!ghterFARS") ?: "f1ref!ghterFARS"

        val autoLoginLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val autoLoginLabel = TextView(this).apply {
            text = "Auto-Login"
            textSize = 16f
            setTextColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val autoLoginSwitch = Switch(this).apply { isChecked = autoLoginEnabled }
        autoLoginLayout.addView(autoLoginLabel)
        autoLoginLayout.addView(autoLoginSwitch)
        layout.addView(autoLoginLayout, createLayoutParams(dpToPx(8)))

        val loginUserEdit = EditText(this).apply {
            hint = "Login Username"
            inputType = InputType.TYPE_CLASS_TEXT
            setText(autoLoginUser)
        }
        val loginPassEdit = EditText(this).apply {
            hint = "Login Password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(autoLoginPass)
        }
        layout.addView(loginUserEdit, createLayoutParams(dpToPx(8)))
        layout.addView(loginPassEdit, createLayoutParams(dpToPx(16)))

        AlertDialog.Builder(this)
            .setTitle("General Settings")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val newAlwaysOn = alwaysOnSwitch.isChecked
                val newTimeoutEnabled = enableTimeoutSwitch.isChecked
                val newTimeoutMinutes = timeoutSeekBar.progress + 1
                val newHome = homeEdit.text.toString().trim()

                prefs.edit()
                    .putBoolean("always_on_display", newAlwaysOn)
                    .putBoolean("timeout_enabled", newTimeoutEnabled)
                    .putInt("timeout_minutes", newTimeoutMinutes)
                    .putBoolean("auto_login_enabled", autoLoginSwitch.isChecked)
                    .putString("auto_login_username", loginUserEdit.text.toString())
                    .putString("auto_login_password", loginPassEdit.text.toString())
                    .apply()

                if (newHome.isNotEmpty()) {
                    shared.edit().putString("default_url", newHome).apply()
                }

                Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
                finish()
            }
            .setNeutralButton("Display Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))
            }
            .setNegativeButton("Android Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
            .show()
    }
} 