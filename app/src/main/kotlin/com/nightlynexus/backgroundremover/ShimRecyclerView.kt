package com.nightlynexus.backgroundremover

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.WindowInsets
import androidx.recyclerview.widget.RecyclerView

private class ShimRecyclerView(
  context: Context,
  attrs: AttributeSet
) : RecyclerView(context, attrs) {
  private var insetTop = 0
  private val paint = Paint().apply { color = context.getColor(R.color.output_list_shim) }

  init {
    // TODO: API?
    val paddingTop = paddingTop
    setOnApplyWindowInsetsListener { v, insets ->
      insetTop = insets.getInsets(WindowInsets.Type.systemBars()).top
      v.setPadding(0, paddingTop + insetTop, 0, paddingBottom)
      insets
    }
  }

  override fun dispatchDraw(canvas: Canvas) {
    super.dispatchDraw(canvas)
    canvas.drawRect(0f, 0f, width.toFloat(), insetTop.toFloat(), paint)
  }
}
