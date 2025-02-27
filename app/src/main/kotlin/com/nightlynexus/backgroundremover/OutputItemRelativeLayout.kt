package com.nightlynexus.backgroundremover

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.RelativeLayout

// Makes the initial loading view match the height of the side message and save container.
private class OutputItemRelativeLayout(
  context: Context,
  attrs: AttributeSet
) : RelativeLayout(context, attrs) {
  private lateinit var loadingView: View
  private lateinit var messageAndSaveContainer: View
  private lateinit var loadingViewLayoutParams: LayoutParams

  override fun onFinishInflate() {
    super.onFinishInflate()
    loadingView = findViewById(R.id.loading)
    messageAndSaveContainer = findViewById(R.id.message_and_save_container)
    loadingViewLayoutParams = loadingView.layoutParams as LayoutParams
  }

  override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
    super.onLayout(changed, l, t, r, b)
    val height = messageAndSaveContainer.height
    if (loadingViewLayoutParams.height != height) {
      loadingViewLayoutParams.height = height
      loadingView.layoutParams = loadingViewLayoutParams
    }
  }
}
