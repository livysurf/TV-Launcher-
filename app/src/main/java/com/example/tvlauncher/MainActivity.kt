package com.example.tvlauncher

import android.animation.ObjectAnimator
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.Random
import kotlin.math.PI
import kotlin.math.sin

// ---- Customise your look here ----
private val BACKGROUND = Color.BLACK
private val TEXT = Color.WHITE
private val SUBTEXT = Color.argb(180, 255, 255, 255)
private val TILE_FOCUS = Color.argb(45, 255, 255, 255)  // soft fill behind the focused app
private val TILE_GLOW = Color.argb(70, 255, 255, 255)   // halo around the focused app
private val PANEL_FILL = Color.argb(16, 255, 255, 255)  // faint panel behind the content
private const val PANEL_CURVE = 40f
private const val PILL_CURVE = 200f
private const val TILE_CURVE = 30f
private const val GLOW_SPACE = 28
private const val PILL_TOP_MARGIN = 0    // 0 = pinned to the very top edge
private const val CONTENT_TOP = 150      // space reserved for the island
private const val ICON_SIZE = 72         // app icon size in pixels
private const val TILE_WIDTH = 130       // width of each app tile in pixels
private const val TILE_SPACING = 14      // gap between tiles
private const val CLOCK_TEXT_SIZE = 16f
private const val LABEL_TEXT_SIZE = 13f
private const val DRIFT_DISTANCE = 60f
private const val DRIFT_MS = 9000L
private const val STAR_COUNT = 45
private const val PILL_BREATH_MS = 2400L

// Sound settings
private const val SAMPLE_RATE = 44100
private const val FOCUS_VOLUME = 0.22    // 0.0 to 1.0, quieter = softer
private const val CLICK_VOLUME = 0.35

// Apps that should never appear in the launcher (email apps, plus this launcher)
private val HIDDEN_PACKAGES = setOf(
    "com.example.tvemail",
    "com.google.android.gm",
    "com.google.android.gm.lite",
    "com.microsoft.office.outlook",
    "com.yahoo.mobile.client.android.mail",
    "com.samsung.android.email.provider"
)

data class AppItem(val label: String, val pkg: String, val icon: Drawable)

// Builds a short sound that sweeps from one pitch to another, with a soft fade in and out
private fun buildSweep(fromHz: Double, toHz: Double, ms: Int, volume: Double): AudioTrack {
    val n = SAMPLE_RATE * ms / 1000
    val buffer = ShortArray(n)
    var phase = 0.0
    for (i in 0 until n) {
        val t = i.toDouble() / n
        val freq = fromHz + (toHz - fromHz) * t
        phase += 2 * PI * freq / SAMPLE_RATE
        val envelope = sin(PI * t)
        buffer[i] = (sin(phase) * envelope * volume * Short.MAX_VALUE).toInt().toShort()
    }
    val track = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
        )
        .setBufferSizeInBytes(n * 2)
        .setTransferMode(AudioTrack.MODE_STATIC)
        .build()
    track.write(buffer, 0, n)
    return track
}

class MainActivity : Activity() {
    private val apps = mutableListOf<AppItem>()
    private lateinit var pill: FrameLayout
    private lateinit var clock: TextView
    private lateinit var header: TextView
    private lateinit var appRow: LinearLayout
    private val animators = mutableListOf<ObjectAnimator>()
    private val handler = Handler(Looper.getMainLooper())

    // Short rising chirp for moving focus, deeper sweep for opening an app
    private val focusSound: AudioTrack by lazy { buildSweep(1200.0, 1800.0, 60, FOCUS_VOLUME) }
    private val clickSound: AudioTrack by lazy { buildSweep(600.0, 1200.0, 120, CLICK_VOLUME) }

    // When an app is focused, the island shows its name instead of the time
    private var focusedLabel: String? = null

    // Inter from res/font/inter_regular.ttf, falling back to the system font if it's missing
    private val appFont: Typeface by lazy {
        ResourcesCompat.getFont(this, R.font.inter_regular) ?: Typeface.DEFAULT
    }

