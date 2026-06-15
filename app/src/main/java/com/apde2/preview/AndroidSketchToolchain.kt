package com.apde2.preview

import android.content.Context
import java.io.File

data class AndroidSketchToolchain(
   val androidJar: File,
   val processingCoreJar: File
)

class ToolchainLocator(private val context: Context) {
   fun locate(): Result<AndroidSketchToolchain> {
      val root = File(context.filesDir, "toolchains/processing-android")
      val required = mapOf(
         "android.jar" to File(root, "android.jar"),
         "processing-core.jar" to File(root, "processing-core.jar")
      )
      runCatching {
         required.forEach { (name, file) ->
            if (!file.isFile) {
               copyAsset("toolchains/processing-android/$name", file)
            }
         }
      }.onFailure {
         return Result.failure(it)
      }
      val missing = required.filterValues { !it.isFile }.keys.sorted()
      if (missing.isNotEmpty()) {
         return Result.failure(IllegalStateException(
            "Processing Android toolchain is not installed. Missing: ${missing.joinToString(", ")}. " +
               "Rebuild the app so generated preview toolchain assets are packaged."
         ))
      }
      return Result.success(
         AndroidSketchToolchain(
            androidJar = required.getValue("android.jar"),
            processingCoreJar = required.getValue("processing-core.jar")
         )
      )
   }

   private fun copyAsset(assetPath: String, target: File) {
      target.parentFile?.mkdirs()
      context.assets.open(assetPath).use { input ->
         target.outputStream().use { output -> input.copyTo(output) }
      }
   }
}
