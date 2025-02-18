package com.nightlynexus.backgroundremover

import android.app.Application
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class BackgroundRemoverApplication : Application() {
  internal lateinit var imageExecutor: Executor
  internal lateinit var backgroundRemover: BackgroundRemover

  override fun onCreate() {
    super.onCreate()
    imageExecutor = Executors.newCachedThreadPool()
    backgroundRemover = BackgroundRemover(imageExecutor)
  }
}
