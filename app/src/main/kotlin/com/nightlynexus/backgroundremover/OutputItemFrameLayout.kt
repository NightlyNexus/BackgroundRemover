package com.nightlynexus.backgroundremover

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout

// Makes the save loading view match the width and height of the save button it is taking the place
// of.
private class OutputItemFrameLayout(
  context: Context,
  attrs: AttributeSet
) : FrameLayout(context, attrs) {
  private lateinit var saveView: View
  private lateinit var saveLoadingView: View
  private lateinit var savedFileNameView: View
  private lateinit var saveLoadingViewLayoutParams: LayoutParams
  private lateinit var savedFileNameViewLayoutParams: LayoutParams

  override fun onFinishInflate() {
    super.onFinishInflate()
    saveLoadingView = findViewById(R.id.save_loading)
    saveView = findViewById(R.id.save)
    savedFileNameView = findViewById(R.id.saved_file_name)
    saveLoadingViewLayoutParams = saveLoadingView.layoutParams as LayoutParams
    savedFileNameViewLayoutParams = savedFileNameView.layoutParams as LayoutParams
  }

  override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
    super.onLayout(changed, l, t, r, b)
    val width = saveView.width
    val height = saveView.height
    if (saveLoadingViewLayoutParams.width != width
      || saveLoadingViewLayoutParams.height != height) {
      saveLoadingViewLayoutParams.width = width
      saveLoadingViewLayoutParams.height = height
      saveLoadingView.layoutParams = saveLoadingViewLayoutParams
    }
    if (savedFileNameViewLayoutParams.width != width) {
      savedFileNameViewLayoutParams.width = width
      savedFileNameView.layoutParams = savedFileNameViewLayoutParams
    }
  }
}
