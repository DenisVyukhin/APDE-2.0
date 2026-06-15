package com.apde2.preview

import com.android.tools.r8.CompilationMode
import com.android.tools.r8.D8
import com.android.tools.r8.D8Command
import com.android.tools.r8.Diagnostic
import com.android.tools.r8.DiagnosticsHandler
import com.android.tools.r8.OutputMode
import org.eclipse.jdt.internal.compiler.batch.Main
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintWriter
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AndroidSketchCompiler(
   private val toolchain: AndroidSketchToolchain,
   private val parser: PreviewDiagnosticParser = PreviewDiagnosticParser()
) {
   fun compile(project: GeneratedSketchProject, settings: SketchSettings): PreviewBuildOutcome {
      val outDir = File(project.root, "out").also {
         it.deleteRecursively()
         it.mkdirs()
      }
      val classesDir = File(outDir, "classes").also { it.mkdirs() }
      val dexDir = File(outDir, "dex").also { it.mkdirs() }

      val compileOutput = compileJava(project, classesDir)
      if (!compileOutput.success) {
         val diagnostics = parser.parse(compileOutput.output)
         return PreviewBuildOutcome.Failure(
            compileOutput.output.ifBlank { "Java compilation failed." },
            diagnostics
         )
      }

      val d8Output = runCatching { dex(project, settings, classesDir, dexDir) }.getOrElse {
         val message = it.message ?: "D8 failed."
         return PreviewBuildOutcome.Failure(message, parser.parse(message))
      }
      if (d8Output.isNotBlank()) {
         return PreviewBuildOutcome.Failure(d8Output, parser.parse(d8Output))
      }

      val dexFile = File(dexDir, "classes.dex")
      if (!dexFile.isFile) {
         return PreviewBuildOutcome.Failure("D8 did not produce classes.dex.")
      }
      val sketchDex = File(outDir, "sketch.dex")
      dexFile.copyTo(sketchDex, overwrite = true)

      val dataZip = project.dataDir?.takeIf { it.isDirectory && it.walkTopDown().any(File::isFile) }?.let {
         File(outDir, "data.zip").also { zip -> zipDirectory(it, zip) }
      }

      return PreviewBuildOutcome.Success(
         BuiltSketch(
            runId = UUID.randomUUID().toString(),
            dexFile = sketchDex,
            dataZip = dataZip,
            dexedLibraries = emptyList(),
            packageName = project.packageName,
            className = project.className,
            orientation = settings.orientation
         )
      )
   }

   fun builtFromCache(project: GeneratedSketchProject, settings: SketchSettings): BuiltSketch? {
      val dex = File(project.root, "out/sketch.dex")
      if (!dex.isFile) {
         return null
      }
      return BuiltSketch(
         runId = UUID.randomUUID().toString(),
         dexFile = dex,
         dataZip = File(project.root, "out/data.zip").takeIf { it.isFile },
         dexedLibraries = File(project.root, "out/libs").listFiles { file -> file.extension == "jar" }?.toList().orEmpty(),
         packageName = project.packageName,
         className = project.className,
         orientation = settings.orientation
      )
   }

   private fun compileJava(project: GeneratedSketchProject, classesDir: File): CompilerOutput {
      val output = ByteArrayOutputStream()
      val writer = PrintWriter(output, true)
      val classpath = listOf(toolchain.processingCoreJar.absolutePath).joinToString(File.pathSeparator)
      val args = arrayOf(
         "-nowarn",
         "-proc:none",
         "-source", "1.8",
         "-target", "1.8",
         "-bootclasspath", toolchain.androidJar.absolutePath,
         "-classpath", classpath,
         "-d", classesDir.absolutePath,
         project.generatedSource.absolutePath
      )
      val success = Main.compile(args, writer, writer, null)
      writer.flush()
      return CompilerOutput(success, output.toString(Charsets.UTF_8.name()))
   }

   private fun dex(project: GeneratedSketchProject, settings: SketchSettings, classesDir: File, dexDir: File): String {
      val diagnostics = D8Diagnostics()
      val classFiles = classesDir.walkTopDown()
         .filter { it.isFile && it.extension == "class" }
         .map { it.toPath() }
         .toList()
      if (classFiles.isEmpty()) {
         return "Java compiler produced no class files."
      }
      val command = D8Command.builder(diagnostics)
         .setMode(CompilationMode.DEBUG)
         .setMinApiLevel(settings.minSdk)
         .setOutput(dexDir.toPath(), OutputMode.DexIndexed)
         .addLibraryFiles(toolchain.androidJar.toPath(), toolchain.processingCoreJar.toPath())
         .addProgramFiles(classFiles)
         .build()
      D8.run(command)
      return diagnostics.errors()
   }

   private fun zipDirectory(source: File, zipFile: File) {
      ZipOutputStream(zipFile.outputStream().buffered()).use { zip ->
         source.walkTopDown().filter(File::isFile).forEach { file ->
            val name = file.relativeTo(source).path.replace(File.separatorChar, '/')
            zip.putNextEntry(ZipEntry(name))
            file.inputStream().use { input -> input.copyTo(zip) }
            zip.closeEntry()
         }
      }
   }

   private data class CompilerOutput(val success: Boolean, val output: String)

   private class D8Diagnostics : DiagnosticsHandler {
      private val errors = mutableListOf<String>()

      override fun error(error: Diagnostic) {
         errors += error.diagnosticMessage
      }

      override fun warning(warning: Diagnostic) {
      }

      override fun info(info: Diagnostic) {
      }

      fun errors(): String = errors.joinToString("\n")
   }
}
