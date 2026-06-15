package com.apde2.preview

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

class SketchPreviewInstaller(private val activity: Activity) {
   fun needsInstallOrUpdate(): Boolean {
      val info = runCatching {
         if (Build.VERSION.SDK_INT >= 33) {
            activity.packageManager.getPackageInfo(PreviewProtocol.PREVIEW_PACKAGE, PackageManager.PackageInfoFlags.of(0))
         } else {
            @Suppress("DEPRECATION")
            activity.packageManager.getPackageInfo(PreviewProtocol.PREVIEW_PACKAGE, 0)
         }
      }.getOrNull() ?: return true
      val installedVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
         info.longVersionCode
      } else {
         @Suppress("DEPRECATION")
         info.versionCode.toLong()
      }
      return installedVersion < REQUIRED_VERSION_CODE
   }

   fun requestInstall(): Result<Unit> {
      return runCatching {
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.packageManager.canRequestPackageInstalls()) {
            activity.startActivity(
               Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                  .setData(Uri.parse("package:${activity.packageName}"))
            )
            error("Allow APDE 2.0 to install unknown apps, then press Run again to install Sketch Preview.")
         }

         val apk = copyBundledApk()
         val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.previewfiles", apk)
         val installIntent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
         activity.startActivity(installIntent)
      }
   }

   private fun copyBundledApk(): File {
      val target = File(activity.cacheDir, "sketch-preview-installer/sketch-preview.apk")
      target.parentFile?.mkdirs()
      activity.assets.open("sketch-preview/sketch-preview.apk").use { input ->
         target.outputStream().use { output -> input.copyTo(output) }
      }
      return target
   }

   private companion object {
      const val REQUIRED_VERSION_CODE = 10L
   }
}
