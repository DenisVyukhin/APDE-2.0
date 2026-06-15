package com.apde2.sketchpreview

import android.content.Context
import android.net.Uri
import android.system.Os
import dalvik.system.PathClassLoader
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.util.zip.ZipInputStream

internal data class LoadedSketch(
   val instance: Any,
   val pAppletClass: Class<*>,
   val pFragmentClass: Class<*>
)

internal class PreviewFiles(private val context: Context) {
   private val root: File = File(context.filesDir, "runtime")
   private val dexFile = File(root, "sketch.dex")
   private val libsDir = File(root, "libs")
   private val dataDir = context.filesDir

   fun reset() {
      context.filesDir.listFiles()?.forEach { file ->
         if (file.name != root.name) {
            file.deleteRecursively()
         }
      }
      root.deleteRecursively()
      libsDir.mkdirs()
   }

   fun installDex(uri: Uri): File {
      dexFile.parentFile?.mkdirs()
      copyUri(uri, dexFile)
      makeReadOnly(dexFile)
      return dexFile
   }

   fun installDexedLibraries(uris: Array<String>): List<File> {
      libsDir.mkdirs()
      return uris.mapIndexedNotNull { index, rawUri ->
         if (rawUri.isBlank()) {
            null
         } else {
            File(libsDir, "lib-$index-dex.jar").also {
               copyUri(Uri.parse(rawUri), it)
               makeReadOnly(it)
            }
         }
      }
   }

   fun installData(zipUri: Uri?) {
      if (zipUri == null) {
         return
      }
      ZipInputStream(context.contentResolver.openInputStream(zipUri)).use { input ->
         while (true) {
            val entry = input.nextEntry ?: break
            val target = File(dataDir, entry.name).canonicalFile
            if (!target.path.startsWith(dataDir.canonicalPath + File.separator) && target != dataDir.canonicalFile) {
               input.closeEntry()
               continue
            }
            if (entry.isDirectory) {
               target.mkdirs()
            } else {
               target.parentFile?.mkdirs()
               FileOutputStream(target).use { output -> input.copyTo(output) }
            }
            input.closeEntry()
         }
      }
   }

   fun loadSketch(dex: File, libraries: List<File>, packageName: String, className: String): LoadedSketch {
      val dexPath = sequenceOf(dex).plus(libraries.asSequence()).joinToString(File.pathSeparator) { it.absolutePath }
      val loader = PathClassLoader(dexPath, javaClass.classLoader)
      val pAppletClass = Class.forName("processing.core.PApplet", true, loader)
      val pFragmentClass = Class.forName("processing.android.PFragment", true, loader)
      val clazz = Class.forName("$packageName.$className", true, loader)
      require(pAppletClass.isAssignableFrom(clazz)) {
         "$packageName.$className does not extend processing.core.PApplet"
      }
      @Suppress("DEPRECATION")
      return LoadedSketch(clazz.newInstance(), pAppletClass, pFragmentClass)
   }

   private fun copyUri(uri: Uri, target: File) {
      target.parentFile?.mkdirs()
      context.contentResolver.openInputStream(uri).use { input ->
         requireNotNull(input) { "Cannot open $uri" }
         FileOutputStream(target).use { output -> input.copyTo(output) }
      }
   }

   private fun makeReadOnly(file: File) {
      Os.chmod(file.absolutePath, 0b100100100)
      file.setWritable(false, false)
      require(!file.canWrite()) {
         "Dex file is still writable: ${file.absolutePath}"
      }
   }
}

internal fun Throwable.stackTraceString(): String {
   val writer = StringWriter()
   printStackTrace(PrintWriter(writer))
   return writer.toString()
}
