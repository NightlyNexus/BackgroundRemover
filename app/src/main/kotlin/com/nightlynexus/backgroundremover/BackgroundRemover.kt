package com.nightlynexus.backgroundremover

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import java.io.IOException
import java.util.concurrent.Executor
import kotlin.math.sqrt

internal class BackgroundRemover(private val executor: Executor) {
  private val maxPixels = 3072 * 4080
  private val mainHandler = Handler(Looper.getMainLooper())
  private val segmenter: SubjectSegmenter = SubjectSegmentation.getClient(
    SubjectSegmenterOptions.Builder()
      .setExecutor(executor)
      .enableForegroundBitmap()
      .build()
  )
  private val segmenterWithMask: SubjectSegmenter = SubjectSegmentation.getClient(
    SubjectSegmenterOptions.Builder()
      .setExecutor(executor)
      .enableForegroundBitmap()
      .enableForegroundConfidenceMask()
      .build()
  )

  @MainThread
  interface Callback {
    fun onSuccess(
      imageUri: Uri,
      fileName: String,
      foregroundToDisplay: Bitmap,
      foregroundToSave: Bitmap
    )

    fun onFailure(
      imageUri: Uri,
      e: Exception
    )
  }

  fun removeBackground(context: Context, imageUri: Uri, callback: Callback) {
    executor.execute {
      val fileName = fileName(context.contentResolver, imageUri)
      if (fileName == null) {
        mainHandler.post {
          callback.onFailure(imageUri, IOException("Failed to find image file name."))
        }
        return@execute
      }
      val inputImage = try {
        InputImage.fromFilePath(context, imageUri)
      } catch (e: IOException) {
        mainHandler.post {
          callback.onFailure(imageUri, e)
        }
        return@execute
      }
      val originalWidth = inputImage.width
      val originalHeight = inputImage.height
      val originalPixels = originalWidth * originalHeight
      if (originalPixels <= maxPixels) {
        segmenter.process(inputImage).addOnSuccessListener { result ->
          // Main thread.
          val foreground = result.foregroundBitmap!!
          callback.onSuccess(
            imageUri,
            fileName,
            foregroundToDisplay = foreground,
            foregroundToSave = foreground
          )
        }.addOnFailureListener { e ->
          // Main thread.
          callback.onFailure(
            imageUri,
            e
          )
        }
        return@execute
      }
      val scale = sqrt(maxPixels / originalPixels.toFloat())
      val newWidth = (originalWidth * scale).toInt()
      val newHeight = (originalHeight * scale).toInt()
      val scaleWidth = newWidth / originalWidth.toFloat()
      val scaleHeight = newHeight / originalHeight.toFloat()
      val originalBitmap = inputImage.bitmapInternal!!
      val newBitmap = Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)
      val newInputImage = InputImage.fromBitmap(newBitmap, 0)
      segmenterWithMask.process(newInputImage).addOnSuccessListener { result ->
        // Main thread.
        executor.execute {
          val mask = result.foregroundConfidenceMask!!
          val resultBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
          for (row in 0 until originalHeight) {
            val maskOffset = (row * scaleHeight).toInt() * newWidth
            for (column in 0 until originalWidth) {
              val maskIndex = maskOffset + (column * scaleWidth).toInt()
              val confidence = mask[maskIndex]
              if (confidence <= 0.5f) {
                // TODO: Process this Bitmap in chunks to batch this call.
                resultBitmap.setPixel(column, row, Color.TRANSPARENT)
              }
            }
          }
          mainHandler.post {
            callback.onSuccess(
              imageUri,
              fileName,
              foregroundToDisplay = result.foregroundBitmap!!,
              foregroundToSave = resultBitmap
            )
          }
        }
      }.addOnFailureListener { e ->
        // Main thread.
        callback.onFailure(
          imageUri,
          e
        )
      }
    }
  }
}
