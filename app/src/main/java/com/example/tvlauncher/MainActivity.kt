package com.example.tvlauncher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---- Customise your look here ----
private val WALL_TOP = Color.parseColor("#05070F")
private val WALL_MID = Color.parseColor("#1A0B3D")
private val WALL_BOTTOM = Color.parseColor("#06324F")
private val TEXT = Color.WHITE
private val GLASS_TOP = Color.argb(70, 255, 255, 255)
private val GLASS_BOTTOM = Color.argb(20, 255, 255, 255)
private val GLASS_FOCUS_TOP = Color.argb(150, 255, 255, 255)
private val GLASS_FOCUS_BOTTOM = Color.argb(60, 255, 255, 255)
private val GLASS_EDGE = Color.argb(120, 255, 255, 255)
private const val CURVE = 40f
private const val COLUMNS = 5

// Apps that should never appear in the grid (email apps, plus this launcher)
private val HIDDEN_PACKAGES = setOf(
    "com.example.tvemail",
    "com.google.android.gm",
    "com.google.android.gm.lite",
    "com.microsoft.office.outlook",
    "com.yahoo.mobile.client.android.mail",
    "com.samsung.android.email.provider"
)

data class AppItem(val label: String, val pkg: String, val icon: Drawable)

class MainActivity : Activity() {
    private val apps = mutableListOf<AppItem>()
    private lateinit var clock: TextView
    private lateinit var grid: GridView
    private val handler = Handler(Looper.getMainLooper())
    private val clockTick = object : Runnable {
        override fun run() {
            clock.text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val screen = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(WALL_TOP, WALL_MID, WALL_BOTTOM)
            )
        }
        screen.addView(orb(Color.parseColor("#7B5CFF"), 800, Gravity.TOP or Gravity.START, -240, -220))
        screen.addView(orb(Color.parseColor("#00C2FF"), 640, Gravity.BOTTOM or Gravity.END, 0, 0))
        screen.addView(orb(Color.parseColor("#FF6AD5"), 420, Gravity.TOP or Gravity.END, 0, 120))

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(80, 40, 80, 40)
        }

        // Floating status pill at the top centre
        clock = TextView(this).apply {
            textSize = 22f
            setTextColor(TEXT)
            gravity = Gravity.CENTER
            setPadding(60, 16, 60, 16)
            background = glass()
        }
        column.addView(
            clock,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        )

        grid = GridView(this).apply {
            numColumns = COLUMNS
            verticalSpacing = 36
            horizontalSpacing = 36
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            selector = ColorDrawable(Color.TRANSPARENT)
            adapter = AppAdapter()
            onItemClickListener = AdapterView.OnItemClickListener { _, _, pos, _ ->
                launch(apps[pos].pkg)
            }
        }
        column.addView(
            grid,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = 40 }
        )

        screen.addView(
            column,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        setContentView(screen)
        loadApps()
    }

    override fun onResume() {
        super.onResume()
        handler.post(clockTick)
        grid.requestFocus()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(clockTick)
    }

    // True for email apps, so they stay out of the grid
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
        (grid.adapter as BaseAdapter).notifyDataSetChanged()
    }

    private fun launch(pkg: String) {
        packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it) }
    }

    private fun orb(color: Int, size: Int, gravity: Int, dx: Int, dy: Int) = View(this).apply {
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

    // Liquid-glass look: tinted body fading top to bottom, plus a bright sheen
    private fun glass(focused: Boolean = false): Drawable {
        val body = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            if (focused) intArrayOf(GLASS_FOCUS_TOP, GLASS_FOCUS_BOTTOM)
            else intArrayOf(GLASS_TOP, GLASS_BOTTOM)
        ).apply {
            cornerRadius = CURVE
            setStroke(3, GLASS_EDGE)
        }
        val sheen = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb(80, 255, 255, 255), Color.TRANSPARENT)
        ).apply {
            cornerRadius = CURVE
        }
        return LayerDrawable(arrayOf<Drawable>(body, sheen))
    }

    private inner class AppAdapter : BaseAdapter() {
        override fun getCount() = apps.size
        override fun getItem(position: Int) = apps[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val item = apps[position]
            return LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(20, 30, 20, 30)
                isFocusable = true
                background = glass()

                addView(
                    ImageView(this@MainActivity).apply { setImageDrawable(item.icon) },
                    LinearLayout.LayoutParams(130, 130)
                )
                addView(
                    TextView(this@MainActivity).apply {
                        text = item.label
                        textSize = 18f
                        setTextColor(TEXT)
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        gravity = Gravity.CENTER
                        setPadding(0, 16, 0, 0)
                    },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                )

                setOnFocusChangeListener { v, hasFocus ->
                    v.background = glass(hasFocus)
                    val scale = if (hasFocus) 1.08f else 1f
                    v.animate().scaleX(scale).scaleY(scale).setDuration(120).start()
                }
            }
        }
    }
}