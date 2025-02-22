package com.nightlynexus.backgroundremover

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.WindowInsets
import android.widget.FrameLayout

internal class ScrimFrameLayout(
  context: Context,
  attrs: AttributeSet
) : FrameLayout(context, attrs) {
  private val paint = Paint().apply { color = context.getColor(R.color.output_list_shim) }
  private var insetTop = 0
  private var showScrim = false

  init {
    // TODO: API?
    setOnApplyWindowInsetsListener { v, insets ->
      val systemBars = insets.getInsets(WindowInsets.Type.systemBars())
      v.setPadding(systemBars.left, 0, systemBars.right, 0)
      insetTop = systemBars.top
      insets
    }
  }

  fun showScrim(show: Boolean) {
    showScrim = show
    invalidate()
  }

  override fun dispatchDraw(canvas: Canvas) {
    super.dispatchDraw(canvas)
    if (showScrim) {
      canvas.drawRect(0f, 0f, width.toFloat(), insetTop.toFloat(), paint)
    }
  }
}
