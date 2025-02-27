package com.nightlynexus.backgroundremover

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.nightlynexus.backgroundremover.OutputViewController.ImageResult

internal class OutputItemViewController(
  private val loadingView: View,
  private val imageView: ImageView,
  private val fileNameView: TextView,
  private val saveButton: View?,
  private val saveLoadingView: View,
  private val savedFileNameView: TextView
) {
  private val context = loadingView.context
  private lateinit var imageResult: ImageResult

  init {
    imageView.background = CheckerboardDrawable(
      context.getColor(R.color.output_item_image_background_light),
      context.getColor(R.color.output_item_image_background_dark),
      context.resources.getDimension(R.dimen.output_item_image_background_side_length)
    )
  }

  fun setImageResult(imageResult: ImageResult) {
    this.imageResult = imageResult
    when (imageResult) {
      is ImageResult.SavedToDisk -> {
        imageView.setImageBitmap(imageResult.foreground)
        val fileName = imageResult.fileName
        imageView.contentDescription = context.getString(
          R.string.output_item_image_content_description,
          fileName
        )
        fileNameView.text = null
        val resultingPath = imageResult.resultingPath
        savedFileNameView.text = if (resultingPath == null) {
          context.getString(
            R.string.output_item_message_saved_to_disk_failed_to_get_path,
            fileName
          )
        } else {
          context.getString(
            R.string.output_item_message_saved_to_disk,
            resultingPath
          )
        }
        loadingView.visibility = View.GONE
        imageView.visibility = View.VISIBLE
        fileNameView.visibility = View.GONE
        saveButton?.visibility = View.INVISIBLE
        saveLoadingView.visibility = View.GONE
        savedFileNameView.visibility = View.VISIBLE
      }

      is ImageResult.LoadingSaveToDisk -> {
        imageView.setImageBitmap(imageResult.foreground)
        imageView.contentDescription = context.getString(
          R.string.output_item_image_content_description,
          imageResult.fileName
        )
        fileNameView.text = context.getString(
          R.string.output_item_message_extracted_foreground,
          imageResult.fileName
        )
        savedFileNameView.text = null
        loadingView.visibility = View.GONE
        imageView.visibility = View.VISIBLE
        // Make fileNameView invisible, not gone, to keep the output item at the same height after
        // the user clicks the save button.
        fileNameView.visibility = View.INVISIBLE
        saveButton?.visibility = View.INVISIBLE
        saveLoadingView.visibility = View.VISIBLE
        savedFileNameView.visibility = View.GONE
      }

      is ImageResult.ExtractedForeground -> {
        imageView.setImageBitmap(imageResult.foreground)
        imageView.contentDescription = context.getString(
          R.string.output_item_image_content_description,
          imageResult.fileName
        )
        fileNameView.text = context.getString(
          R.string.output_item_message_extracted_foreground,
          imageResult.fileName
        )
        savedFileNameView.text = null
        loadingView.visibility = View.GONE
        imageView.visibility = View.VISIBLE
        fileNameView.visibility = View.VISIBLE
        saveButton?.visibility = View.VISIBLE
        saveLoadingView.visibility = View.GONE
        savedFileNameView.visibility = View.GONE
      }

      is ImageResult.FailedToExtractForeground -> {
        // TODO: Set an error view.
        // TODO: We could have a retry button
        //  to move FailedToExtractForeground to the LoadingForeground state.
        imageView.setImageDrawable(null)
        imageView.contentDescription = null
        fileNameView.text = null
        savedFileNameView.setText(R.string.output_item_message_failed_to_extract_foreground)
        loadingView.visibility = View.GONE
        imageView.visibility = View.VISIBLE
        fileNameView.visibility = View.GONE
        saveButton?.visibility = View.INVISIBLE
        saveLoadingView.visibility = View.GONE
        savedFileNameView.visibility = View.VISIBLE
      }

      is ImageResult.LoadingForeground -> {
        imageView.setImageDrawable(null)
        imageView.contentDescription = null
        fileNameView.text = null
        savedFileNameView.text = null
        loadingView.visibility = View.VISIBLE
        imageView.visibility = View.GONE
        fileNameView.visibility = View.GONE
        saveButton?.visibility = View.INVISIBLE
        saveLoadingView.visibility = View.GONE
        savedFileNameView.visibility = View.GONE
      }
    }
  }

  // Not strictly necessary, but we release the Bitmap memory of the single output when we switch to
  // the multi-output view.
  fun clear() {
    imageView.setImageDrawable(null)
    imageView.contentDescription = null
    fileNameView.text = null
    savedFileNameView.text = null
  }

  fun needsChangeAnimation(update: OutputItemViewController): Boolean {
    val old = imageResult
    val new = update.imageResult
    return when (old) {
      is ImageResult.SavedToDisk -> {
        throw IllegalStateException("SavedToDisk is a terminal state. old: $old new: $new")
      }

      is ImageResult.LoadingSaveToDisk -> {
        if (new !is ImageResult.ExtractedForeground
          && new !is ImageResult.SavedToDisk) {
          throw IllegalStateException("The next state after LoadingSaveToDisk must be either" +
            "ExtractedForeground or SavedToDisk. old: $old new: $new")
        }
        false
      }

      is ImageResult.ExtractedForeground -> {
        if (new !is ImageResult.LoadingSaveToDisk) {
          throw IllegalStateException("The next state after ExtractedForeground must be " +
            "LoadingSaveToDisk. old: $old new: $new")
        }
        false
      }

      is ImageResult.FailedToExtractForeground -> {
        // TODO: Set an error view.
        // TODO: We could have a retry button
        //  to move FailedToExtractForeground to the LoadingForeground state.
        throw IllegalStateException(
          "FailedToExtractForeground is a terminal state. old: $old new: $new"
        )
      }

      is ImageResult.LoadingForeground -> {
        if (new !is ImageResult.FailedToExtractForeground
          && new !is ImageResult.ExtractedForeground) {
          throw IllegalStateException("The next state after LoadingForeground must be either" +
            "FailedToExtractForeground or ExtractedForeground. old: $old new: $new")
        }
        true
      }
    }
  }
}
