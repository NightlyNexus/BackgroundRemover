package com.nightlynexus.backgroundremover

import android.app.Activity
import android.content.Intent
import android.content.Intent.ACTION_PICK
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
import android.view.View
import android.view.WindowInsets
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executor

class LauncherActivity : AppCompatActivity() {
  private lateinit var imageExecutor: Executor
  private lateinit var backgroundRemover: BackgroundRemover
  private lateinit var pickMedia: ActivityResultLauncher<Intent>

  override fun onCreate(savedInstanceState: Bundle?) {
    val app = application as BackgroundRemoverApplication
    imageExecutor = app.imageExecutor
    backgroundRemover = app.backgroundRemover
    super.onCreate(savedInstanceState)

    setContentView(R.layout.launcher_activity_initial)
    val selectRoot = findViewById<View>(R.id.select_root)
    // TODO: API?
    selectRoot.setOnApplyWindowInsetsListener { v, insets ->
      val systemBars = insets.getInsets(WindowInsets.Type.systemBars())
      v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
      insets
    }
    val selectContainer = selectRoot.findViewById<View>(R.id.select_container)

    val selectOnClickListener = View.OnClickListener {
      pickMedia.launch(Intent(ACTION_PICK, EXTERNAL_CONTENT_URI).apply {
        type = "image/*"
        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
      })
    }
    selectContainer.setOnClickListener(selectOnClickListener)
    pickMedia = registerForActivityResult(
      ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
      if (result.resultCode != Activity.RESULT_OK) {
        return@registerForActivityResult
      }
      val clipData = result.data!!.clipData!!
      val clipDataItemCount = clipData.itemCount
      val uris = ArrayList<Uri>(clipDataItemCount)
      for (i in 0 until clipDataItemCount) {
        uris += clipData.getItemAt(i).uri
      }

      setContentView(R.layout.launcher_activity_selected)
      val rootView = findViewById<View>(R.id.root)
      rootView.requestApplyInsets()
      val outputContainer = rootView.findViewById<ScrimFrameLayout>(R.id.output_container)
      val outputListView = outputContainer.findViewById<RecyclerView>(R.id.output_list)
      val outputSingleContainerView = outputContainer.findViewById<View>(R.id.output_single_container)
      val outputSingleLoading = outputContainer.findViewById<View>(R.id.output_single_loading)
      val outputSingleImage = outputContainer.findViewById<ImageView>(R.id.output_single_image)
      val outputSingleMessage = outputContainer.findViewById<TextView>(R.id.output_single_message)
      val outputSingleSaveLoading = outputContainer.findViewById<View>(
        R.id.output_single_save_loading
      )
      val saveAllContainer = rootView.findViewById<View>(R.id.save_all_container)
      val saveAllButton = saveAllContainer.findViewById<TextView>(R.id.save_all)
      rootView.findViewById<View>(R.id.reselect_container).apply {
        setOnClickListener(selectOnClickListener)
      }
      val outputViewController = OutputViewController(
        outputContainer,
        outputListView,
        outputSingleContainerView,
        outputSingleLoading,
        outputSingleImage,
        outputSingleMessage,
        outputSingleSaveLoading,
        saveAllContainer,
        saveAllButton,
        imageExecutor,
        backgroundRemover
      )
      outputViewController.setSelectedUris(uris)
    }
  }
}
