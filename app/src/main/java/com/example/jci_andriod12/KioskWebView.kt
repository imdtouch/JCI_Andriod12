package com.example.jci_andriod12

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.view.View
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class KioskWebView(
    context: Context,
    private val onRevealControls: () -> Unit,
    private val onPasswordPrompt: () -> Unit
) : FrameLayout(context) {

    private val webView: WebView
    private lateinit var gestureDetector: GestureDetectorCompat
    private var onContentClickListener: (() -> Unit)? = null
    
    private val autoRefreshHandler = Handler(Looper.getMainLooper())
    private val autoRefreshRunnable = Runnable { webView.reload() }
    private var refreshInterval = getRefreshInterval()

    init {
        // Create WebView
        webView = WebView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        
        setupWebView()
        setupGestureDetection()
        addView(webView)
        // Simplified: OS-level bars disabled, rely on top/bottom edge swipe
    }

    private fun setupWebView() {
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                (context as? MainActivity)?.let { it.invalidateOptionsMenu() }
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
        ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
            val areVisible = insets.isVisible(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
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
        gestureDetector = GestureDetectorCompat(
            context,
            // Edge swipe reveals bottom controls
            EdgeSwipeDetector(context, 1600, { onRevealControls() }, requireTwoFingers = false, detectHorizontal = false)
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Update gesture detector with actual screen height
        gestureDetector = GestureDetectorCompat(
            context,
            EdgeSwipeDetector(context, h, { onRevealControls() }, requireTwoFingers = false, detectHorizontal = false)
        )

        // With OS-level bars disabled, we can remove exclusions and guards
    }

    private fun addEdgeGuards() {
        val guardHeight = dpToPx(120)
        val topGuard = View(context).apply {
            isClickable = true
            setOnTouchListener { _, _ ->
                // Consume touches to prevent top-down system gesture
                hideSystemBars()
                true
            }
            alpha = 0.01f
        }
        val bottomGuard = View(context).apply {
            isClickable = true
            setOnTouchListener { _, _ ->
                hideSystemBars()
                true
            }
            alpha = 0.01f
        }

        addView(topGuard, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            guardHeight,
            Gravity.TOP
        ))
        addView(bottomGuard, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            guardHeight,
            Gravity.BOTTOM
        ))
    }

    private fun addAdminHotspot() {
        val hotspot = View(context).apply {
            isClickable = true
            isLongClickable = true
            alpha = 0.02f // nearly invisible
            setOnLongClickListener {
                onRevealControls()
                true
            }
        }
        val size = dpToPx(56)
        val lp = FrameLayout.LayoutParams(size, size).apply {
            gravity = Gravity.TOP or Gravity.END
            setMargins(0, dpToPx(6), dpToPx(6), 0)
        }
        addView(hotspot, lp)
    }
    
    // Deprecated floating admin button removed in favor of bottom bar
    
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
    
    fun setOnContentClickListener(listener: () -> Unit) {
        onContentClickListener = listener
    }
    
    private fun startAutoRefreshTimer() {
        autoRefreshHandler.postDelayed(autoRefreshRunnable, refreshInterval)
    }
    
    private fun resetAutoRefreshTimer() {
        autoRefreshHandler.removeCallbacks(autoRefreshRunnable)
        refreshInterval = getRefreshInterval()
        autoRefreshHandler.postDelayed(autoRefreshRunnable, refreshInterval)
    }
    
    private fun stopAutoRefreshTimer() {
        autoRefreshHandler.removeCallbacks(autoRefreshRunnable)
    }

    fun noteUserInteraction() {
        if (isTimeoutEnabled()) {
            resetAutoRefreshTimer()
        }
    }

    fun stopIdleTimer() {
        stopAutoRefreshTimer()
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
        val controller = ViewCompat.getWindowInsetsController(this)
        controller?.hide(
            WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars()
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