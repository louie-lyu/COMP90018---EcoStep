package com.ecostep.app.evaluation

import android.app.UiAutomation
import android.graphics.Rect
import android.os.Bundle
import android.os.Build
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.KeyCharacterMap
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo

/** Real input + accessibility observations. No Compose idling or virtual clock in timings. */
internal class EvaluationUi(private val automation: UiAutomation, val timeoutMs: Long) {
    fun nodes(): List<AccessibilityNodeInfo> = buildList {
        // Compose can update enabled state/tags before accessibility invalidates its cached tree.
        if (Build.VERSION.SDK_INT >= 34) automation.clearCache()
        else automation.serviceInfo = automation.serviceInfo
        fun visit(node: AccessibilityNodeInfo) {
            add(node)
            repeat(node.childCount) { node.getChild(it)?.let(::visit) }
        }
        automation.rootInActiveWindow?.let(::visit)
    }

    fun visible(tag: String) = nodes().any { it.tag() == tag && it.isVisibleToUser }
    fun prefix(prefix: String) = nodes().firstOrNull { it.tag().startsWith(prefix) && it.isVisibleToUser }

    fun waitFor(label: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        do {
            if (condition()) return
            SystemClock.sleep(20)
        } while (SystemClock.elapsedRealtime() < deadline)
        throw UiResponseTimeout("Timed out: $label")
    }

    fun await(tag: String) = waitFor(tag) { visible(tag) }

    /** Resolve a stable, visible target before starting the measurement. */
    fun button(tagOrText: String): Rect {
        var previous: Rect? = null
        var stableSince = SystemClock.elapsedRealtime()
        waitFor("stable $tagOrText") {
            var node = nodes().firstOrNull {
                it.isVisibleToUser && (it.tag() == tagOrText || it.text?.toString() == tagOrText ||
                    it.contentDescription?.toString() == tagOrText)
            }
            while (node != null && !node.isClickable) node = node.parent
            val bounds = node?.takeIf { it.isEnabled && it.isVisibleToUser }?.let { Rect().also(it::getBoundsInScreen) }
            if (bounds != previous) { previous = bounds; stableSince = SystemClock.elapsedRealtime() }
            bounds != null && !bounds.isEmpty && SystemClock.elapsedRealtime() - stableSince >= 300
        }
        return checkNotNull(previous)
    }

    fun click(tagOrText: String) = tap(button(tagOrText))

    fun tap(bounds: Rect) {
        val down = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                bounds.exactCenterX(), bounds.exactCenterY(), 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
            try { check(automation.injectInputEvent(event, false)) { "Input injection failed" } }
            finally { event.recycle() }
        }
    }

    fun search(tag: String) {
        if (Build.VERSION.SDK_INT >= 30) {
            val field = nodes().first { it.tag() == tag && it.isEditable }
            check(field.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)) {
                "Search IME action unavailable"
            }
            return
        }
        val now = SystemClock.uptimeMillis()
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            check(automation.injectInputEvent(KeyEvent(now, SystemClock.uptimeMillis(), action,
                KeyEvent.KEYCODE_ENTER, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0,
                InputDevice.SOURCE_KEYBOARD), false))
        }
    }

    fun setText(tag: String, text: String) {
        click(tag)
        val field = nodes().first { it.tag() == tag && it.isEditable }
        check(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        })) { "Could not edit $tag" }
        waitFor("edited $tag") { nodes().any { it.tag() == tag && it.text?.toString() == text } }
    }

    fun scrollTo(tagOrText: String) {
        repeat(12) {
            if (nodes().any { it.isVisibleToUser && (it.tag() == tagOrText || it.text?.toString() == tagOrText) }) return
            nodes().firstOrNull { it.isScrollable && it.isVisibleToUser }
                ?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            SystemClock.sleep(250)
        }
        error("Cannot scroll to $tagOrText")
    }
}

internal fun AccessibilityNodeInfo.tag(): String = viewIdResourceName
    ?: extras.getString("androidx.compose.ui.semantics.testTag").orEmpty()

internal class UiResponseTimeout(message: String) : AssertionError(message)
