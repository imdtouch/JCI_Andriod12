package com.example.jci_andriod12

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Color
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import androidx.activity.ComponentActivity

class AdminActivity : ComponentActivity() {
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
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
        }
        
        val exitButton = Button(this).apply {
            text = "Exit Application"
            textSize = 24f
            setBackgroundColor(Color.RED)
            setTextColor(Color.WHITE)
            setOnClickListener {
                exitApp()
            }
        }
        
        val backButton = Button(this).apply {
            text = "Back to Kiosk"
            textSize = 20f
            setBackgroundColor(Color.GRAY)
            setTextColor(Color.WHITE)
            setOnClickListener {
                finish()
            }
        }
        
        val buttonMargin = 40
        
        val exitParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, buttonMargin) }
        
        layout.addView(exitButton, exitParams)
        layout.addView(backButton)
        setContentView(layout)
    }
    
    private fun exitApp() {
        if (devicePolicyManager.isDeviceOwnerApp(packageName)) {
            try { stopLockTask() } catch (_: Exception) { }
            try { devicePolicyManager.setStatusBarDisabled(adminComponent, false) } catch (_: Exception) { }
            try { devicePolicyManager.setKeyguardDisabled(adminComponent, false) } catch (_: Exception) { }
            try { devicePolicyManager.clearPackagePersistentPreferredActivities(adminComponent, packageName) } catch (_: Exception) { }
            try { devicePolicyManager.clearDeviceOwnerApp(packageName) } catch (_: Exception) { }
        }
        // Go to system launcher
        val home = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(home)
        finishAffinity()
    }
} 