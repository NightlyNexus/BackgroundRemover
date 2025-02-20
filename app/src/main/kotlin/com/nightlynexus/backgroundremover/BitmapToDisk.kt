package com.nightlynexus.backgroundremover

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import android.provider.MediaStore.Images.Media.DISPLAY_NAME
import android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
import android.provider.MediaStore.Images.Media.RELATIVE_PATH
import androidx.annotation.WorkerThread
import java.io.File
import java.io.IOException

internal sealed interface SaveBitmapResult {
  // filePath is null if and only if we successfully save the file but fail to get the resulting
  // file path.
  class Success(val filePath: String?) : SaveBitmapResult
  object Failure : SaveBitmapResult
}

@WorkerThread internal fun saveBitmapAsPng(
  contentResolver: ContentResolver,
  bitmap: Bitmap,
  fileName: String
): SaveBitmapResult {
  val values = ContentValues().apply {
    // Avoid .jpg.png resulting names.
    val fileNameWithoutExtension = fileName.substringBeforeLast(".")
    put(DISPLAY_NAME, fileNameWithoutExtension)
    put(RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}${File.separator}BackgroundRemover")
    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
    put(MediaStore.Images.Media.IS_PENDING, 1)
  }

  val outputUri = contentResolver.insert(EXTERNAL_CONTENT_URI, values)!!
  val outputStream = contentResolver.openOutputStream(outputUri)
  if (outputStream == null) {
    contentResolver.delete(outputUri, null, null)
    return SaveBitmapResult.Failure
  }
  try {
    outputStream.use { out ->
      if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
        contentResolver.delete(outputUri, null, null)
        return SaveBitmapResult.Failure
      }
    }
  } catch (e: IOException) {
    contentResolver.delete(outputUri, null, null)
    return SaveBitmapResult.Failure
  }
  // We clear because we are only updating the IS_PENDING flag.
  values.clear()
  values.put(MediaStore.Images.Media.IS_PENDING, 0)
  contentResolver.update(outputUri, values, null, null)
  // We can't simply use the file path we give the ContentResolver because duplicate names can
  // result in a changed path (such as "my-image (2).png").
  val resultingFilePath = filePath(contentResolver, outputUri)
  return SaveBitmapResult.Success(resultingFilePath)
}
