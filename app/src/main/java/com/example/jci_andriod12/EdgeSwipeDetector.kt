package com.example.jci_andriod12

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewConfiguration

class EdgeSwipeDetector(
    context: Context,
    private val screenExtent: Int,
    private val onEdgeSwipe: () -> Unit,
    private val requireTwoFingers: Boolean = false,
    @Suppress("unused") private val detectHorizontal: Boolean = false
) : GestureDetector.SimpleOnGestureListener() {

    private val viewConfig = ViewConfiguration.get(context)
    private val edgeThreshold = viewConfig.scaledEdgeSlop.toFloat() * 2
    private val minSwipeDistance = viewConfig.scaledTouchSlop.toFloat() * 2
    private val minVelocity = viewConfig.scaledMinimumFlingVelocity.toFloat() / 4

    override fun onDown(e: MotionEvent): Boolean {
        return true
    }

    override fun onScroll(
        e1: MotionEvent?,
        e2: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        e1?.let { startEvent ->
            val hasEnoughFingers = !requireTwoFingers || e2.pointerCount >= 2
            val deltaY = e2.y - startEvent.y
            val absDeltaY = kotlin.math.abs(deltaY)
            val absDeltaX = kotlin.math.abs(e2.x - startEvent.x)
            // Require mostly vertical movement
            if (!hasEnoughFingers || absDeltaY < minSwipeDistance || absDeltaY <= absDeltaX) return false
            // Start near top/bottom edge
            val startedFromTopEdge = startEvent.y <= edgeThreshold
            val startedFromBottomEdge = startEvent.y >= screenExtent - edgeThreshold
            if (startedFromTopEdge && deltaY > 0) {
                onEdgeSwipe(); return true
            }
            if (startedFromBottomEdge && deltaY < 0) {
                onEdgeSwipe(); return true
            }
        }
        return false
    }

    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        e1?.let { startEvent ->
            val hasEnoughFingers = !requireTwoFingers || e2.pointerCount >= 2
            val deltaY = e2.y - startEvent.y
            val absDeltaY = kotlin.math.abs(deltaY)
            val absVelocityY = kotlin.math.abs(velocityY)
            if (!hasEnoughFingers || absDeltaY < minSwipeDistance || absVelocityY < minVelocity) return false
            val startedFromTopEdge = startEvent.y <= edgeThreshold
            val startedFromBottomEdge = startEvent.y >= screenExtent - edgeThreshold
            if (startedFromTopEdge && deltaY > 0) {
                onEdgeSwipe(); return true
            }
            if (startedFromBottomEdge && deltaY < 0) {
                onEdgeSwipe(); return true
            }
        }
        return false
    }

    override fun onDoubleTap(e: MotionEvent): Boolean {
        // Double-tap near top/bottom edge (secondary)
        val fromEdge = e.y <= edgeThreshold || e.y >= screenExtent - edgeThreshold
        if (fromEdge) {
            onEdgeSwipe()
            return true
        }
        return false
    }
} 