// carlito | Full CarPlay mirror onto a secondary display (e.g. instrument cluster).
// Self-contained: enumerates displays inline, does not touch HUD code, does not
// negotiate with the iPhone — the main screen stream (110) is already decoded
// by AndroidMediaSink, which fans the same NALUs to mirror decoders per (type,key).
package com.shilapi.xcertplay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.Surface
import android.view.WindowManager
import android.widget.FrameLayout
import com.shilapi.xcertplay.orchestration.CarPlayController
import com.shilapi.xcertplay.media.AndroidMediaSink

/** A non-default Android display that can host the mirrored CarPlay picture. */
internal data class ClusterMirrorDisplay(
    val id: Int,
    val name: String,
    val width: Int,
    val height: Int,
)

internal object ClusterMirrorSettings {
    private const val PREFS = "cluster_mirror_projection"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_DISPLAY_ID = "display_id"
    private const val KEY_DISPLAY_NAME = "display_name"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabled(context: Context) = prefs(context).getBoolean(KEY_ENABLED, false)
    fun setEnabled(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_ENABLED, value).apply()

    fun selectedDisplayId(context: Context): Int =
        prefs(context).getInt(KEY_DISPLAY_ID, Display.INVALID_DISPLAY)

    fun selectedDisplayName(context: Context): String? =
        prefs(context).getString(KEY_DISPLAY_NAME, null)

    fun select(context: Context, display: ClusterMirrorDisplay) {
        prefs(context).edit()
            .putInt(KEY_DISPLAY_ID, display.id)
            .putString(KEY_DISPLAY_NAME, display.name)
            .apply()
    }

    fun clearSelection(context: Context) {
        prefs(context).edit()
            .remove(KEY_DISPLAY_ID)
            .remove(KEY_DISPLAY_NAME)
            .apply()
    }
}

/**
 * Mirrors the main CarPlay screen onto a secondary display by handing a fresh
 * Surface to [AndroidMediaSink.setMirrorSurface] with stream type 110. The iPhone
 * is unaware: it keeps feeding the main screen, and the sink decodes a second
 * copy onto the mirror surface. No projection session token, no vehicle bridge.
 *
 * Self-contained on purpose: display enumeration lives here, and HUD code
 * ([GeelyHudProjection] et al.) is never called — HUD is out of scope.
 */
internal object ClusterMirrorProjection {
    private const val MIRROR_KEY = "cluster_mirror"
    private const val STREAM_MAIN = 110 // CarPlayHostActivity.SCREEN_TYPE_MAIN

    private val main = Handler(Looper.getMainLooper())
    private var app: Context? = null
    private var controller: CarPlayController? = null
    private var sink: AndroidMediaSink? = null
    private var manager: WindowManager? = null
    private var root: FrameLayout? = null
    private var texture: ClusterVideoTexture? = null
    private var surface: Surface? = null
    private var attachedDisplay: ClusterMirrorDisplay? = null
    private var stage = "IDLE"
    private val poll = object : Runnable {
        override fun run() { refresh(); if (controller != null) main.postDelayed(this, 500L) }
    }

    /** Enumerate non-default, non-OFF displays. Inline — does not call HUD code. */
    fun availableDisplays(context: Context): List<ClusterMirrorDisplay> {
        val dm = context.getSystemService(DisplayManager::class.java) ?: return emptyList()
        return dm.displays.asSequence()
            .filter { it.displayId != Display.DEFAULT_DISPLAY && it.state != Display.STATE_OFF }
            .map { display ->
                val metrics = context.createDisplayContext(display).resources.displayMetrics
                val w = metrics.widthPixels.takeIf { it > 0 } ?: display.mode.physicalWidth
                val h = metrics.heightPixels.takeIf { it > 0 } ?: display.mode.physicalHeight
                ClusterMirrorDisplay(display.displayId, display.name, w, h)
            }
            .toList()
    }

    fun attach(context: Context, next: CarPlayController, renderer: AndroidMediaSink) {
        main.post {
            if (controller === next) return@post
            closeNow()
            app = context.applicationContext
            controller = next
            sink = renderer
            main.post(poll)
        }
    }

    fun owns(expected: CarPlayController?) = expected != null && controller === expected
    fun stage() = stage

    fun close(expected: CarPlayController? = null) =
        main.post { if (expected == null || controller === expected) closeNow() }

    private fun refresh() {
        val context = app ?: return
        val renderer = sink ?: return
        if (controller == null) { hide(); return }
        if (!ClusterMirrorSettings.enabled(context) || !Settings.canDrawOverlays(context)) {
            hide(); return
        }
        val wantedId = ClusterMirrorSettings.selectedDisplayId(context)
        val wantedName = ClusterMirrorSettings.selectedDisplayName(context)
        val screen = availableDisplays(context).firstOrNull { display ->
            (wantedName == null || display.name == wantedName) &&
                (wantedId == Display.INVALID_DISPLAY || display.id == wantedId)
        } ?: availableDisplays(context).firstOrNull { it.name == wantedName }
        val display = screen?.let {
            context.getSystemService(DisplayManager::class.java)?.getDisplay(it.id)
        }
        if (screen == null || display == null || screen.width <= 0 || screen.height <= 0) {
            hide(); stage = "DISPLAY_UNAVAILABLE"; return
        }
        if (root != null && attachedDisplay?.id != screen.id) hide()
        if (root == null && !createWindow(context, display, screen)) {
            stage = "WINDOW_REJECTED"; return
        }
        if (surface?.isValid == true) stage = "ACTIVE" else stage = "WAITING_SURFACE"
    }

    private fun createWindow(context: Context, display: Display, screen: ClusterMirrorDisplay): Boolean {
        val displayContext = context.createDisplayContext(display)
        val wm = displayContext.getSystemService(WindowManager::class.java) ?: return false
        val container = FrameLayout(displayContext).apply { setBackgroundColor(Color.TRANSPARENT) }
        val video = ClusterVideoTexture(displayContext, screen.width, screen.height, onFrame = {}) { next ->
            val old = surface
            surface = next
            if (next == null) {
                if (old != null) sink?.setMirrorSurface(STREAM_MAIN, MIRROR_KEY, null)
            } else {
                sink?.setMirrorSurface(STREAM_MAIN, MIRROR_KEY, next)
            }
        }.apply { alpha = 1f }
        container.addView(video, FrameLayout.LayoutParams(screen.width, screen.height, Gravity.CENTER))
        val params = WindowManager.LayoutParams(screen.width, screen.height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.TOP or Gravity.START; title = "DiPlay Cluster Mirror"
            }
        return try {
            manager = wm; root = container; texture = video; attachedDisplay = screen
            wm.addView(container, params); true
        } catch (_: RuntimeException) { destroyWindow(); false }
    }

    private fun hide() {
        if (surface != null) sink?.setMirrorSurface(STREAM_MAIN, MIRROR_KEY, null)
        destroyWindow()
        stage = "IDLE"
    }

    private fun destroyWindow() {
        texture?.close(); texture = null
        root?.let { runCatching { manager?.removeViewImmediate(it) } }
        root = null; manager = null; attachedDisplay = null; surface = null
    }

    private fun closeNow() {
        main.removeCallbacks(poll)
        hide()
        controller = null; sink = null; app = null; stage = "IDLE"
    }

    fun diagnostics() = "clusterMirror stage=$stage target=${attachedDisplay?.id} hasSurface=${surface != null}"
}
