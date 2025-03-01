package com.nightlynexus.backgroundremover

import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import java.util.concurrent.Executor

internal class OutputViewController(
  private val outputListView: RecyclerView,
  private val outputSingleContainerView: View,
  outputSingleLoading: View,
  outputSingleImage: ImageView,
  outputSingleFileName: TextView,
  outputSingleSaveLoading: View,
 outputSingleSavedFileName: TextView,
  saveAllContainer: View,
  private val saveAllButton: TextView,
  private val imageExecutor: Executor,
  private val backgroundRemover: BackgroundRemover
) {
  private val mainHandler = Handler(Looper.getMainLooper())
  private val context = outputListView.context
  private val imageResults = mutableListOf<ImageResult>()
  private var backgroundRemoverCallback: BackgroundRemoverCallback? = null
  private var imageResultsInDisableSaveAllStateCount = 0
  private val adapter = Adapter()
  private val outputSingleItemViewController = OutputItemViewController(
    outputSingleLoading,
    outputSingleImage,
    outputSingleFileName,
    saveButton = null,
    outputSingleSaveLoading,
    outputSingleSavedFileName
  )

  init {
    ViewCompat.setOnApplyWindowInsetsListener(saveAllContainer) { v, insets ->
      val systemBarsAndCutout = insets.getInsets(
        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
      )
      v.setPadding(
        systemBarsAndCutout.left,
        0,
        systemBarsAndCutout.right,
        systemBarsAndCutout.bottom
      )
      insets
    }

    outputListView.layoutManager = LinearLayoutManager(context)
    outputListView.addItemDecoration(DividerItemDecoration(
      AppCompatResources.getDrawable(context, R.drawable.output_item_divider)!!
    ))
    outputListView.itemAnimator = NoFlashItemAnimator()
    outputListView.adapter = adapter

    saveAllButton.isEnabled = false
    saveAllButton.setOnClickListener {
      for (i in imageResults.indices) {
        when (val oldImageResult = imageResults[i]) {
          is ImageResult.SavedToDisk -> {
            // Do nothing.
          }

          is ImageResult.LoadingSaveToDisk -> {
            // Do nothing.
          }

          is ImageResult.ExtractedForeground -> {
            val uri = oldImageResult.uri
            val fileName = oldImageResult.fileName
            val foregroundToDisplay = oldImageResult.foregroundToDisplay
            val foregroundToSave = oldImageResult.foregroundToSave
            val saveBitmapAsPngRunnable = SaveBitmapAsPngRunnable(
              uri,
              fileName,
              foregroundToDisplay,
              foregroundToSave
            )
            val newImageResult = ImageResult.LoadingSaveToDisk(
              uri,
              fileName,
              foregroundToDisplay,
              foregroundToSave,
              saveBitmapAsPngRunnable
            )
            imageResults[i] = newImageResult
            incrementImageResultsInDisableSaveAllStateCount()
            if (imageResults.size == 1) {
              outputSingleItemViewController.setImageResult(newImageResult)
            } else {
              adapter.notifyItemChanged(i)
            }
            imageExecutor.execute(saveBitmapAsPngRunnable)
          }

          is ImageResult.FailedToExtractForeground -> {
            // Do nothing. Already terminal.
          }

          is ImageResult.LoadingForeground -> {
            throw IllegalStateException(
              "ImageResult cannot be LoadingForeground. Index $i in $imageResults"
            )
          }
        }
      }
    }
  }

  fun setSelectedUris(uris: List<Uri>) {
    saveAllButton.isEnabled = false
    backgroundRemoverCallback?.cancel()
    imageResultsInDisableSaveAllStateCount = 0
    for (i in imageResults.indices) {
      val imageResult = imageResults[i]
      if (imageResult is ImageResult.LoadingSaveToDisk) {
        imageResult.saveBitmapAsPngRunnable.canceled = true
      }
    }
    val oldSize = imageResults.size
    imageResults.clear()

    adapter.notifyItemRangeRemoved(0, oldSize)
    outputSingleItemViewController.clear()

    val size = uris.size
    if (size == 1) {
      outputListView.visibility = View.GONE
      outputSingleContainerView.visibility = View.VISIBLE
      saveAllButton.setText(R.string.save_all_single)
    } else {
      outputListView.visibility = View.VISIBLE
      outputSingleContainerView.visibility = View.GONE
      saveAllButton.setText(R.string.save_all_multiple)
    }

    val callback = BackgroundRemoverCallback()
    this.backgroundRemoverCallback = callback
    for (i in uris.indices) {
      val imageUri = uris[i]
      val imageResult = ImageResult.LoadingForeground(imageUri)
      imageResults += imageResult
      if (size == 1) {
        outputSingleItemViewController.setImageResult(imageResult)
      } else {
        adapter.notifyItemInserted(i)
      }
      backgroundRemover.removeBackground(context, imageUri, callback)
    }
  }

  private fun incrementImageResultsInDisableSaveAllStateCount() {
    check(imageResultsInDisableSaveAllStateCount < imageResults.size)
    imageResultsInDisableSaveAllStateCount++
    if (imageResultsInDisableSaveAllStateCount == imageResults.size) {
      saveAllButton.isEnabled = false
    }
  }

  private fun decrementImageResultsInDisableSaveAllStateCount() {
    check(imageResultsInDisableSaveAllStateCount > 0)
    imageResultsInDisableSaveAllStateCount--
    saveAllButton.isEnabled = true
  }

  private fun findIndex(imageUri: Uri): Int {
    for (i in imageResults.indices) {
      val imageResult = imageResults[i]
      if (imageResult.uri == imageUri) {
        return i
      }
    }
    throw IllegalStateException("$imageUri is not in $imageResults")
  }

  private inner class BackgroundRemoverCallback : BackgroundRemover.Callback {
    private var canceled = false
    private var extractedForegroundOrFailureCount = 0

    fun cancel() {
      canceled = true
      extractedForegroundOrFailureCount = 0
    }

    override fun onSuccess(
      imageUri: Uri,
      fileName: String,
      foregroundToDisplay: Bitmap,
      foregroundToSave: Bitmap
    ) {
      if (canceled) {
        return
      }
      val existingIndex = findIndex(imageUri)
      val imageResult = ImageResult.ExtractedForeground(
        imageUri,
        fileName,
        foregroundToDisplay,
        foregroundToSave
      )
      imageResults[existingIndex] = imageResult
      if (imageResults.size == 1) {
        outputSingleItemViewController.setImageResult(imageResult)
      } else {
        adapter.notifyItemChanged(existingIndex)
      }
      incrementExtractedForegroundOrFailureCount()
    }

    override fun onFailure(
      imageUri: Uri,
      e: Exception
    ) {
      if (canceled) {
        return
      }
      val existingIndex = findIndex(imageUri)
      val imageResult = ImageResult.FailedToExtractForeground(imageUri)
      imageResults[existingIndex] = imageResult
      if (imageResults.size == 1) {
        outputSingleItemViewController.setImageResult(imageResult)
      } else {
        adapter.notifyItemChanged(existingIndex)
      }
      incrementExtractedForegroundOrFailureCount()
      incrementImageResultsInDisableSaveAllStateCount()
    }

    private fun incrementExtractedForegroundOrFailureCount() {
      check(extractedForegroundOrFailureCount < imageResults.size)
      extractedForegroundOrFailureCount++
      if (extractedForegroundOrFailureCount == imageResults.size) {
        saveAllButton.isEnabled = true
      }
    }
  }

  inner class SaveBitmapAsPngRunnable(
    private val uri: Uri,
    private val fileName: String,
    private val foregroundToDisplay: Bitmap,
    private val foregroundToSave: Bitmap
  ) : Runnable {
    var canceled = false

    override fun run() {
      val result = saveBitmapAsPng(context.contentResolver, foregroundToSave, fileName)
      mainHandler.post {
        if (canceled) {
          return@post
        }
        val existingIndex = findIndex(uri)
        when (result) {
          is SaveBitmapResult.Success -> {
            val resultingPath = result.filePath
            val imageResult = ImageResult.SavedToDisk(
              uri,
              fileName,
              foregroundToDisplay,
              foregroundToSave,
              resultingPath
            )
            imageResults[existingIndex] = imageResult
            if (imageResults.size == 1) {
              outputSingleItemViewController.setImageResult(imageResult)
            } else {
              adapter.notifyItemChanged(existingIndex)
            }
          }

          SaveBitmapResult.Failure -> {
            val imageResult = ImageResult.ExtractedForeground(
              uri,
              fileName,
              foregroundToDisplay,
              foregroundToSave
            )
            imageResults[existingIndex] = imageResult
            decrementImageResultsInDisableSaveAllStateCount()
            Toast.makeText(
              context,
              context.getString(R.string.output_item_toast_failed_to_save_to_disk, fileName),
              Toast.LENGTH_LONG
            ).show()
            if (imageResults.size == 1) {
              outputSingleItemViewController.setImageResult(imageResult)
            } else {
              adapter.notifyItemChanged(existingIndex)
            }
          }
        }
      }
    }
  }

  private class NoFlashItemAnimator : DefaultItemAnimator() {
    override fun animateChange(
      oldHolder: ViewHolder,
      newHolder: ViewHolder,
      fromX: Int,
      fromY: Int,
      toX: Int,
      toY: Int
    ): Boolean {
      oldHolder as Adapter.ViewHolder
      newHolder as Adapter.ViewHolder
      val oldController = oldHolder.outputItemViewController
      val newController = newHolder.outputItemViewController
      return if (oldController.needsChangeAnimation(newController)) {
        super.animateChange(oldHolder, newHolder, fromX, fromY, toX, toY)
      } else {
        if (oldHolder == newHolder) {
          dispatchChangeFinished(oldHolder, false)
        } else {
          dispatchChangeFinished(oldHolder, true)
          dispatchChangeFinished(newHolder, false)
        }
        false
      }
    }
  }

  private inner class Adapter : RecyclerView.Adapter<Adapter.ViewHolder>() {
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
      val outputItemViewController = run {
        val loadingView = itemView.findViewById<View>(R.id.loading)!!
        val imageView = itemView.findViewById<ImageView>(R.id.image)!!
        val fileNameView = itemView.findViewById<TextView>(R.id.file_name)!!
        val saveButton = itemView.findViewById<View>(R.id.save)!!.apply {
          setOnClickListener {
            val index = adapterPosition
            val success = imageResults[index] as ImageResult.ExtractedForeground
            val uri = success.uri
            val fileName = success.fileName
            val foregroundToDisplay = success.foregroundToDisplay
            val foregroundToSave = success.foregroundToSave
            val saveBitmapAsPngRunnable = SaveBitmapAsPngRunnable(
              uri,
              fileName,
              foregroundToDisplay,
              foregroundToSave
            )
            imageResults[index] = ImageResult.LoadingSaveToDisk(
              uri,
              fileName,
              foregroundToDisplay,
              foregroundToSave,
              saveBitmapAsPngRunnable
            )
            incrementImageResultsInDisableSaveAllStateCount()
            adapter.notifyItemChanged(index)
            imageExecutor.execute(saveBitmapAsPngRunnable)
          }
        }
        val saveLoadingView = itemView.findViewById<View>(R.id.save_loading)!!
        val savedFileNameView = itemView.findViewById<TextView>(R.id.saved_file_name)!!
        OutputItemViewController(
          loadingView,
          imageView,
          fileNameView,
          saveButton,
          saveLoadingView,
          savedFileNameView
        )
      }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
      val inflater = LayoutInflater.from(parent.context)
      val root = inflater.inflate(R.layout.output_item, parent, false)
      return ViewHolder(root)
    }

    override fun getItemCount(): Int {
      return imageResults.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
      val imageResult = imageResults[position]
      holder.outputItemViewController.setImageResult(imageResult)
    }
  }

  sealed interface ImageResult {
    val uri: Uri

    class SavedToDisk(
      override val uri: Uri,
      val fileName: String,
      val foregroundToDisplay: Bitmap,
      val foregroundToSave: Bitmap,
      val resultingPath: String?
    ) : ImageResult

    class LoadingSaveToDisk(
      override val uri: Uri,
      val fileName: String,
      val foregroundToDisplay: Bitmap,
      val foregroundToSave: Bitmap,
      val saveBitmapAsPngRunnable: SaveBitmapAsPngRunnable
    ) : ImageResult

    class ExtractedForeground(
      override val uri: Uri,
      val fileName: String,
      val foregroundToDisplay: Bitmap,
      val foregroundToSave: Bitmap
    ) : ImageResult

    class FailedToExtractForeground(
      override val uri: Uri
    ) : ImageResult

    class LoadingForeground(
      override val uri: Uri
    ) : ImageResult
  }
}
