package com.apde2.preview

import java.io.File

enum class PreviewBuildPhase {
   PREPARING,
   PREPROCESSING,
   COMPILING,
   PACKAGING,
   LAUNCHING
}

data class PreviewBuildStatus(
   val phase: PreviewBuildPhase,
   val message: String
)

data class PreviewDiagnostic(
   val file: String,
   val line: Int,
   val column: Int,
   val message: String
) {
   override fun toString(): String {
      val location = buildString {
         if (file.isNotBlank()) {
            append(file)
         }
         if (line > 0) {
            if (isNotEmpty()) append(':')
            append(line)
            if (column > 0) append(":").append(column)
         }
      }
      return if (location.isEmpty()) message else "$location: $message"
   }
}

data class SketchSettings(
   val displayName: String,
   val packageName: String,
   val className: String,
   val versionCode: Int,
   val versionName: String,
   val orientation: String,
   val minSdk: Int,
   val targetSdk: Int,
   val permissions: List<String>,
   val fullscreen: Boolean
)

data class SketchPreviewRequest(
   val projectDir: File,
   val projectName: String,
   val settings: SketchSettings
)

data class SketchSnapshot(
   val root: File,
   val pdeFiles: List<File>,
   val dataDir: File?,
   val assetsDir: File?,
   val codeDir: File?,
   val androidDir: File?,
   val settings: SketchSettings
)

data class GeneratedSketchProject(
   val root: File,
   val sourceDir: File,
   val generatedSource: File,
   val dataDir: File?,
   val packageName: String,
   val className: String,
   val cacheKey: String
)

data class BuiltSketch(
   val runId: String,
   val dexFile: File,
   val dataZip: File?,
   val dexedLibraries: List<File>,
   val packageName: String,
   val className: String,
   val orientation: String
)

sealed class PreviewBuildOutcome {
   data class Success(val sketch: BuiltSketch) : PreviewBuildOutcome()
   data class Failure(val message: String, val diagnostics: List<PreviewDiagnostic> = emptyList()) : PreviewBuildOutcome()
}
