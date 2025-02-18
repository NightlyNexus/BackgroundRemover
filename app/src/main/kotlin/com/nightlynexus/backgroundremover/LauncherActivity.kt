package com.nightlynexus.backgroundremover

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.Intent.ACTION_PICK
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.MediaStore.Images.Media.DISPLAY_NAME
import android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
import android.provider.MediaStore.Images.Media.RELATIVE_PATH
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.IOException
import java.util.concurrent.Executor

class LauncherActivity : AppCompatActivity() {
  private lateinit var imageExecutor: Executor
  private lateinit var backgroundRemover: BackgroundRemover
  private lateinit var rootView: View
  private lateinit var selectImageView: View
  private lateinit var selectTextView: View
  private lateinit var loadingView: View
  private lateinit var outputView: ImageView
  private lateinit var saveView: View
  private lateinit var pickMedia: ActivityResultLauncher<Intent>
  private lateinit var selectedFileName: String

  override fun onCreate(savedInstanceState: Bundle?) {
    val app = application as BackgroundRemoverApplication
    imageExecutor = app.imageExecutor
    backgroundRemover = app.backgroundRemover
    super.onCreate(savedInstanceState)

    setContentView(R.layout.launcher_activity)
    rootView = findViewById(R.id.root)
    selectImageView = rootView.findViewById(R.id.select_image)
    selectTextView = rootView.findViewById(R.id.select_text)
    loadingView = rootView.findViewById(R.id.loading)
    outputView = rootView.findViewById(R.id.output)
    saveView = rootView.findViewById(R.id.save)

    val callback = object : BackgroundRemover.Callback {
      override fun onSuccess(fileName: String, foreground: Bitmap) {
        selectedFileName = fileName
        outputView.setImageBitmap(foreground)
        saveView.setOnClickListener {
          selectImageView.visibility = View.VISIBLE
          selectTextView.visibility = View.VISIBLE
          loadingView.visibility = View.GONE
          outputView.visibility = View.GONE
          saveView.visibility = View.GONE
          rootView.isClickable = true
          saveBitmapAsPng(foreground)
        }

        loadingView.visibility = View.GONE
        outputView.visibility = View.VISIBLE
        saveView.visibility = View.VISIBLE
      }

      override fun onFailure(e: Exception) {
        selectImageView.visibility = View.VISIBLE
        selectTextView.visibility = View.VISIBLE
        loadingView.visibility = View.GONE
        outputView.visibility = View.GONE
        saveView.visibility = View.GONE
        rootView.isClickable = true
        val message = getString(R.string.background_remover_failed_toast, e.message)
        Toast.makeText(this@LauncherActivity, message, Toast.LENGTH_LONG).show()
      }
    }

    pickMedia = registerForActivityResult(
      ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
      if (result.resultCode != Activity.RESULT_OK) {
        rootView.isClickable = true
        return@registerForActivityResult
      }
      val imageUri = result.data!!.data!!

      selectImageView.visibility = View.GONE
      selectTextView.visibility = View.GONE
      loadingView.visibility = View.VISIBLE

      backgroundRemover.removeBackground(this, imageUri, callback)
    }

    rootView.setOnClickListener {
      rootView.isClickable = false
      pickMedia.launch(Intent(ACTION_PICK, EXTERNAL_CONTENT_URI))
    }

    selectImageView.visibility = View.VISIBLE
    selectTextView.visibility = View.VISIBLE
    loadingView.visibility = View.GONE
    outputView.visibility = View.GONE
    saveView.visibility = View.GONE
  }

  private fun saveBitmapAsPng(bitmap: Bitmap) {
    val contentResolver = contentResolver
    imageExecutor.execute {
      val values = ContentValues().apply {
        // Avoid .jpg.png resulting names.
        val fileNameWithoutExtension = selectedFileName.substringBeforeLast(".")
        put(DISPLAY_NAME, fileNameWithoutExtension)
        put(RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}${File.separator}BackgroundRemover")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.IS_PENDING, 1)
      }

      val outputUri = contentResolver.insert(EXTERNAL_CONTENT_URI, values)!!
      val outputStream = contentResolver.openOutputStream(outputUri)
      if (outputStream == null) {
        contentResolver.delete(outputUri, null, null)
        runOnUiThread {
          Toast.makeText(this, R.string.save_toast_failed, Toast.LENGTH_LONG).show()
        }
        return@execute
      }
      try {
        outputStream.use { out ->
          if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
            contentResolver.delete(outputUri, null, null)
            runOnUiThread {
              Toast.makeText(this, R.string.save_toast_failed, Toast.LENGTH_LONG).show()
            }
            return@execute
          }
        }
      } catch (e: IOException) {
        contentResolver.delete(outputUri, null, null)
        runOnUiThread {
          Toast.makeText(this, R.string.save_toast_failed, Toast.LENGTH_LONG).show()
        }
        return@execute
      }
      // We clear because we are only updating the IS_PENDING flag.
      values.clear()
      values.put(MediaStore.Images.Media.IS_PENDING, 0)
      contentResolver.update(outputUri, values, null, null)
      // We can't simply use the file path we give the ContentResolver because duplicate names can
      // result in a changed path (such as "my-image (2).png").
      val resultingFilePath = filePath(contentResolver, outputUri)
      runOnUiThread {
        val message = if (resultingFilePath == null) {
          getText(R.string.save_toast_success_failed_to_get_path)
        } else {
          getString(R.string.save_toast_success, resultingFilePath)
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
      }
    }
  }
}
