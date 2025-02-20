package com.nightlynexus.backgroundremover

import android.app.Activity
import android.content.Intent
import android.content.Intent.ACTION_PICK
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
import android.view.View
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executor

class LauncherActivity : AppCompatActivity() {
  private lateinit var imageExecutor: Executor
  private lateinit var backgroundRemover: BackgroundRemover
  private lateinit var rootView: View
  private lateinit var selectContainer: View
  private lateinit var outputViewController: OutputViewController
  private lateinit var pickMedia: ActivityResultLauncher<Intent>

  override fun onCreate(savedInstanceState: Bundle?) {
    val app = application as BackgroundRemoverApplication
    imageExecutor = app.imageExecutor
    backgroundRemover = app.backgroundRemover
    super.onCreate(savedInstanceState)

    setContentView(R.layout.launcher_activity)
    rootView = findViewById(R.id.root)
    selectContainer = rootView.findViewById(R.id.select_container)
    val outputListContainer = rootView.findViewById<View>(R.id.output_list_container)
    val outputListView = outputListContainer.findViewById<RecyclerView>(R.id.output_list)
    val saveAllContainer = rootView.findViewById<View>(R.id.save_all_container)
    val saveAllButton = saveAllContainer.findViewById<View>(R.id.save_all)
    val reselectContainer = rootView.findViewById<View>(R.id.reselect_container)
    outputViewController = OutputViewController(
      outputListContainer,
      outputListView,
      saveAllContainer,
      saveAllButton,
      imageExecutor,
      backgroundRemover
    )

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
      outputViewController.setSelectedUris(uris)

      selectContainer.visibility = View.GONE
      outputViewController.setVisible(true)
    }

    val selectOnClickListener = View.OnClickListener {
      pickMedia.launch(Intent(ACTION_PICK, EXTERNAL_CONTENT_URI).apply {
        type = "image/*"
        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
      })
    }
    selectContainer.setOnClickListener(selectOnClickListener)
    reselectContainer.setOnClickListener(selectOnClickListener)

    selectContainer.visibility = View.VISIBLE
    outputViewController.setVisible(false)
  }
}
