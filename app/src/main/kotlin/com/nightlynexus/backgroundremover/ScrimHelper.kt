package com.nightlynexus.backgroundremover

import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

internal class ScrimHelper(private val view: View) {
  private val scrim = Paint().apply {
    color = view.context.getColor(R.color.output_list_scrim)
  }
  private var insetTop = 0
  private var insetLeft = 0
  private var insetRight = 0
  private var show = false

  init {
    val paddingLeft = view.paddingLeft
    val paddingTop = view.paddingTop
    val paddingRight = view.paddingRight
    val paddingBottom = view.paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
      val systemBarsAndCutout = insets.getInsets(
        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
      )
      v.setPadding(
        paddingLeft + systemBarsAndCutout.left,
        paddingTop + systemBarsAndCutout.top,
        paddingRight + systemBarsAndCutout.right,
        paddingBottom
      )

      val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
      insetTop = systemBars.top
      insetLeft = systemBars.left
      insetRight = systemBars.right
      insets
    }
  }

  fun shouldShowBasedOnTopOfTopView(top: Int): Boolean {
    return top < insetTop
  }

  fun setShow(show: Boolean) {
    if (this.show != show) {
      this.show = show
      view.invalidate()
    }
  }

  fun dispatchDraw(canvas: Canvas, topOffset: Int = 0) {
    if (!show) {
      return
    }
    val left = insetLeft
    val right = view.width - insetRight
    canvas.drawRect(
      left.toFloat(),
      topOffset.toFloat(),
      right.toFloat(),
      topOffset + insetTop.toFloat(),
      scrim
    )
  }
}

