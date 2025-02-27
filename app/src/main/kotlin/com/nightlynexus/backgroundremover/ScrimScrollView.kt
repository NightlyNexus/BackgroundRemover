package com.nightlynexus.backgroundremover

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.widget.ScrollView

private class ScrimScrollView(
  context: Context,
  attrs: AttributeSet
) : ScrollView(context, attrs) {
  private val scrimHelper = ScrimHelper(this)

  override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
    super.onLayout(changed, l, t, r, b)
    checkTopOfTopView()
  }

  override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
    super.onScrollChanged(l, t, oldl, oldt)
    checkTopOfTopView()
  }

  private fun checkTopOfTopView() {
    if (childCount == 0) {
      scrimHelper.setShow(false)
      return
    }
    val top = getChildAt(0).top - scrollY
    val shouldShow = scrimHelper.shouldShowBasedOnTopOfTopView(top)
    scrimHelper.setShow(shouldShow)
  }

  override fun dispatchDraw(canvas: Canvas) {
    super.dispatchDraw(canvas)
    scrimHelper.dispatchDraw(canvas, topOffset = scrollY)
  }
}
