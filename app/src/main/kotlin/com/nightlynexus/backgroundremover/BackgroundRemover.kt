package com.nightlynexus.backgroundremover

import android.content.Context
import android.graphics.Bitmap
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

internal class BackgroundRemover(private val executor: Executor) {
  private val mainHandler = Handler(Looper.getMainLooper())
  private val segmenter: SubjectSegmenter = SubjectSegmentation.getClient(
    SubjectSegmenterOptions.Builder()
      .setExecutor(executor)
      .enableForegroundBitmap()
      .build()
  )

  @MainThread
  interface Callback {
    fun onSuccess(imageUri: Uri, fileName: String, foreground: Bitmap)
    fun onFailure(imageUri: Uri, e: Exception)
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
      segmenter.process(inputImage).addOnSuccessListener { result ->
        // Main thread.
        callback.onSuccess(imageUri, fileName, result.foregroundBitmap!!)
      }.addOnFailureListener { e ->
        // Main thread.
        callback.onFailure(imageUri, e)
      }
    }
  }
}