    private val clockTick = object : Runnable {
        override fun run() {
            if (focusedLabel == null) clock.text = timeNow()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val screen = FrameLayout(this).apply {
            setBackgroundColor(BACKGROUND)
        }

        // Background layers: twinkling stars, then slowly drifting orbs
        screen.addView(starField())
        screen.addView(driftingOrb(Color.parseColor("#7B5CFF"), 800, Gravity.TOP or Gravity.START, -240, -220))
        screen.addView(driftingOrb(Color.parseColor("#00C2FF"), 640, Gravity.BOTTOM or Gravity.END, 0, 0))
        screen.addView(driftingOrb(Color.parseColor("#FF6AD5"), 420, Gravity.TOP or Gravity.END, 0, 120))

        // Greeting in the top-left corner
        val greeting = TextView(this).apply {
            text = greetingText()
            textSize = 26f
            setTextColor(TEXT)
            typeface = appFont
        }
        screen.addView(
            greeting,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START
            ).apply {
                leftMargin = 100
                topMargin = 60
            }
        )

        // Top bar in the top-right: Apps, Install, Settings
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(topBarButton("Apps") { focusFirstApp() })
            addView(topBarButton("Install") { openStore() })
            addView(topBarButton("Settings") { openSettings() })
        }
        screen.addView(
            topBar,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            ).apply {
                rightMargin = 100
                topMargin = 56
            }
        )

        // Main content: the app panel, with the hint bar at the bottom
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(100, CONTENT_TOP, 100, 40)
        }

        // Single horizontal row holding every app
        header = TextView(this).apply {
            text = "All apps"
            textSize = 16f
            setTextColor(SUBTEXT)
            typeface = appFont
            setPadding(20, 0, 0, 12)
        }
        appRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 10, 0, 10)
        }
        val rowScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = false
            addView(appRow)
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = panelDrawable()
            setPadding(30, 24, 30, 24)
            addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(rowScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        column.addView(
            panel,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 20
            }
        )

        // Hint bar at the bottom
        val hint = TextView(this).apply {
            text = "OK to open   ·   Back to return   ·   Home to reset"
            textSize = 14f
            setTextColor(SUBTEXT)
            typeface = appFont
            gravity = Gravity.CENTER
        }
        column.addView(
            hint,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 30
            }
        )

        screen.addView(
            column,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        // Dynamic island pinned to the top edge, centred horizontally, drawn above everything
        clock = TextView(this).apply {
            text = timeNow()
            textSize = CLOCK_TEXT_SIZE
            setTextColor(TEXT)
            typeface = appFont
            gravity = Gravity.CENTER
            setPadding(44, 10, 44, 10)
        }
        pill = FrameLayout(this).apply {
            background = pillDrawable()
            setPadding(GLOW_SPACE, GLOW_SPACE, GLOW_SPACE, GLOW_SPACE)
            addView(clock)
        }
        screen.addView(
            pill,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            ).apply {
                topMargin = PILL_TOP_MARGIN
            }
        )
        // Slow breathing animation on the island
        animators.add(ObjectAnimator.ofFloat(pill, View.ALPHA, 0.8f, 1f).apply {
            duration = PILL_BREATH_MS
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
        })

        setContentView(screen)
        loadApps()
    }

    override fun onResume() {
        super.onResume()
        handler.post(clockTick)
        animators.forEach { if (!it.isRunning) it.start() }
        focusFirstApp()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(clockTick)
        animators.forEach { it.cancel() }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (focusSound.playState != AudioTrack.PLAYSTATE_STOPPED || true) focusSound.release()
        clickSound.release()
    }

    // Plays a sound from the start, even if it was already playing
    private fun play(track: AudioTrack) {
        track.stop()
        track.reloadStaticData()
        track.play()
    }

    // Moves focus to the first app in the row, so the remote works straight away
    private fun focusFirstApp() {
        appRow.getChildAt(0)?.requestFocus()
    }

    // Top bar button: frosted pill that brightens when focused
    private fun topBarButton(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 16f
        setTextColor(TEXT)
        typeface = appFont
        isAllCaps = false
        isFocusable = true
        background = barDrawable(false)
        setPadding(40, 14, 40, 14)
        setOnClickListener {
            play(clickSound)
            onClick()
        }
        setOnFocusChangeListener { v, hasFocus ->
            v.background = barDrawable(hasFocus)
            val scale = if (hasFocus) 1.08f else 1f
            v.animate().scaleX(scale).scaleY(scale).setDuration(150).start()
            if (hasFocus) play(focusSound)
        }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { leftMargin = 16 }
    }

    private fun barDrawable(focused: Boolean): Drawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = PILL_CURVE
        setColor(if (focused) TILE_FOCUS else PANEL_FILL)
    }

    // Opens the Play Store search so you can add new apps to the TV
    private fun openStore() {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=tv%20apps"))
        try {
            startActivity(market)
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps")))
        }
    }

    private fun openSettings() {
        try {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            // Settings screen not available on this device, nothing to open
        }
    }

    private fun timeNow(): String =
        SimpleDateFormat("EEE, MMM d  ·  h:mm a", Locale.getDefault()).format(Date())

    // "Good morning", "Good afternoon" or "Good evening", based on the time
    private fun greetingText(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    // Island reacts to focus: pulses, then swaps the time for the app name
    private fun onAppFocused(label: String?) {
        focusedLabel = label
        val newText = label ?: timeNow()
        pill.animate().scaleX(1.06f).scaleY(1.06f).setDuration(140).withEndAction {
            pill.animate().scaleX(1f).scaleY(1f).setDuration(220).start()
        }.start()
        clock.animate().alpha(0f).setDuration(110).withEndAction {
            clock.text = newText
            clock.animate().alpha(1f).setDuration(180).start()
        }.start()
    }

    // Small white dots that fade in and out at different speeds
    private fun starField(): FrameLayout {
        val layer = FrameLayout(this)
        val rnd = Random(7)
        repeat(STAR_COUNT) {
            val size = 3 + rnd.nextInt(5)
            val star = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.WHITE)
                }
                layoutParams = FrameLayout.LayoutParams(size, size, Gravity.TOP or Gravity.START).apply {
                    leftMargin = rnd.nextInt(1800)
                    topMargin = rnd.nextInt(1000)
                }
            }
            animators.add(ObjectAnimator.ofFloat(star, View.ALPHA, 0.15f, 0.9f).apply {
                duration = 1500L + rnd.nextInt(3000)
                startDelay = rnd.nextInt(3000).toLong()
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
            })
            layer.addView(star)
        }
        return layer
    }

    // A soft orb that slowly floats back and forth
    private fun driftingOrb(color: Int, size: Int, gravity: Int, dx: Int, dy: Int): View {
        val orb = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                gradientType = GradientDrawable.RADIAL_GRADIENT
                gradientRadius = size / 2f
                colors = intArrayOf(color, Color.TRANSPARENT)
            }
            layoutParams = FrameLayout.LayoutParams(size, size, gravity).apply {
                leftMargin = dx
                topMargin = dy
            }
        }
        animators.add(ObjectAnimator.ofFloat(orb, View.TRANSLATION_X, -DRIFT_DISTANCE, DRIFT_DISTANCE).apply {
            duration = DRIFT_MS
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
        })
        animators.add(ObjectAnimator.ofFloat(orb, View.TRANSLATION_Y, DRIFT_DISTANCE, -DRIFT_DISTANCE).apply {
            duration = DRIFT_MS * 3 / 2
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
        })
        return orb
    }

    // Stacked rings: four white rings, brightest near the pill, then the black pill on top
    private fun pillDrawable(): Drawable {
        fun ring(alpha: Int) = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = PILL_CURVE
            setColor(Color.argb(alpha, 255, 255, 255))
        }
        val black = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = PILL_CURVE
            setColor(Color.BLACK)
        }
        val layers = LayerDrawable(arrayOf(ring(35), ring(80), ring(160), ring(230), black))
        layers.setLayerInset(0, 0, 0, 0, 0)
        layers.setLayerInset(1, 6, 6, 6, 6)
        layers.setLayerInset(2, 12, 12, 12, 12)
        layers.setLayerInset(3, 18, 18, 18, 18)
        layers.setLayerInset(4, GLOW_SPACE, GLOW_SPACE, GLOW_SPACE, GLOW_SPACE)
        return layers
    }

    // Very faint rounded panel behind the content
    private fun panelDrawable(): Drawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = PANEL_CURVE
        setColor(PANEL_FILL)
    }

    // True for email apps, so they stay out of the launcher
    private fun isHidden(pkg: String, label: String): Boolean {
        if (pkg == packageName) return true
        if (pkg in HIDDEN_PACKAGES) return true
        val text = (pkg + " " + label).lowercase(Locale.getDefault())
        return text.contains("mail") || text.contains("gmail") || text.contains("outlook")
    }

    // Finds every app with a TV or phone launcher icon, minus the hidden ones
    private fun loadApps() {
        val pm = packageManager
        val seen = HashSet<String>()
        val found = mutableListOf<AppItem>()
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (info in pm.queryIntentActivities(intent, 0)) {
                val pkg = info.activityInfo.packageName
                if (!seen.add(pkg)) continue
                val label = info.loadLabel(pm).toString()
                if (isHidden(pkg, label)) continue
                found.add(AppItem(label, pkg, info.loadIcon(pm)))
            }
        }
        apps.clear()
        apps.addAll(found.sortedBy { it.label.lowercase(Locale.getDefault()) })
        header.text = "All apps  ·  ${apps.size}"

        // Put every app into the one horizontal row
        appRow.removeAllViews()
        apps.forEach { item ->
            appRow.addView(
                makeTile(item),
                LinearLayout.LayoutParams(TILE_WIDTH, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    rightMargin = TILE_SPACING
                }
            )
        }
        focusFirstApp()
    }

    private fun launch(pkg: String) {
        val intent = packageManager.getLaunchIntentForPackage(pkg) ?: return
        play(clickSound)
        startActivity(intent)
    }

    // Focused tile: soft fill with a faint halo. Unfocused: fully invisible.
    private fun tileDrawable(focused: Boolean): Drawable {
        val fill = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = TILE_CURVE
            setColor(if (focused) TILE_FOCUS else Color.TRANSPARENT)
        }
        if (!focused) return fill
        val halo = GradientDrawable().apply {
           