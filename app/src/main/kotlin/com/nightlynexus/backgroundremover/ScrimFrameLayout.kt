package com.nightlynexus.backgroundremover

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

internal class ScrimFrameLayout(
  context: Context,
  attrs: AttributeSet
) : FrameLayout(context, attrs) {
  private val scrim = Paint().apply { color = context.getColor(R.color.output_list_scrim) }
  private val saveAllBarShadow = AppCompatResources.getDrawable(
    context,
    R.drawable.save_all_bar_shadow
  )!!
  private val saveAllBarShadowHeight = resources.getDimensionPixelSize(
    R.dimen.save_all_bar_shadow_height
  )
  private var insetTop = 0
  private var insetLeft = 0
  private var insetRight = 0
  private var showScrim = false

  init {
    ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
      val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
      insetTop = systemBars.top
      insetLeft = systemBars.left
      insetRight = systemBars.right
      insets
    }
  }

  fun showScrim(show: Boolean) {
    val oldShow = showScrim
    if (show != oldShow) {
      showScrim = show
      invalidate()
    }
  }

  override fun dispatchDraw(canvas: Canvas) {
    super.dispatchDraw(canvas)
    val left = insetLeft
    val right = width - insetRight
    if (showScrim) {
      canvas.drawRect(
        left.toFloat(),
        0f,
        right.toFloat(),
        insetTop.toFloat(),
        scrim
      )
    }
    saveAllBarShadow.setBounds(left, height - saveAllBarShadowHeight, right, height)
    saveAllBarShadow.draw(canvas)
  }
}
