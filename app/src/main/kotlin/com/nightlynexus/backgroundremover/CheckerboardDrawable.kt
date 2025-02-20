package com.nightlynexus.backgroundremover

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import kotlin.math.round

internal class CheckerboardDrawable(
  @ColorInt private val light: Int,
  @ColorInt private val dark: Int,
  private val idealSquareSideLength: Float
) : Drawable() {
  private val paintLight = Paint().apply { color = light }
  private val paintDark = Paint().apply { color = dark }
  private var sideLength = 0f

  override fun draw(canvas: Canvas) {
    val width = bounds.width()
    val height = bounds.height()
    var y = 0f
    var rowStartsDark = false
    while (y < height) {
      var x = 0f
      var dark = rowStartsDark
      while (x < width) {
        val paint = if (dark) paintDark else paintLight
        canvas.drawRect(x, y, x + sideLength, y + sideLength, paint)
        dark = !dark
        x += sideLength
      }
      rowStartsDark = !rowStartsDark
      y += sideLength
    }
  }

  override fun onBoundsChange(bounds: Rect) {
    val width = bounds.width()
    val height = bounds.height()
    sideLength = sideLength(idealSquareSideLength, width, height)
  }

  private fun sideLength(idealSquareSideLength: Float, width: Int, height: Int): Float {
    val doubleHeight = 2 * height
    val ratio = doubleHeight / width.toFloat()
    val startingHalfOfColumnCount = round(width / (2 * idealSquareSideLength)).toInt()
    for (halfOfColumnCount in startingHalfOfColumnCount..width) {
      val rowCount = ratio * halfOfColumnCount
      if (rowCount.isCloseToInteger()) {
        /*println("width: $width")
        println("height: $height")
        println("rowCount: $rowCount")
        println("columnCount: ${halfOfColumnCount * 2}")
        println("sideLength: ${height / rowCount}")
        println("sideLength: ${width / (2 * halfOfColumnCount).toFloat()}")*/
        return width / (2 * halfOfColumnCount).toFloat()
      }
    }
    throw AssertionError(
      "At worst, halfOfColumnCount will be width, and the side length will be 0.5."
    )
  }

  private fun Float.isCloseToInteger(): Boolean {
    val tolerance = 0.05
    val truncated = toInt()
    return this - truncated <= tolerance || truncated + 1 - this <= tolerance
  }

  override fun setAlpha(alpha: Int) {
    throw UnsupportedOperationException()
  }

  override fun setColorFilter(colorFilter: ColorFilter?) {
    throw UnsupportedOperationException()
  }

  override fun getOpacity() = PixelFormat.OPAQUE
}
