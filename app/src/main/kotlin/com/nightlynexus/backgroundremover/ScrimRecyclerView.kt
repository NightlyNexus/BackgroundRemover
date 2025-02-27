package com.nightlynexus.backgroundremover

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

private class ScrimRecyclerView(
  context: Context,
  attrs: AttributeSet
) : RecyclerView(context, attrs) {
  private val scrimHelper = ScrimHelper(this)

  override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
    super.onLayout(changed, l, t, r, b)
    checkTopOfTopView()
  }

  override fun onScrolled(dx: Int, dy: Int) {
    checkTopOfTopView()
  }

  private fun checkTopOfTopView() {
    if (childCount == 0) {
      scrimHelper.setShow(false)
      return
    }
    val top = getChildAt(0).top
    val shouldShow = scrimHelper.shouldShowBasedOnTopOfTopView(top)
    if (shouldShow) {
      scrimHelper.setShow(true)
      return
    }
    val layoutManager = layoutManager as LinearLayoutManager
    val firstViewVisible = layoutManager.findFirstVisibleItemPosition() == 0
    if (firstViewVisible) {
      // Our first child is too low (shouldShow), and our first child is indeed the first item in
      // the adapter.
      scrimHelper.setShow(false)
    } else {
      // Our first child is too low (!shouldShow), but our first child is not the first item in the
      // adapter.
      scrimHelper.setShow(true)
    }
  }

  override fun dispatchDraw(canvas: Canvas) {
    super.dispatchDraw(canvas)
    scrimHelper.dispatchDraw(canvas)
  }
}
