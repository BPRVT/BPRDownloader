package com.bprvt.bprdownloader.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.bprvt.bprdownloader.R
import com.bprvt.bprdownloader.data.Prefs

/**
 * A pointer you steer with the D-pad, drawn on top of the WebView.
 *
 * Plain focus-based navigation falls apart on sites that were never built for
 * a remote, so cursor mode fakes a mouse: arrows move the pointer, OK sends a
 * synthetic tap at that spot, and pushing against the top/bottom edge scrolls.
 */
class CursorOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onTap: ((Float, Float) -> Unit)? = null
    var onScroll: ((Int) -> Unit)? = null
    var onExit: (() -> Unit)? = null

    private val pointer: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_cursor)
    private var cursorX = -1f
    private var cursorY = -1f

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (cursorX < 0 || cursorY < 0) centerCursor()
    }

    fun centerCursor() {
        cursorX = width / 2f
        cursorY = height / 2f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val drawable = pointer ?: return
        val w = drawable.intrinsicWidth
        val h = drawable.intrinsicHeight
        val left = cursorX.toInt()
        val top = cursorY.toInt()
        drawable.setBounds(left, top, left + w, top + h)
        drawable.draw(canvas)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Holding a direction accelerates, so crossing a 1080p page doesn't
        // take fifty presses.
        val step = Prefs.cursorSpeed * (1f + minOf(event.repeatCount, 20) * 0.45f)
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                cursorX = (cursorX - step).coerceAtLeast(0f)
                invalidate()
                return true
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                cursorX = (cursorX + step).coerceAtMost(width - 1f)
                invalidate()
                return true
            }

            KeyEvent.KEYCODE_DPAD_UP -> {
                if (cursorY - step < EDGE_MARGIN) {
                    onScroll?.invoke(-(step * SCROLL_FACTOR).toInt())
                    cursorY = EDGE_MARGIN.toFloat()
                } else {
                    cursorY -= step
                }
                invalidate()
                return true
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (cursorY + step > height - EDGE_MARGIN) {
                    onScroll?.invoke((step * SCROLL_FACTOR).toInt())
                    cursorY = (height - EDGE_MARGIN).toFloat()
                } else {
                    cursorY += step
                }
                invalidate()
                return true
            }

            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BUTTON_A -> {
                onTap?.invoke(cursorX, cursorY)
                return true
            }

            KeyEvent.KEYCODE_BACK -> {
                onExit?.invoke()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        // Back is handled on the down stroke; swallow the up stroke too or the
        // activity also treats it as a back press and leaves the page.
        if (keyCode == KeyEvent.KEYCODE_BACK) return true
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_BUTTON_A -> true

            else -> super.onKeyUp(keyCode, event)
        }
    }

    companion object {
        private const val EDGE_MARGIN = 90
        private const val SCROLL_FACTOR = 3f
    }
}
