package com.nightlynexus.backgroundremover

import android.app.Activity
import android.content.Intent
import android.content.Intent.ACTION_PICK
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executor

class LauncherActivity : AppCompatActivity() {
  private lateinit var imageExecutor: Executor
  private lateinit var backgroundRemover: BackgroundRemover
  private lateinit var selectContainerLauncher: ActivityResultLauncher<Intent>
  private lateinit var reselectContainerLauncher: ActivityResultLauncher<Intent>
  private var outputViewController: OutputViewController? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    val app = application as BackgroundRemoverApplication
    imageExecutor = app.imageExecutor
    backgroundRemover = app.backgroundRemover
    super.onCreate(savedInstanceState)

    WindowCompat.setDecorFitsSystemWindows(window, false)

    val contentView = findViewById<View>(android.R.id.content)
    setContentView(R.layout.launcher_activity_initial)
    // We remove this window insets listener and the padding when leaving the selection view.
    ViewCompat.setOnApplyWindowInsetsListener(contentView) { v, insets ->
      val systemBars = insets.getInsets(
        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
      )
      v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
      insets
    }
    val selectContainer = contentView.findViewById<View>(R.id.select_container)

    selectContainer.setOnClickListener { selectContainerLauncher.launch(imagePickerIntent()) }

    selectContainerLauncher = registerForActivityResult(
      ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
      if (result.resultCode != Activity.RESULT_OK) {
        return@registerForActivityResult
      }
      val data = result.data!!

      moveToSelectionView(contentView, data)
    }
    reselectContainerLauncher = registerForActivityResult(
      ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
      if (result.resultCode != Activity.RESULT_OK) {
        return@registerForActivityResult
      }
      val data = result.data!!

      val outputViewController = outputViewController
      // moveToSelectionView is null if and only if the Android recreated the Activity.
      if (outputViewController == null) {
        moveToSelectionView(contentView, data)
      } else {
        setUris(outputViewController, data)
      }
    }
  }

  private fun moveToSelectionView(contentView: View, data: Intent) {
    ViewCompat.setOnApplyWindowInsetsListener(contentView, null)
    contentView.setPadding(0, 0, 0, 0)
    setContentView(R.layout.launcher_activity_selected)
    val rootView = contentView.findViewById<View>(R.id.root)
    rootView.requestApplyInsets()
    val outputContainer = rootView.findViewById<View>(R.id.output_container)
    val outputListView = outputContainer.findViewById<RecyclerView>(R.id.output_list)
    val outputSingleContainerView = outputContainer.findViewById<View>(
      R.id.output_single_container
    )
    val outputSingleLoading = outputContainer.findViewById<View>(R.id.output_single_loading)
    val outputSingleImage = outputContainer.findViewById<ImageView>(R.id.output_single_image)
    val outputSingleFileName = outputContainer.findViewById<TextView>(
      R.id.output_single_file_name
    )
    val outputSingleSaveLoading = outputContainer.findViewById<View>(
      R.id.output_single_save_loading
    )
    val outputSingleSavedFileName = outputContainer.findViewById<TextView>(
      R.id.output_single_saved_file_name
    )
    val saveAllContainer = rootView.findViewById<View>(R.id.save_all_container)
    val saveAllButton = saveAllContainer.findViewById<TextView>(R.id.save_all)
    val reselectContainer = rootView.findViewById<View>(R.id.reselect_container)
    reselectContainer.setOnClickListener { reselectContainerLauncher.launch(imagePickerIntent()) }
    val outputViewController = OutputViewController(
      outputListView,
      outputSingleContainerView,
      outputSingleLoading,
      outputSingleImage,
      outputSingleFileName,
      outputSingleSaveLoading,
      outputSingleSavedFileName,
      saveAllContainer,
      saveAllButton,
      imageExecutor,
      backgroundRemover
    )
    this.outputViewController = outputViewController
    setUris(outputViewController, data)
  }

  private fun setUris(outputViewController: OutputViewController, data: Intent) {
    val singleData = data.data
    val uris: List<Uri>
    if (singleData == null) {
      val clipData = data.clipData!!
      val clipDataItemCount = clipData.itemCount
      uris = ArrayList(clipDataItemCount)
      for (i in 0 until clipDataItemCount) {
        uris += clipData.getItemAt(i).uri
      }
    } else {
      uris = listOf(singleData)
    }
    outputViewController.setSelectedUris(uris)
  }

  private fun imagePickerIntent(): Intent {
    val intent = Intent(ACTION_PICK)
    intent.setDataAndType(EXTERNAL_CONTENT_URI, "image/*")
    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
    return intent
  }
}
