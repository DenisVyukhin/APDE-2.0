package com.apde2.preview

import android.content.Context
import java.io.File

class ProcessingAndroidPreviewPipeline(
   context: Context,
   cacheRoot: File = File(context.cacheDir, "sketch-preview")
) {
   private val snapshotter = SketchSnapshotter(cacheRoot)
   private val generator = ProcessingSourceGenerator(cacheRoot)
   private val toolchains = ToolchainLocator(context)

   fun build(
      request: SketchPreviewRequest,
      progress: (PreviewBuildStatus) -> Unit
   ): PreviewBuildOutcome {
      progress(PreviewBuildStatus(PreviewBuildPhase.PREPARING, "Preparing project snapshot."))
      val snapshot = runCatching { snapshotter.snapshot(request) }.getOrElse {
         return PreviewBuildOutcome.Failure(it.message ?: "Could not snapshot sketch.")
      }

      progress(PreviewBuildStatus(PreviewBuildPhase.PREPROCESSING, "Generating Processing Android source."))
      val generated = runCatching { generator.generate(snapshot) }.getOrElse {
         return PreviewBuildOutcome.Failure(it.message ?: "Could not generate sketch source.")
      }

      progress(PreviewBuildStatus(PreviewBuildPhase.COMPILING, "Checking Processing Android toolchain."))
      val toolchain = toolchains.locate().getOrElse {
         return PreviewBuildOutcome.Failure(it.message ?: "Processing Android toolchain is unavailable.")
      }

      val compiler = AndroidSketchCompiler(toolchain)
      progress(PreviewBuildStatus(PreviewBuildPhase.COMPILING, "Compiling sketch classes."))
      return compiler.compile(generated, request.settings)
   }
}
