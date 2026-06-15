package com.apde2.preview

import android.app.Activity
import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

class SketchPreviewController(private val activity: Activity) {
   interface Callback {
      fun onProgress(status: PreviewBuildStatus)
      fun onDiagnostic(diagnostic: PreviewDiagnostic)
      fun onSuccess()
      fun onFailure(message: String)
      fun onStopped()
   }

   private val main = Handler(Looper.getMainLooper())
   private val executor: ExecutorService = Executors.newSingleThreadExecutor()
   private val settingsReader = SketchSettingsReader(activity)
   private val pipeline = ProcessingAndroidPreviewPipeline(activity)
   private val launcher = SketchPreviewLauncher(activity)
   private var currentTask: Future<*>? = null

   fun run(projectDir: File, projectName: String, callback: Callback) {
      stop()
      val available = launcher.ensureAvailable()
      if (available.isFailure) {
         callback.onFailure(available.exceptionOrNull()?.message ?: "Could not open the Sketch Preview installer.")
         return
      }
      val settings = settingsReader.read(projectName)
      val request = SketchPreviewRequest(projectDir, projectName, settings)
      currentTask = executor.submit {
         val outcome = pipeline.build(request) { status -> post { callback.onProgress(status) } }
         if (Thread.currentThread().isInterrupted) {
            post { callback.onStopped() }
            return@submit
         }
         when (outcome) {
            is PreviewBuildOutcome.Success -> {
               post {
                  callback.onProgress(PreviewBuildStatus(PreviewBuildPhase.LAUNCHING, "Launching sketch preview."))
                  val launched = launcher.launch(outcome.sketch)
                  launched.fold(
                     onSuccess = { callback.onSuccess() },
                     onFailure = { callback.onFailure(it.message ?: "Could not launch sketch preview.") }
                  )
               }
            }
            is PreviewBuildOutcome.Failure -> {
               post {
                  outcome.diagnostics.forEach(callback::onDiagnostic)
                  callback.onFailure(outcome.message)
               }
            }
         }
      }
   }

   fun stop() {
      currentTask?.cancel(true)
      currentTask = null
      launcher.stop()
   }

   fun close() {
      stop()
      executor.shutdownNow()
   }

   private fun post(action: () -> Unit) {
      main.post(action)
   }
}
