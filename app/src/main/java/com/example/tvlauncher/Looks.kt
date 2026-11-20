package com.example.tvlauncher

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable

// Stacked rings: four white rings, brightest near the pill, then the black pill on top
fun pillDrawable(): Drawable {
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
    val layers = LayerDrawable(
        arrayOf<Drawable>(ring(35), ring(80), ring(160), ring(230), black)
    )
    layers.setLayerInset(0, 0, 0, 0, 0)
    layers.setLayerInset(1, 6, 6, 6, 6)
    layers.setLayerInset(2, 12, 12, 12, 12)
    layers.setLayerInset(3, 18, 18, 18, 18)
    layers.setLayerInset(4, GLOW_SPACE, GLOW_SPACE, GLOW_SPACE, GLOW_SPACE)
    return layers
}

// Very faint rounded panel behind the app row
fun panelDrawable(): Drawable = GradientDrawable().apply {
    shape = GradientDrawable.RECTANGLE
    cornerRadius = PANEL_CURVE
    setColor(PANEL_FILL)
}

// Top bar buttons: frosted pill that brightens when focused
fun barDrawable(focused: Boolean): Drawable = GradientDrawable().apply {
    shape = GradientDrawable.RECTANGLE
    cornerRadius = PILL_CURVE
    setColor(if (focused) TILE_FOCUS else PANEL_FILL)
}

// Focused tile: soft fill with a faint halo. Unfocused: fully invisible.
fun tileDrawable(focused: Boolean): Drawable {
    val fill = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = TILE_CURVE
        setColor(if (focused) TILE_FOCUS else Color.TRANSPARENT)
    }
    if (!focused) return fill
    val halo = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = TILE_CURVE + 6
        setColor(TILE_GLOW)
    }
    return LayerDrawable(arrayOf<Drawable>(halo, fill)).apply {
        setLayerInset(1, 4, 4, 4, 4)
    }
}