package com.apde2.preview

import java.io.File
import java.security.MessageDigest

class SketchSnapshotter(private val cacheRoot: File) {
   fun snapshot(request: SketchPreviewRequest): SketchSnapshot {
      require(request.projectDir.isDirectory) { "Sketch project folder does not exist: ${request.projectDir}" }
      val snapshotRoot = File(cacheRoot, "snapshot").also {
         it.deleteRecursively()
         it.mkdirs()
      }
      request.projectDir.copyRecursively(snapshotRoot, overwrite = true)
      val pdeFiles = snapshotRoot.listFiles { file -> file.isFile && file.extension.equals("pde", true) }
         ?.sortedWith(compareBy<File> { tabPriority(it, request.projectName) }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
         .orEmpty()
      require(pdeFiles.isNotEmpty()) { "Sketch has no .pde files." }
      require(pdeFiles.any { it.hasCodeIgnoringComments() }) {
         "Sketch is empty. Add code to the .pde file before running."
      }
      return SketchSnapshot(
         root = snapshotRoot,
         pdeFiles = pdeFiles,
         dataDir = snapshotRoot.childDir("data"),
         assetsDir = snapshotRoot.childDir("assets"),
         codeDir = snapshotRoot.childDir("code"),
         androidDir = snapshotRoot.childDir("android"),
         settings = request.settings
      )
   }

   private fun tabPriority(file: File, projectName: String): Int {
      return when {
         file.nameWithoutExtension.equals(projectName, ignoreCase = true) -> 0
         file.name.equals("sketch.pde", ignoreCase = true) -> 1
         file.name.equals("Sketch.pde", ignoreCase = true) -> 1
         else -> 2
      }
   }
}

private fun File.hasCodeIgnoringComments(): Boolean {
   val text = readText(Charsets.UTF_8)
   var index = 0
   var inLineComment = false
   var inBlockComment = false
   var inString = false
   var quote = '\u0000'
   var escaped = false

   while (index < text.length) {
      val char = text[index]
      val next = if (index + 1 < text.length) text[index + 1] else '\u0000'

      if (inLineComment) {
         if (char == '\n' || char == '\r') {
            inLineComment = false
         }
         index++
         continue
      }
      if (inBlockComment) {
         if (char == '*' && next == '/') {
            inBlockComment = false
            index += 2
         } else {
            index++
         }
         continue
      }
      if (inString) {
         if (escaped) {
            escaped = false
         } else if (char == '\\') {
            escaped = true
         } else if (char == quote) {
            inString = false
         }
         index++
         continue
      }

      if (char == '/' && next == '/') {
         inLineComment = true
         index += 2
         continue
      }
      if (char == '/' && next == '*') {
         inBlockComment = true
         index += 2
         continue
      }
      if (char == '"' || char == '\'') {
         inString = true
         quote = char
         return true
      }
      if (!char.isWhitespace()) {
         return true
      }
      index++
   }
   return false
}

internal fun File.childDir(name: String): File? = File(this, name).takeIf { it.isDirectory }

internal fun digestFiles(files: List<File>, extra: String): String {
   val digest = MessageDigest.getInstance("SHA-256")
   digest.update(extra.toByteArray(Charsets.UTF_8))
   for (file in files.sortedBy { it.relativeToOrSelf(files.first().parentFile ?: it).path }) {
      if (file.isFile) {
         digest.update(file.path.toByteArray(Charsets.UTF_8))
         digest.update(file.readBytes())
      }
   }
   return digest.digest().joinToString("") { "%02x".format(it) }
}
