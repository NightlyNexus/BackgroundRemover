package com.nightlynexus.backgroundremover

import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import java.util.concurrent.Executor

internal class OutputViewController(
  private val outputListContainer: View,
  outputListView: RecyclerView,
  private val saveAllContainer: View,
  private val saveAllButton: View,
  private val imageExecutor: Executor,
  private val backgroundRemover: BackgroundRemover
) {
  private val mainHandler = Handler(Looper.getMainLooper())
  private val context = outputListView.context
  private val imageResults = mutableListOf<ImageResult>()
  private var backgroundRemoverCallback: BackgroundRemoverCallback? = null
  private var imageResultsInDisableSaveAllStateCount = 0
  private val adapter = Adapter()

  init {
    // TODO: API?
    outputListContainer.setOnApplyWindowInsetsListener { v, insets ->
      val systemBars = insets.getInsets(WindowInsets.Type.systemBars())
      v.setPadding(systemBars.left, 0, systemBars.right, 0)
      insets
    }
    saveAllContainer.setOnApplyWindowInsetsListener { v, insets ->
      val systemBars = insets.getInsets(WindowInsets.Type.systemBars())
      v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
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
        when (val imageResult = imageResults[i]) {
          is ImageResult.SavedToDisk -> {
            // Do nothing.
          }

          is ImageResult.LoadingSaveToDisk -> {
            // Do nothing.
          }

          is ImageResult.ExtractedForeground -> {
            val uri = imageResult.uri
            val fileName = imageResult.fileName
            val foreground = imageResult.foreground
            val saveBitmapAsPngRunnable = SaveBitmapAsPngRunnable(uri, fileName, foreground)
            imageResults[i] = ImageResult.LoadingSaveToDisk(
              uri,
              fileName,
              foreground,
              saveBitmapAsPngRunnable
            )
            incrementImageResultsInDisableSaveAllStateCount()
            adapter.notifyItemChanged(i)
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

    val size = uris.size
    val callback = BackgroundRemoverCallback()
    this.backgroundRemoverCallback = callback
    for (i in 0 until size) {
      val imageUri = uris[i]
      imageResults += ImageResult.LoadingForeground(imageUri)
      adapter.notifyItemInserted(i)
      backgroundRemover.removeBackground(context, imageUri, callback)
    }
  }

  fun setVisible(visible: Boolean) {
    if (visible) {
      outputListContainer.visibility = View.VISIBLE
      saveAllContainer.visibility = View.VISIBLE
    } else {
      outputListContainer.visibility = View.GONE
      saveAllContainer.visibility = View.GONE
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

    override fun onSuccess(imageUri: Uri, fileName: String, foreground: Bitmap) {
      if (canceled) {
        return
      }
      val existingIndex = findIndex(imageUri)
      imageResults[existingIndex] = ImageResult.ExtractedForeground(
        imageUri,
        fileName,
        foreground
      )
      adapter.notifyItemChanged(existingIndex)
      incrementExtractedForegroundOrFailureCount()
    }

    override fun onFailure(imageUri: Uri, e: Exception) {
      if (canceled) {
        return
      }
      val existingIndex = findIndex(imageUri)
      imageResults[existingIndex] = ImageResult.FailedToExtractForeground(imageUri)
      adapter.notifyItemChanged(existingIndex)
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

  private inner class SaveBitmapAsPngRunnable(
    private val uri: Uri,
    private val fileName: String,
    private val foreground: Bitmap
  ) : Runnable {
    var canceled = false

    override fun run() {
      val result = saveBitmapAsPng(context.contentResolver, foreground, fileName)
      mainHandler.post {
        if (canceled) {
          return@post
        }
        val existingIndex = findIndex(uri)
        when (result) {
          is SaveBitmapResult.Success -> {
            imageResults[existingIndex] = ImageResult.SavedToDisk(
              uri,
              fileName,
              foreground,
              result.filePath
            )
          }

          SaveBitmapResult.Failure -> {
            imageResults[existingIndex] = ImageResult.ExtractedForeground(
              uri,
              fileName,
              foreground
            )
            decrementImageResultsInDisableSaveAllStateCount()
            Toast.makeText(
              context,
              context.getString(R.string.output_item_toast_failed_to_save_to_disk, fileName),
              Toast.LENGTH_LONG
            ).show()
          }
        }
        adapter.notifyItemChanged(existingIndex)
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
      if (oldHolder == newHolder) {
        dispatchChangeFinished(oldHolder, true)
      } else {
        dispatchChangeFinished(oldHolder, true)
        dispatchChangeFinished(newHolder, false)
      }
      return false
    }
  }

  private inner class Adapter : RecyclerView.Adapter<Adapter.ViewHolder>() {
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
      val loadingView = itemView.findViewById<View>(R.id.loading)!!
      val imageView = itemView.findViewById<ImageView>(R.id.image)!!.apply {
        background = CheckerboardDrawable(
          context.getColor(R.color.output_item_image_background_light),
          context.getColor(R.color.output_item_image_background_dark),
          resources.getDimension(R.dimen.output_item_image_background_side_length)
        )
      }
      val messageView = itemView.findViewById<TextView>(R.id.message)!!
      val saveButton = itemView.findViewById<View>(R.id.save)!!.apply {
        setOnClickListener {
          val index = adapterPosition
          val success = imageResults[index] as ImageResult.ExtractedForeground
          val uri = success.uri
          val fileName = success.fileName
          val foreground = success.foreground
          val saveBitmapAsPngRunnable = SaveBitmapAsPngRunnable(uri, fileName, foreground)
          imageResults[index] = ImageResult.LoadingSaveToDisk(
            uri,
            fileName,
            foreground,
            saveBitmapAsPngRunnable
          )
          incrementImageResultsInDisableSaveAllStateCount()
          adapter.notifyItemChanged(index)
          imageExecutor.execute(saveBitmapAsPngRunnable)
        }
      }
      val saveLoadingView = itemView.findViewById<View>(R.id.save_loading)!!

      fun setImageResult(imageResult: ImageResult) {
        when (imageResult) {
          is ImageResult.SavedToDisk -> {
            imageView.setImageBitmap(imageResult.foreground)
            imageView.contentDescription = context.getString(
              R.string.output_item_image_content_description,
              imageResult.fileName
            )
            val resultingPath = imageResult.resultingPath
            if (resultingPath == null) {
              messageView.setText(R.string.output_item_message_saved_to_disk_failed_to_get_path)
            } else {
              messageView.text = context.getString(
                R.string.output_item_message_saved_to_disk,
                resultingPath
              )
            }
            loadingView.visibility = View.GONE
            imageView.visibility = View.VISIBLE
            messageView.visibility = View.VISIBLE
            saveButton.visibility = View.INVISIBLE
            saveLoadingView.visibility = View.GONE
          }

          is ImageResult.LoadingSaveToDisk -> {
            imageView.setImageBitmap(imageResult.foreground)
            loadingView.visibility = View.GONE
            imageView.visibility = View.VISIBLE
            messageView.visibility = View.INVISIBLE
            saveButton.visibility = View.INVISIBLE
            saveLoadingView.visibility = View.VISIBLE
          }

          is ImageResult.ExtractedForeground -> {
            imageView.setImageBitmap(imageResult.foreground)
            imageView.contentDescription = context.getString(
              R.string.output_item_image_content_description,
              imageResult.fileName
            )
            messageView.text = context.getString(
              R.string.output_item_message_extracted_foreground,
              imageResult.fileName
            )
            loadingView.visibility = View.GONE
            imageView.visibility = View.VISIBLE
            messageView.visibility = View.VISIBLE
            saveButton.visibility = View.VISIBLE
            saveLoadingView.visibility = View.GONE
          }

          is ImageResult.FailedToExtractForeground -> {
            // TODO: Set an error view.
            // TODO: We could have a retry button.
            imageView.setImageDrawable(null)
            messageView.setText(R.string.output_item_message_failed_to_extract_foreground)
            loadingView.visibility = View.GONE
            imageView.visibility = View.VISIBLE
            messageView.visibility = View.VISIBLE
            saveButton.visibility = View.INVISIBLE
            saveLoadingView.visibility = View.GONE
          }

          is ImageResult.LoadingForeground -> {
            loadingView.visibility = View.VISIBLE
            imageView.visibility = View.GONE
            messageView.visibility = View.INVISIBLE
            saveButton.visibility = View.INVISIBLE
            saveLoadingView.visibility = View.GONE
          }
        }
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
      holder.setImageResult(imageResult)
    }
  }

  private sealed interface ImageResult {
    val uri: Uri

    class SavedToDisk(
      override val uri: Uri,
      val fileName: String,
      val foreground: Bitmap,
      val resultingPath: String?
    ) : ImageResult

    class LoadingSaveToDisk(
      override val uri: Uri,
      val fileName: String,
      val foreground: Bitmap,
      val saveBitmapAsPngRunnable: SaveBitmapAsPngRunnable
    ) : ImageResult

    class ExtractedForeground(
      override val uri: Uri,
      val fileName: String,
      val foreground: Bitmap
    ) : ImageResult

    class FailedToExtractForeground(
      override val uri: Uri
    ) : ImageResult

    class LoadingForeground(
      override val uri: Uri
    ) : ImageResult
  }
}
