package com.example.jci_andriod12

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.view.GestureDetector
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController

// Connection state enum for clean state management
enum class ConnectionState {
    LOADING,
    CONNECTED,
    DISCONNECTED
}

// Listener interface for connection state changes
interface ConnectionStateListener {
    fun onConnectionStateChanged(state: ConnectionState, errorMessage: String?)
}

class KioskWebView(
    context: Context,
    private val onRevealControls: () -> Unit
) : FrameLayout(context) {

    private val webView: WebView
    private lateinit var gestureDetector: GestureDetector
    private var onContentClickListener: (() -> Unit)? = null
    private var autoLoginAttempted = false
    
    private val autoRefreshHandler = Handler(Looper.getMainLooper())
    private val autoRefreshRunnable = Runnable { webView.reload(); if (isTimeoutEnabled()) startAutoRefreshTimer() }
    private var refreshInterval = getRefreshInterval()
    private var countdownRemaining = 0L
    private val countdownOverlay: TextView
    private val countdownHandler = Handler(Looper.getMainLooper())
    private val countdownRunnable = object : Runnable {
        override fun run() {
            if (countdownRemaining > 0) {
                countdownRemaining -= 1000
                updateCountdownText()
                countdownHandler.postDelayed(this, 1000)
            }
        }
    }
    
    // Connection state management
    private var connectionState: ConnectionState = ConnectionState.LOADING
    private var connectionStateListener: ConnectionStateListener? = null
    private var lastErrorMessage: String? = null
    private lateinit var errorOverlay: LinearLayout
    private lateinit var errorMessageText: TextView
    
    fun setConnectionStateListener(listener: ConnectionStateListener?) {
        connectionStateListener = listener
    }
    
    fun getConnectionState(): ConnectionState = connectionState
    
    private fun setConnectionState(state: ConnectionState, errorMessage: String? = null) {
        if (connectionState != state || errorMessage != lastErrorMessage) {
            connectionState = state
            lastErrorMessage = errorMessage
            connectionStateListener?.onConnectionStateChanged(state, errorMessage)
            updateErrorOverlay()
        }
    }
    
    private fun updateErrorOverlay() {
        when (connectionState) {
            ConnectionState.DISCONNECTED -> {
                errorMessageText.text = lastErrorMessage ?: "Connection Lost"
                errorOverlay.visibility = View.VISIBLE
            }
            ConnectionState.CONNECTED, ConnectionState.LOADING -> {
                errorOverlay.visibility = View.GONE
            }
        }
    }

    init {
        // Create WebView
        webView = WebView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        
        // Create countdown overlay - larger and semi-transparent
        countdownOverlay = TextView(context).apply {
            textSize = 24f
            setTextColor(Color.argb(80, 0, 0, 0)) // Very transparent black
            setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6))
            visibility = View.GONE
        }
        
        // Create full-screen error overlay
        errorOverlay = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.argb(200, 0, 0, 0)) // Semi-transparent black
            visibility = View.GONE
            isClickable = true // Prevent touches from passing through
        }
        
        // Warning icon - large
        val warningIcon = TextView(context).apply {
            text = "⚠"
            textSize = 72f
            setTextColor(Color.rgb(255, 100, 100))
            gravity = Gravity.CENTER
        }
        
        // Error title
        val errorTitle = TextView(context).apply {
            text = "Connection Failed"
            textSize = 28f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, dpToPx(16), 0, dpToPx(8))
        }
        
        // Error message text
        errorMessageText = TextView(context).apply {
            text = "Unable to reach server"
            textSize = 16f
            setTextColor(Color.argb(200, 255, 255, 255))
            gravity = Gravity.CENTER
            setPadding(dpToPx(32), 0, dpToPx(32), dpToPx(24))
        }
        
        // Retry button - prominent
        val retryButton = Button(context).apply {
            text = "   RETRY   "
            textSize = 18f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.rgb(220, 80, 80))
                cornerRadius = dpToPx(8).toFloat()
            }
            setPadding(dpToPx(32), dpToPx(14), dpToPx(32), dpToPx(14))
            setOnClickListener { webView.reload() }
        }
        
        errorOverlay.addView(warningIcon)
        errorOverlay.addView(errorTitle)
        errorOverlay.addView(errorMessageText)
        errorOverlay.addView(retryButton)
        
        setupWebView()
        setupGestureDetection()
        addView(webView)
        addView(errorOverlay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        addView(countdownOverlay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.END
        ).apply { setMargins(0, 0, dpToPx(16), dpToPx(16)) })
    }

    private fun setupWebView() {
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                setConnectionState(ConnectionState.LOADING)
                autoLoginAttempted = false
            }
            
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Only set CONNECTED if we haven't received an error
                if (connectionState != ConnectionState.DISCONNECTED) {
                    setConnectionState(ConnectionState.CONNECTED)
                }
                (context as? MainActivity)?.let { it.invalidateOptionsMenu() }
                attemptAutoLogin()
            }
            
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                // Only handle main frame errors (not subresources like images/scripts)
                if (request?.isForMainFrame == true) {
                    val errorDesc = error?.description?.toString() ?: "Connection failed"
                    setConnectionState(ConnectionState.DISCONNECTED, errorDesc)
                }
            }
            
            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                super.onReceivedHttpError(view, request, errorResponse)
                // Only handle main frame errors
                if (request?.isForMainFrame == true) {
                    val statusCode = errorResponse?.statusCode ?: 0
                    if (statusCode >= 500) {
                        setConnectionState(ConnectionState.DISCONNECTED, "Server error: $statusCode")
                    }
                }
            }
        }
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            mediaPlaybackRequiresUserGesture = false
        }
        
        // Set up touch delegation from WebView to parent
        webView.setOnTouchListener { _, event ->
            // Reset auto-refresh timer on any touch activity
            if (isTimeoutEnabled()) {
                resetAutoRefreshTimer()
            }
            
            // Handle content clicks (single tap, not edge swipes)
            if (event.action == MotionEvent.ACTION_UP) {
                val edgeThreshold = 100f // pixels from edge
                val isNearEdge = event.y <= edgeThreshold || 
                                event.y >= height - edgeThreshold ||
                                event.x <= edgeThreshold || 
                                event.x >= width - edgeThreshold
                                
                if (!isNearEdge) {
                    onContentClickListener?.invoke()
                }
            }
            
            false // Allow WebView to handle the touch normally
        }
        
        // Handle edge gestures on parent container to avoid zoom interference
        this.setOnTouchListener { _, event ->
            // Always handle gesture detection on parent view coordinates
            gestureDetector.onTouchEvent(event)
            false // Don't consume - let children handle their touches
        }

        // Listen for insets changes to log and re-hide bars if they appear
        setOnApplyWindowInsetsListener { _, insets ->
            val areVisible = insets.isVisible(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            if (areVisible) {
                android.util.Log.d("Kiosk", "System bars visible; re-hiding")
                hideSystemBars()
                postDelayed({ hideSystemBars() }, 50)
                postDelayed({ hideSystemBars() }, 150)
            }
            insets
        }
    }
    
    private fun setupGestureDetection() {
        gestureDetector = GestureDetector(
            context,
            // Edge swipe reveals bottom controls
            EdgeSwipeDetector(context, 1600, { onRevealControls() }, requireTwoFingers = false, detectHorizontal = false)
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Update gesture detector with actual screen height
        gestureDetector = GestureDetector(
            context,
            EdgeSwipeDetector(context, h, { onRevealControls() }, requireTwoFingers = false, detectHorizontal = false)
        )

    }
    
    fun loadUrl(url: String) {
        webView.loadUrl(url)
        refreshInterval = getRefreshInterval()
        if (isTimeoutEnabled()) {
            startAutoRefreshTimer()
        }
    }
    
    // Navigation methods
    fun canGoBack(): Boolean = webView.canGoBack()
    
    fun canGoForward(): Boolean = webView.canGoForward()
    
    fun goBack() {
        if (webView.canGoBack()) {
            webView.goBack()
        }
    }
    
    fun goForward() {
        if (webView.canGoForward()) {
            webView.goForward()
        }
    }
    
    fun reload() {
        webView.reload()
    }
    
    fun setZoom(zoomLevel: Float) {
        val zoomPercent = (zoomLevel * 100).toInt()
        webView.evaluateJavascript(
            "document.body.style.zoom = '${zoomPercent}%';",
            null
        )
    }
    
    fun getPageInfo(callback: (String) -> Unit) {
        webView.evaluateJavascript("""
            (function() {
                var inputs = document.querySelectorAll('input');
                var buttons = document.querySelectorAll('button, input[type="submit"]');
                var forms = document.querySelectorAll('form');
                var result = {
                    url: window.location.href,
                    title: document.title,
                    inputs: [],
                    buttons: [],
                    forms: forms.length
                };
                inputs.forEach(function(inp) {
                    result.inputs.push({
                        type: inp.type,
                        id: inp.id,
                        name: inp.name,
                        placeholder: inp.placeholder,
                        value: inp.value ? '[has value]' : ''
                    });
                });
                buttons.forEach(function(btn) {
                    result.buttons.push({
                        type: btn.type,
                        id: btn.id,
                        text: btn.innerText || btn.value
                    });
                });
                return JSON.stringify(result, null, 2);
            })();
        """) { result -> callback(result) }
    }
    
    private fun attemptAutoLogin() {
        if (autoLoginAttempted) return
        val prefs = context.getSharedPreferences("kiosk_settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("auto_login_enabled", true)) return
        
        val username = prefs.getString("auto_login_username", "admin") ?: "admin"
        val password = prefs.getString("auto_login_password", "f1ref!ghterFARS") ?: "f1ref!ghterFARS"
        if (username.isEmpty()) return
        
        val jsUser = username.replace("\\", "\\\\").replace("'", "\\'")
        val jsPass = password.replace("\\", "\\\\").replace("'", "\\'")
        
        webView.evaluateJavascript("""
            (function() {
                if (!/sign|login|auth/i.test(window.location.href)) return 'skip';
                var passField = document.querySelector('input[type="password"]');
                if (!passField) return 'skip';
                var userField = document.querySelector('input[type="text"]') ||
                               document.querySelector('input[type="email"]');
                if (!userField) return 'skip';
                var submitBtn = document.querySelector('button[type="submit"]') ||
                               document.querySelector('input[type="submit"]') ||
                               Array.from(document.querySelectorAll('button')).find(b => 
                                   /sign|login|submit/i.test(b.textContent));
                userField.value = '$jsUser';
                userField.dispatchEvent(new Event('input', {bubbles: true}));
                passField.value = '$jsPass';
                passField.dispatchEvent(new Event('input', {bubbles: true}));
                if (submitBtn) { submitBtn.click(); return 'submitted'; }
                return 'no_button';
            })();
        """) { result ->
            if (result.contains("submitted")) {
                autoLoginAttempted = true
                Handler(Looper.getMainLooper()).postDelayed({
                    webView.evaluateJavascript("(/sign|login|auth/i.test(window.location.href)).toString()") { stillOnLogin ->
                        if (stillOnLogin.contains("true")) {
                            android.app.AlertDialog.Builder(context)
                                .setTitle("Auto-Login Failed")
                                .setMessage("Could not log in automatically. Check credentials in Settings.")
                                .setPositiveButton("OK", null)
                                .show()
                        }
                    }
                }, 3000)
            } else if (result.contains("no_button")) {
                autoLoginAttempted = true
                android.app.AlertDialog.Builder(context)
                    .setTitle("Auto-Login Failed")
                    .setMessage("Could not find login button.")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }
    
    fun setOnContentClickListener(listener: () -> Unit) {
        onContentClickListener = listener
    }
    
    private fun startAutoRefreshTimer() {
        refreshInterval = getRefreshInterval()
        countdownRemaining = refreshInterval
        autoRefreshHandler.postDelayed(autoRefreshRunnable, refreshInterval)
        countdownOverlay.visibility = View.VISIBLE
        countdownHandler.removeCallbacks(countdownRunnable)
        countdownHandler.post(countdownRunnable)
        updateCountdownText()
    }
    
    private fun resetAutoRefreshTimer() {
        autoRefreshHandler.removeCallbacks(autoRefreshRunnable)
        refreshInterval = getRefreshInterval()
        countdownRemaining = refreshInterval
        autoRefreshHandler.postDelayed(autoRefreshRunnable, refreshInterval)
        countdownHandler.removeCallbacks(countdownRunnable)
        countdownHandler.post(countdownRunnable)
        updateCountdownText()
    }
    
    private fun stopAutoRefreshTimer() {
        autoRefreshHandler.removeCallbacks(autoRefreshRunnable)
        countdownHandler.removeCallbacks(countdownRunnable)
        countdownOverlay.visibility = View.GONE
    }
    
    private fun updateCountdownText() {
        val secs = (countdownRemaining / 1000).coerceAtLeast(0)
        val min = secs / 60
        val sec = secs % 60
        countdownOverlay.text = String.format(java.util.Locale.ROOT, "%d:%02d", min, sec)
    }

    fun noteUserInteraction() {
        if (isTimeoutEnabled()) {
            resetAutoRefreshTimer()
        }
    }

    fun stopIdleTimer() {
        stopAutoRefreshTimer()
    }
    
    fun restartIdleTimer() {
        if (isTimeoutEnabled()) {
            startAutoRefreshTimer()
        }
    }
    
    private fun isTimeoutEnabled(): Boolean {
        val prefs = context.getSharedPreferences("kiosk_settings", Context.MODE_PRIVATE)
        return prefs.getBoolean("timeout_enabled", true)
    }
    
    private fun getRefreshInterval(): Long {
        val prefs = context.getSharedPreferences("kiosk_settings", Context.MODE_PRIVATE)
        val minutes = prefs.getInt("timeout_minutes", 5)
        return minutes * 60 * 1000L // Convert minutes to milliseconds
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }

    private fun hideSystemBars() {
        val activity = context as? android.app.Activity ?: return
        activity.window.insetsController?.hide(
            WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
        )
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        ev?.let {
            if (isTimeoutEnabled()) {
                resetAutoRefreshTimer()
            }
            // Three-finger quick gesture to open admin without conflicting with system bars
            // Keep triple-finger fallback disabled to allow single-finger edge swipe UX
            gestureDetector.onTouchEvent(it)
        }
        return super.onInterceptTouchEvent(ev)
    }
    
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        event?.let {
            if (isTimeoutEnabled()) {
                resetAutoRefreshTimer()
            }
            gestureDetector.onTouchEvent(it)
        }
        return super.onTouchEvent(event)
    }
} 