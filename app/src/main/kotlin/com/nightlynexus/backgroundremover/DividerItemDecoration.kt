package com.nightlynexus.backgroundremover

import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.roundToInt

/**
 * Copied from [androidx.recyclerview.widget.DividerItemDecoration].
 * <p>Shows the divider under every item except the last one.
 */
internal class DividerItemDecoration(
  private val divider: Drawable
) : RecyclerView.ItemDecoration() {
  private val bounds = Rect()

  override fun onDraw(
    c: Canvas,
    parent: RecyclerView,
    state: RecyclerView.State
  ) {
    drawVertical(c, parent)
  }

  private fun drawVertical(
    canvas: Canvas,
    parent: RecyclerView
  ) {
    val reversed = (parent.layoutManager as LinearLayoutManager).reverseLayout
    val skipIndex = if (reversed) {
      0
    } else {
      parent.adapter!!.itemCount - 1
    }

    canvas.save()
    val left: Int
    val right: Int
    if (parent.clipToPadding) {
      left = parent.paddingLeft
      right = parent.width - parent.paddingRight
      canvas.clipRect(
        left, parent.paddingTop, right,
        parent.height - parent.paddingBottom
      )
    } else {
      left = 0
      right = parent.width
    }
    val childCount = parent.childCount
    for (i in 0 until childCount) {
      val child = parent.getChildAt(i)
      if (parent.getChildAdapterPosition(child) != skipIndex) {
        parent.getDecoratedBoundsWithMargins(child, bounds)
        val bottom = bounds.bottom + child.translationY.roundToInt()
        val top = bottom - divider.intrinsicHeight
        divider.setBounds(left, top, right, bottom)
        divider.draw(canvas)
      }
    }
    canvas.restore()
  }

  override fun getItemOffsets(
    outRect: Rect,
    view: View,
    parent: RecyclerView,
    state: RecyclerView.State
  ) {
    val reversed = (parent.layoutManager as LinearLayoutManager).reverseLayout
    val skipIndex = if (reversed) {
      0
    } else {
      parent.adapter!!.itemCount - 1
    }

    val dividerHeight = if (parent.getChildAdapterPosition(
        view
      ) == skipIndex
    ) 0 else divider.intrinsicHeight
    outRect[0, 0, 0] = dividerHeight
  }
}
