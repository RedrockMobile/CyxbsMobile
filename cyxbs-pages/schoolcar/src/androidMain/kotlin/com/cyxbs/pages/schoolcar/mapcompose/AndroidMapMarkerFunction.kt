package com.cyxbs.pages.schoolcar.mapcompose

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
/**
 * 将矢量图转为 Bitmap
 */
fun Context.getBitmapBySvg(@DrawableRes id: Int): Bitmap {
	val vectorDrawable = ContextCompat.getDrawable(this, id)?.mutate()
		?: return createBitmap(1, 1)
	val w = vectorDrawable.intrinsicWidth.takeIf { it > 0 } ?: 1
	val h = vectorDrawable.intrinsicHeight.takeIf { it > 0 } ?: 1
	val bitmap = createBitmap(w, h)
	val canvas = Canvas(bitmap)
	vectorDrawable.setBounds(0, 0, canvas.width, canvas.height)
	vectorDrawable.draw(canvas)
	return bitmap
}