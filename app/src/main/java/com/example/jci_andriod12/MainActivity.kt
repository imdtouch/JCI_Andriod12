package com.example.jci_andriod12

import android.app.Activity
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.preference.PreferenceManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.core.view.WindowCompat
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.RelativeLayout
import android.widget.SeekBar
import android.widget.Button
 

class MainActivity : Activity() {
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName
    
    // Menu item references
    private var mHome: MenuItem? = null
    private var mBack: MenuItem? = null
    private var mForward: MenuItem? = null
    private var mSettings: MenuItem? = null
    
    // Configuration variables
    private var showBrowserControls = true
    private var defaultUrl = "http://192.168.10.12"
    
    // WebView reference
    private lateinit var kioskWebView: KioskWebView
    private lateinit var prefs: SharedPreferences
    private val uiHideHandler = Handler(Looper.getMainLooper())
    private val uiHideRunnable = Runnable { hideSystemUI() }
    private val dpmEnforceRunnable = Runnable { enforceDeviceOwnerPolicies() }
    private var controlsJustRevealed = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, KioskDeviceAdminReceiver::class.java)
        
        // Load preferences
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        loadPreferences()
        
        // Setup WebView
        setupWebView()
        
        window.decorView.setBackgroundResource(R.drawable.launcher_background)
        hideSystemUI()
        // Ensure we are launcher before applying some DO policies
        setAsDefaultLauncher()
        enableKioskMode()
        
        kioskWebView.postDelayed({
            kioskWebView.loadUrl(normalizedHome())
        }, 100)
        preferEthernet()
    }
    
    private fun loadPreferences() {
        showBrowserControls = prefs.getBoolean("show_browser_controls", true)
        defaultUrl = prefs.getString("default_url", "http://192.168.10.12") ?: "http://192.168.10.12"
        // Orientation handled via Android Display settings now
    }
    
    private fun setupWebView() {
        kioskWebView = KioskWebView(this, { revealControls() }, { showPasswordPrompt() })
        val container = findViewById<FrameLayout>(R.id.contentContainer)
        container.addView(
            kioskWebView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        setupNavButtons()
    }

    private fun setupNavButtons() {
        findViewById<ImageButton>(R.id.btnHome)?.setOnClickListener {
            kioskWebView.loadUrl(normalizedHome())
            updateNavButtons()
            resetControlsTimer()
        }
        findViewById<ImageButton>(R.id.btnBack)?.setOnClickListener {
            kioskWebView.goBack()
            updateNavButtons()
            resetControlsTimer()
        }
        findViewById<ImageButton>(R.id.btnForward)?.setOnClickListener {
            kioskWebView.goForward()
            updateNavButtons()
            resetControlsTimer()
        }
        findViewById<ImageButton>(R.id.btnAdmin)?.setOnClickListener {
            showPasswordPrompt()
        }
        
        // Setup zoom SeekBar
        findViewById<SeekBar>(R.id.zoomSeekBar)?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val zoomLevel = (50 + (progress * 2.5f)) / 100f // 50% to 300%
                    kioskWebView.setZoom(zoomLevel)
                    // Reset timer when user is actively using the slider
                    resetControlsTimer()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // Cancel auto-hide while user is dragging
                autoHideHandler.removeCallbacks(hideRunnable)
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Restart timer when user stops dragging
                resetControlsTimer()
            }
        })
        
        // Setup zoom reset button
        findViewById<Button>(R.id.zoomResetButton)?.setOnClickListener {
            val seekBar = findViewById<SeekBar>(R.id.zoomSeekBar)
            seekBar?.progress = 20 // Reset to 100% zoom (50% + 20*2.5 = 100%)
            kioskWebView.setZoom(1.0f)
            resetControlsTimer()
        }
        
        // Add click listener to content area to hide controls
        kioskWebView.setOnContentClickListener { hideControlsImmediately() }
        
        updateNavButtons()
    }

    private fun updateNavButtons() {
        findViewById<ImageButton>(R.id.btnBack)?.isEnabled = this::kioskWebView.isInitialized && kioskWebView.canGoBack()
        findViewById<ImageButton>(R.id.btnForward)?.isEnabled = this::kioskWebView.isInitialized && kioskWebView.canGoForward()
    }

    private val autoHideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { 
        findViewById<android.view.View>(R.id.navBar)?.visibility = android.view.View.GONE
        findViewById<android.view.View>(R.id.zoomBar)?.visibility = android.view.View.GONE
    }

    private fun revealControls() {
        val nav = findViewById<android.view.View>(R.id.navBar) ?: return
        val zoom = findViewById<android.view.View>(R.id.zoomBar) ?: return
        nav.visibility = android.view.View.VISIBLE
        zoom.visibility = android.view.View.VISIBLE
        updateNavButtons()
        resetControlsTimer()
        
        // Prevent immediate hiding after reveal
        controlsJustRevealed = true
        autoHideHandler.postDelayed({ controlsJustRevealed = false }, 800)
    }
    
    private fun resetControlsTimer() {
        autoHideHandler.removeCallbacks(hideRunnable)
        autoHideHandler.postDelayed(hideRunnable, 5000)
    }
    
    private fun hideControlsImmediately() {
        // Don't hide if controls were just revealed (prevents immediate hiding after edge swipe)
        if (controlsJustRevealed) return
        
        autoHideHandler.removeCallbacks(hideRunnable)
        findViewById<android.view.View>(R.id.navBar)?.visibility = android.view.View.GONE
        findViewById<android.view.View>(R.id.zoomBar)?.visibility = android.view.View.GONE
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu XML
        menuInflater.inflate(R.menu.main, menu)
        
        // Get references to menu items
        mHome = menu.findItem(R.id.action_home)
        mBack = menu.findItem(R.id.action_back)
        mForward = menu.findItem(R.id.action_forward)
        mSettings = menu.findItem(R.id.action_settings)
        
        // Configure visibility based on settings
        mSettings?.isVisible = true
        mHome?.isVisible = showBrowserControls
        mBack?.isVisible = showBrowserControls
        mForward?.isVisible = showBrowserControls
        
        return true
    }
    
    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        // Update back/forward button states based on WebView navigation
        mBack?.isEnabled = kioskWebView.canGoBack()
        mForward?.isEnabled = kioskWebView.canGoForward()
        return super.onPrepareOptionsMenu(menu)
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_home -> {
                kioskWebView.loadUrl(defaultUrl)
                true
            }
            R.id.action_back -> {
                kioskWebView.goBack()
                invalidateOptionsMenu() // Refresh menu state
                true
            }
            R.id.action_forward -> {
                kioskWebView.goForward()
                invalidateOptionsMenu() // Refresh menu state
                true
            }
            R.id.action_settings -> {
                openAdminScreen()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    override fun onBackPressed() {
        // In kiosk mode, use WebView navigation if available, otherwise do nothing
        if (kioskWebView.canGoBack()) {
            kioskWebView.goBack()
            invalidateOptionsMenu()
        }
        // Don't call super.onBackPressed() to prevent exiting kiosk mode
    }
    
    private fun enableKioskMode() {
        if (devicePolicyManager.isDeviceOwnerApp(packageName)) {
            devicePolicyManager.setLockTaskPackages(adminComponent, arrayOf(packageName))
            // Restrict system features while in lock task mode
            devicePolicyManager.setLockTaskFeatures(
                adminComponent,
                DevicePolicyManager.LOCK_TASK_FEATURE_NONE
            )

            // Disable status bar / keyguard for device owner
            devicePolicyManager.setStatusBarDisabled(adminComponent, true)
            devicePolicyManager.setKeyguardDisabled(adminComponent, true)

            startLockTask()
        }
    }
    
    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = window.insetsController
        if (controller != null) {
            controller.hide(
                WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
            )
            // Do not set SHOW_TRANSIENT_BARS_BY_SWIPE to avoid swipe showing bars
        } else {
            @Suppress("DEPRECATION")
            run {
                window.decorView.systemUiVisibility = (
                    android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                    android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
            }
        }

        // Also set legacy immersive flags to belt-and-suspenders hide
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
            android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent?): Boolean {
        // Re-hide on any interaction in case bars were revealed
        hideSystemUI()
        if (this::kioskWebView.isInitialized) {
            kioskWebView.noteUserInteraction()
        }
        return super.dispatchTouchEvent(ev)
    }
    
    private fun showPasswordPrompt() {
        val stored = getStoredPassword()
        if (stored.isEmpty()) { openFullAdminScreen(); return }
        val input = EditText(this).apply {
            hint = "Password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this)
            .setTitle("Admin Password")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                if (input.text.toString() == stored) openFullAdminScreen() else
                    android.widget.Toast.makeText(this, "Incorrect password", android.widget.Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun openAdminScreen() {
        val intent = Intent(this, AdminActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
    }
    
    private fun openFullAdminScreen() {
        val intent = Intent(this, FullAdminActivity::class.java)
        startActivity(intent)
    }

    private fun setAsDefaultLauncher() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val pm = packageManager
        val activities = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        
        val thisComponent = ComponentName(this, MainActivity::class.java)
        pm.setComponentEnabledSetting(
            thisComponent,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
        
        if (devicePolicyManager.isDeviceOwnerApp(packageName)) {
            try {
                val homeIntentFilter = IntentFilter(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    addCategory(Intent.CATEGORY_DEFAULT)
                }
                devicePolicyManager.addPersistentPreferredActivity(
                    adminComponent, homeIntentFilter, thisComponent
                )
            } catch (e: Exception) {
                // Fallback if persistent preferred activity fails
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadPreferences()
        hideSystemUI()
        invalidateOptionsMenu() // Update menu state when resuming
        updateNavButtons()
        // Re-apply immersive shortly after resume to collapse any transient bars
        window.decorView.postDelayed({ hideSystemUI() }, 100)
        // Start periodic re-hide while activity is in foreground
        uiHideHandler.postDelayed(uiHideRunnable, 1000)
        // Periodically re-assert device owner UI policies
        uiHideHandler.postDelayed(dpmEnforceRunnable, 1000)
        uiHideHandler.postDelayed(dpmEnforceRunnable, 3000)
        uiHideHandler.postDelayed(dpmEnforceRunnable, 5000)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
            window.decorView.postDelayed({ hideSystemUI() }, 50)
            window.decorView.postDelayed({ hideSystemUI() }, 150)
        }
    }

    override fun onPause() {
        super.onPause()
        uiHideHandler.removeCallbacks(uiHideRunnable)
        uiHideHandler.removeCallbacks(dpmEnforceRunnable)
        if (this::kioskWebView.isInitialized) {
            kioskWebView.stopIdleTimer()
        }
    }

    private fun preferEthernet() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .build()
        cm.requestNetwork(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                cm.bindProcessToNetwork(network)
            }
        })
    }

    private fun enforceDeviceOwnerPolicies() {
        if (!this::devicePolicyManager.isInitialized) return
        if (devicePolicyManager.isDeviceOwnerApp(packageName)) {
            try {
                devicePolicyManager.setStatusBarDisabled(adminComponent, true)
                devicePolicyManager.setKeyguardDisabled(adminComponent, true)
            } catch (t: Throwable) {
                android.util.Log.w("Kiosk", "Failed to enforce DPM policies", t)
            }
        }
        hideSystemUI()
    }

    private fun getStoredPassword(): String {
        val prefs = getSharedPreferences("kiosk_settings", Context.MODE_PRIVATE)
        return prefs.getString("admin_password", "") ?: ""
    }

    private fun normalizedHome(): String {
        val url = defaultUrl.trim()
        if (url.startsWith("http://") || url.startsWith("https://")) return url
        return "http://$url"
    }
}