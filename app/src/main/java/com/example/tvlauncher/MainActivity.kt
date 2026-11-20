package com.example.tvlauncher

import android.animation.ObjectAnimator
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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

        val screen = FrameLayout(this).apply { setBackgroundColor(BACKGROUND) }

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

        // Dynamic island pinned to the top edge, centred, drawn above everything
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
            ).apply { topMargin = PILL_TOP_MARGIN }
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
        focusSound.release()
        clickSound.release()
    }

    // Plays a sound from the start, even if it was already playing
    private fun play(track: AudioTrack) {
        track.stop()
        track.reloadStaticData()
        track.play()
    }

    private fun focusFirstApp() {
        appRow.getChildAt(0)?.requestFocus()
    }

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
            // No settings screen on this device
        }
    }

    private fun timeNow(): String =
        SimpleDateFormat("EEE, MMM d  ·  h:mm a", Locale.getDefault()).format(Date())

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

    // True for email apps, so they stay out of the launcher
    private fun isHidden(pkg: String, label: String): Boolean {
        if (pkg == packageName) return true
        if (pkg in HIDDEN_PACKAGES) return true
        val text = (pkg + " " + label).lowercase(Locale.getDefault())
        return text.contains("mail") || text.contains("outlook")
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

    // One small app tile: icon on top, short name below
    private fun makeTile(item: AppItem): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(10, 16, 10, 16)
            isFocusable = true
            background = tileDrawable(false)

            addView(
                ImageView(this@MainActivity).apply { setImageDrawable(item.icon) },
                LinearLayout.LayoutParams(ICON_SIZE, ICON_SIZE)
            )
            addView(
                TextView(this@MainActivity).apply {
                    text = item.label
                    textSize = LABEL_TEXT_SIZE
                    setTextColor(TEXT)
                    typeface = appFont
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    gravity = Gravity.CENTER
                    setPadding(0, 10, 0, 0)
                },
                LinearLayout.LayoutParams(TILE_WIDTH - 20, ViewGroup.LayoutParams.WRAP_CONTENT)
            )

            setOnClickListener { launch(item.pkg) }
            setOnFocusChangeListener { v, hasFocus ->
                v.background = tileDrawable(hasFocus)
                val scale = if (hasFocus) 1.12f else 1f
                v.animate().scaleX(scale).scaleY(scale).setDuration(150).start()
                onAppFocused(if (hasFocus) item.label else null)
                if (hasFocus) play(focusSound)
            }
        }
    }
}