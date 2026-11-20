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
import android.text.TextUtils
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
private val BACKGROUND = Color.BLACK
private val TEXT = Color.WHITE
private val TILE_FOCUS = Color.argb(45, 255, 255, 255)  // soft fill behind the app you're on
private const val PILL_CURVE = 200f      // large value = fully rounded pill
private const val TILE_CURVE = 36f      // roundness of the focus fill
private const val COLUMNS = 5
private const val GLOW_SPACE = 28       // how far the glow reaches around the pill
private const val PILL_TOP_OFFSET = 140 // how far the pill sits below the top edge
private const val ICON_SIZE = 110       // app icon size in pixels

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
            setBackgroundColor(BACKGROUND)
        }

        // Top padding pushes the pill down from the top edge, horizontally centred
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(100, PILL_TOP_OFFSET, 100, 50)
        }

        clock = TextView(this).apply {
            textSize = 22f
            setTextColor(TEXT)
            gravity = Gravity.CENTER
            setPadding(60, 16, 60, 16)
        }
        val pill = FrameLayout(this).apply {
            background = pillDrawable()
            setPadding(GLOW_SPACE, GLOW_SPACE, GLOW_SPACE, GLOW_SPACE)
            addView(clock)
        }
        column.addView(
            pill,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        )

        grid = GridView(this).apply {
            numColumns = COLUMNS
            verticalSpacing = 30
            horizontalSpacing = 30
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            selector = ColorDrawable(Color.TRANSPARENT)
            adapter = AppAdapter()
            onItemClickListener = AdapterView.OnItemClickListener { _, _, pos, _ ->
                launch(apps[pos].pkg)
            }
        }
        column.addView(
            grid,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = 50 }
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
        // Alpha values control the glow brightness: higher = brighter
        val layers = LayerDrawable(arrayOf(ring(35), ring(80), ring(160), ring(230), black))
        layers.setLayerInset(0, 0, 0, 0, 0)
        layers.setLayerInset(1, 6, 6, 6, 6)
        layers.setLayerInset(2, 12, 12, 12, 12)
        layers.setLayerInset(3, 18, 18, 18, 18)
        layers.setLayerInset(4, GLOW_SPACE, GLOW_SPACE, GLOW_SPACE, GLOW_SPACE)
        return layers
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

    // Tile background: invisible normally, a soft light fill when focused. No outline.
    private fun tileDrawable(focused: Boolean): Drawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = TILE_CURVE
        setColor(if (focused) TILE_FOCUS else Color.TRANSPARENT)
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
                setPadding(16, 24, 16, 24)
                isFocusable = true
                background = tileDrawable(false)

                addView(
                    ImageView(this@MainActivity).apply { setImageDrawable(item.icon) },
                    LinearLayout.LayoutParams(ICON_SIZE, ICON_SIZE)
                )
                addView(
                    TextView(this@MainActivity).apply {
                        text = item.label
                        textSize = 20f
                        setTextColor(TEXT)
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                        gravity = Gravity.CENTER
                        setPadding(0, 14, 0, 0)
                    },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                )

                setOnFocusChangeListener { v, hasFocus ->
                    v.background = tileDrawable(hasFocus)
                    val scale = if (hasFocus) 1.1f else 1f
                    v.animate().scaleX(scale).scaleY(scale).setDuration(120).start()
                }
            }
        }
    }
}