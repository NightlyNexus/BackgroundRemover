package com.nightlynexus.backgroundremover

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.widget.RelativeLayout

/* If we use centerInParent and the image (or progress) view is tall enough to push the
layout_below views below the RelativeLayout's visible bottom, the RelativeLayout will not
calculate the bottom margin correctly. So, when that would happen, we remove centerInParent
(and thus become effectively a LinearLayout). */
private class RelativeLayoutWhenShortLinearLayoutWhenTall(
  context: Context,
  attrs: AttributeSet
) : RelativeLayout(context, attrs) {
  private lateinit var loadingView: View
  private lateinit var imageView: View
  private lateinit var loadingViewLayoutParams: LayoutParams
  private lateinit var imageViewLayoutParams: LayoutParams
  private val localVisibleRect = Rect()

  override fun onFinishInflate() {
    super.onFinishInflate()
    loadingView = findViewById(R.id.output_single_loading)
    imageView = findViewById(R.id.output_single_image)
    loadingViewLayoutParams = loadingView.layoutParams as LayoutParams
    imageViewLayoutParams = imageView.layoutParams as LayoutParams
  }

  private var tryCenter = true

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    if (tryCenter) {
      loadingViewLayoutParams.addRule(CENTER_IN_PARENT)
      imageViewLayoutParams.addRule(CENTER_IN_PARENT)
      loadingView.layoutParams = loadingViewLayoutParams
      imageView.layoutParams = imageViewLayoutParams
    }
    super.onMeasure(widthMeasureSpec, heightMeasureSpec)
  }

  override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
    super.onLayout(changed, l, t, r, b)
    if (!tryCenter) {
      tryCenter = true
      return
    }
    if (fitsWithCenterInParent()) {
      return
    }
    tryCenter = false
    loadingViewLayoutParams.removeRule(CENTER_IN_PARENT)
    imageViewLayoutParams.removeRule(CENTER_IN_PARENT)
    loadingView.layoutParams = loadingViewLayoutParams
    imageView.layoutParams = imageViewLayoutParams
  }

  // Returns true if and only if any visible child view's bottom extends past this RelativeLayout's
  // visible bottom.
  private fun fitsWithCenterInParent(): Boolean {
    getLocalVisibleRect(localVisibleRect)
    val height = localVisibleRect.height()
    for (i in 0 until childCount) {
      val child = getChildAt(i)
      if (child.visibility == VISIBLE) {
        val childLayoutParams = child.layoutParams as LayoutParams
        val childBottom = child.bottom + childLayoutParams.bottomMargin
        if (childBottom > height) {
          return false
        }
      }
    }
    return true
  }
}
