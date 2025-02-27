package com.nightlynexus.backgroundremover

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

private class SaveAllBottomBarShadowFrameLayout(
  context: Context,
  attrs: AttributeSet
) : FrameLayout(context, attrs) {
  private val saveAllBarShadow = AppCompatResources.getDrawable(
    context,
    R.drawable.save_all_bar_shadow
  )!!
  private val saveAllBarShadowHeight = resources.getDimensionPixelSize(
    R.dimen.save_all_bar_shadow_height
  )
  private var insetLeft = 0
  private var insetRight = 0

  init {
    ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
      val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
      insetLeft = systemBars.left
      insetRight = systemBars.right
      insets
    }
  }

  override fun dispatchDraw(canvas: Canvas) {
    super.dispatchDraw(canvas)
    val left = insetLeft
    val right = width - insetRight
    saveAllBarShadow.setBounds(left, height - saveAllBarShadowHeight, right, height)
    saveAllBarShadow.draw(canvas)
  }
}
