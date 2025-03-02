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
  private val confidenceThreshold = 0.5f
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
          if (originalWidth > maxPixels) {
            val pixelBuffer = IntArray(maxPixels)
            var column = 0
            var row = 0
            var maskOffset = 0
            while (row != originalHeight) {
              val columnsInPixelBuffer = if (column + maxPixels > originalWidth) {
                originalWidth - column
              } else {
                maxPixels
              }
              val x = column
              val y = row
              resultBitmap.getPixels(
                pixelBuffer,
                0,
                originalWidth,
                x,
                y,
                columnsInPixelBuffer,
                1
              )
              for (pixelBufferIndex in 0 until columnsInPixelBuffer) {
                val maskIndex = maskOffset + (column * scaleWidth).toInt()
                val confidence = mask[maskIndex]
                if (confidence <= 0.5f) {
                  pixelBuffer[pixelBufferIndex] = Color.TRANSPARENT
                }
                column++
              }
              resultBitmap.setPixels(
                pixelBuffer,
                0,
                originalWidth,
                x,
                y,
                columnsInPixelBuffer,
                1
              )
              if (column == originalWidth) {
                column = 0
                row++
                maskOffset = (row * scaleHeight).toInt() * newWidth
              }
            }
          } else {
            val maxRowsInPixelBuffer = maxPixels % originalWidth
            val pixelBufferSize = originalWidth * maxRowsInPixelBuffer
            val pixelBuffer = IntArray(pixelBufferSize)
            var row = 0
            while (row != originalHeight) {
              val rowsInPixelBuffer = if (row + maxRowsInPixelBuffer > originalHeight) {
                originalHeight - row
              } else {
                maxRowsInPixelBuffer
              }
              val y = row
              resultBitmap.getPixels(
                pixelBuffer,
                0,
                originalWidth,
                0,
                y,
                originalWidth,
                rowsInPixelBuffer
              )
              var pixelBufferIndex = 0
              for (i in 0 until rowsInPixelBuffer) {
                val maskOffset = (row * scaleHeight).toInt() * newWidth
                for (column in 0 until originalWidth) {
                  val maskIndex = maskOffset + (column * scaleWidth).toInt()
                  val confidence = mask[maskIndex]
                  if (confidence <= confidenceThreshold) {
                    pixelBuffer[pixelBufferIndex] = Color.TRANSPARENT
                  }
                  pixelBufferIndex++
                }
                row++
              }
              resultBitmap.setPixels(
                pixelBuffer,
                0,
                originalWidth,
                0,
                y,
                originalWidth,
                rowsInPixelBuffer
              )
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
