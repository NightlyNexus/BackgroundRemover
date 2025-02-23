package com.nightlynexus.backgroundremover

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
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

private const val directoryName = "BackgroundRemover"

@WorkerThread internal fun saveBitmapAsPng(
  contentResolver: ContentResolver,
  bitmap: Bitmap,
  fileName: String
): SaveBitmapResult {
  // Avoid .jpg.png resulting names.
  val fileNameWithoutExtension = fileName.substringBeforeLast(".")
  val values = ContentValues().apply {
    put(DISPLAY_NAME, fileNameWithoutExtension)
    put(RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}${File.separator}$directoryName")
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
  val updated = try {
    contentResolver.rigorousUpdate(outputUri, values, fileName, fileNameWithoutExtension)
  } catch (t: Throwable) {
    contentResolver.delete(outputUri, null, null)
    throw t
  }
  if (!updated) {
    contentResolver.delete(outputUri, null, null)
    return SaveBitmapResult.Failure
  }
  // We can't simply use the file path we give the ContentResolver because duplicate names can
  // result in a changed path (such as "my-image (2).png").
  val resultingFilePath = filePath(contentResolver, outputUri)
  return SaveBitmapResult.Success(resultingFilePath)
}

// Returns true if and only if ContentResolver.update runs.
// Returns false if and only if there are too many duplicate files to find a name for the file.
private fun ContentResolver.rigorousUpdate(
  uri: Uri,
  values: ContentValues,
  fileName: String,
  fileNameWithoutExtension: String
): Boolean {
  var error: Throwable
  try {
    update(uri, values, null, null)
    // The first try is a success. Get out of here.
    return true
  } catch (t: Throwable) {
    error = t
    // Fall through to the error if this is not an IllegalStateException.
    if (t is IllegalStateException) {
      val message = t.message
      // Fall through to the error if this is not the right kind of IllegalStateException.
      if (message != null && message.startsWith("Failed to build unique file")) {
        val directory = File(
          Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
          directoryName
        )
        val baseFile = File(directory, "$fileNameWithoutExtension.png")
        // Fall through to the error if the base file does not exist because the ContentResolver
        // should have been able to save the base file.
        if (baseFile.exists()) {
          var count = 2
          while (true) {
            val newFileNameWithoutExtension = "$fileNameWithoutExtension ($count)"
            val countFile = File(directory, "$newFileNameWithoutExtension.png")
            if (!countFile.exists()) {
              // We found an available file name. Let's try again.
              values.put(DISPLAY_NAME, newFileNameWithoutExtension)
              try {
                update(uri, values, null, null)
              } catch (tFromSecondTry: Throwable) {
                error = tFromSecondTry
                // That still did not work. Fall through to the error.
                break
              }
              // The fallback is a success. Get out of here.
              return true
            }
            if (count == Integer.MAX_VALUE) {
              // That is a lot of files. Give up. Get out of here.
              return false
            }
            count++
          }
        }
      }
    }
  }
  throw IllegalStateException("Error saving file: $fileName", error)
}
