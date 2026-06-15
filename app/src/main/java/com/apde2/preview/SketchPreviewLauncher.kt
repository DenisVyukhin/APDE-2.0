package com.apde2.preview

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

class SketchPreviewLauncher(private val activity: Activity) {
   private val installer = SketchPreviewInstaller(activity)

   fun ensureAvailable(): Result<Unit> {
      return runCatching { ensureAvailableOrRequestInstall() }
   }

   fun launch(sketch: BuiltSketch): Result<Unit> {
      return runCatching {
         ensureAvailableOrRequestInstall()
         val dexUri = share(sketch.dexFile)
         val dataUri = sketch.dataZip?.let(::share)
         val libUris = sketch.dexedLibraries.map { share(it).toString() }.toTypedArray()
         val intent = Intent(PreviewProtocol.RUN_ACTION)
            .setPackage(PreviewProtocol.PREVIEW_PACKAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .putExtra(PreviewProtocol.EXTRA_RUN_ID, sketch.runId)
            .putExtra(PreviewProtocol.EXTRA_DEX_URI, dexUri.toString())
            .putExtra(PreviewProtocol.EXTRA_DATA_URI, dataUri?.toString().orEmpty())
            .putExtra(PreviewProtocol.EXTRA_DEXED_LIB_URIS, libUris)
            .putExtra(PreviewProtocol.EXTRA_PACKAGE_NAME, sketch.packageName)
            .putExtra(PreviewProtocol.EXTRA_CLASS_NAME, sketch.className)
            .putExtra(PreviewProtocol.EXTRA_ORIENTATION, sketch.orientation)
         val resolved = intent.resolveActivity(activity.packageManager)
            ?: error("Sketch Preview is unavailable. Press Run again to install or update it.")
         grant(dexUri)
         dataUri?.let(::grant)
         libUris.forEach { grant(Uri.parse(it)) }
         intent.component = resolved
         activity.startActivity(intent)
      }
   }

   fun stop() {
      activity.sendBroadcast(Intent(PreviewProtocol.STOP_ACTION).setPackage(PreviewProtocol.PREVIEW_PACKAGE))
   }

   private fun share(file: File): Uri {
      return FileProvider.getUriForFile(activity, "${activity.packageName}.previewfiles", file)
   }

   private fun grant(uri: Uri) {
      activity.grantUriPermission(PreviewProtocol.PREVIEW_PACKAGE, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
   }

   private fun ensureAvailableOrRequestInstall() {
      val resolved = Intent(PreviewProtocol.RUN_ACTION)
         .setPackage(PreviewProtocol.PREVIEW_PACKAGE)
         .resolveActivity(activity.packageManager)
      if (resolved == null || installer.needsInstallOrUpdate()) {
         installer.requestInstall().getOrThrow()
         error("Sketch Preview must be installed or updated. The installer has been opened; press Run again after installation.")
      }
   }
}
