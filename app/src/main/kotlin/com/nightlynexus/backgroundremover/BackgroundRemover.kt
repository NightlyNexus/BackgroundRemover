package com.nightlynexus.backgroundremover

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import java.io.IOException
import java.util.concurrent.Executor

internal class BackgroundRemover(private val executor: Executor) {
  private val segmenter: SubjectSegmenter = SubjectSegmentation.getClient(
    SubjectSegmenterOptions.Builder()
      .setExecutor(executor)
      .enableForegroundBitmap()
      .build()
  )

  interface Callback {
    fun onSuccess(fileName: String, foreground: Bitmap)
    fun onFailure(e: Exception)
  }

  fun removeBackground(context: Context, imageUri: Uri, callback: Callback) {
    executor.execute {
      val fileName = fileName(context.contentResolver, imageUri)
      if (fileName == null) {
        callback.onFailure(IOException("Failed to find image file name."))
        return@execute
      }
      val inputImage = try {
        InputImage.fromFilePath(context, imageUri)
      } catch (e: IOException) {
        callback.onFailure(e)
        return@execute
      }
      segmenter.process(inputImage).addOnSuccessListener { result ->
        // Main thread.
        callback.onSuccess(fileName, result.foregroundBitmap!!)
      }.addOnFailureListener { e ->
        // Main thread.
        callback.onFailure(e)
      }
    }
  }
}
