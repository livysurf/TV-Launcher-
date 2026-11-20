package com.example.tvlauncher

import android.graphics.Color
import android.graphics.drawable.Drawable

val BACKGROUND = Color.BLACK
val TEXT = Color.WHITE
val SUBTEXT = Color.argb(180, 255, 255, 255)
val TILE_FOCUS = Color.argb(45, 255, 255, 255)
val TILE_GLOW = Color.argb(70, 255, 255, 255)
val PANEL_FILL = Color.argb(16, 255, 255, 255)
const val PANEL_CURVE = 40f
const val PILL_CURVE = 200f
const val TILE_CURVE = 30f
const val GLOW_SPACE = 28
const val PILL_TOP_MARGIN = 0
const val CONTENT_TOP = 150
const val ICON_SIZE = 72
const val TILE_WIDTH = 130
const val TILE_SPACING = 14
const val CLOCK_TEXT_SIZE = 16f
const val LABEL_TEXT_SIZE = 13f
const val DRIFT_DISTANCE = 60f
const val DRIFT_MS = 9000L
const val STAR_COUNT = 45
const val PILL_BREATH_MS = 2400L
const val SAMPLE_RATE = 44100
const val FOCUS_VOLUME = 0.22
const val CLICK_VOLUME = 0.35

// Apps that should never appear in the launcher (email apps)
val HIDDEN_PACKAGES = setOf(
    "com.example.tvemail",
    "com.google.android.gm",
    "com.google.android.gm.lite",
    "com.microsoft.office.outlook",
    "com.yahoo.mobile.client.android.mail",
    "com.samsung.android.email.provider"
)

data class AppItem(val label: String, val pkg: String, val icon: Drawable)