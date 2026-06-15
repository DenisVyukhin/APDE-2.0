package com.apde2.sketchpreview

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity

class PreviewActivity : FragmentActivity() {
   private var sketch: LoadedSketch? = null
   private lateinit var frame: FrameLayout
   private val files by lazy { PreviewFiles(this) }
   private val log by lazy { PreviewLog(this) }
   private val mainHandler = Handler(Looper.getMainLooper())
   private var watchdogStarted = false
   private val stopReceiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context, intent: Intent) {
         if (intent.action == PreviewProtocol.STOP_ACTION) {
            closePreview()
         }
      }
   }

   override fun onCreate(savedInstanceState: Bundle?) {
      super.onCreate(savedInstanceState)
      frame = FrameLayout(this).also { it.id = ViewId.next() }
      setContentView(
         frame,
         ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
      )
      registerStopReceiver()
      log.installCrashHandler()

      if (intent.action != PreviewProtocol.RUN_ACTION) {
         finish()
         return
      }
      startSketch(intent)
   }

   override fun onDestroy() {
      mainHandler.removeCallbacksAndMessages(null)
      sketch?.call("onDestroy")
      runCatching { unregisterReceiver(stopReceiver) }
      super.onDestroy()
   }

   override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
      super.onRequestPermissionsResult(requestCode, permissions, grantResults)
      sketch?.call("onRequestPermissionsResult", requestCode, permissions, grantResults)
   }

   override fun onNewIntent(intent: Intent) {
      super.onNewIntent(intent)
      setIntent(intent)
      if (intent.action == PreviewProtocol.RUN_ACTION) {
         restartSketch(intent)
      } else {
         sketch?.call("onNewIntent", intent)
      }
   }

   @Deprecated("Deprecated in Android framework")
   override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
      super.onActivityResult(requestCode, resultCode, data)
      sketch?.call("onActivityResult", requestCode, resultCode, data)
   }

   @Deprecated("Deprecated in Android framework")
   override fun onBackPressed() {
      closePreview()
   }

   private fun startSketch(intent: Intent) {
      try {
         log.info("Sketch Preview: preparing runtime.")
         val dexUri = requireNotNull(intent.getStringExtra(PreviewProtocol.EXTRA_DEX_URI)) { "Missing sketch dex" }
         val packageName = requireNotNull(intent.getStringExtra(PreviewProtocol.EXTRA_PACKAGE_NAME)) { "Missing package name" }
         val className = requireNotNull(intent.getStringExtra(PreviewProtocol.EXTRA_CLASS_NAME)) { "Missing class name" }
         val dataUri = intent.getStringExtra(PreviewProtocol.EXTRA_DATA_URI)?.takeIf { it.isNotBlank() }
         val libUris = intent.getStringArrayExtra(PreviewProtocol.EXTRA_DEXED_LIB_URIS) ?: emptyArray()

         files.reset()
         val dex = files.installDex(android.net.Uri.parse(dexUri))
         val libs = files.installDexedLibraries(libUris)
         files.installData(dataUri?.let(android.net.Uri::parse))

         log.info("Sketch Preview: loading $packageName.$className.")
         sketch = files.loadSketch(dex, libs, packageName, className)
         val fragment = sketch!!.pFragmentClass
            .getConstructor(sketch!!.pAppletClass)
            .newInstance(sketch!!.instance) as Fragment
         sketch!!.pFragmentClass
            .getMethod("setView", android.view.View::class.java, FragmentActivity::class.java)
            .invoke(fragment, frame, this)
         supportFragmentManager.executePendingTransactions()
         log.info("Sketch Preview: sketch surface attached.")
         applyOrientation(intent.getStringExtra(PreviewProtocol.EXTRA_ORIENTATION).orEmpty())
         startStartupWatchdog()
      } catch (throwable: Throwable) {
         showFailure(throwable)
      }
   }

   private fun restartSketch(intent: Intent) {
      mainHandler.removeCallbacksAndMessages(null)
      watchdogStarted = false
      sketch?.call("onDestroy")
      sketch = null
      frame.removeAllViews()
      startSketch(intent)
   }

   private fun closePreview() {
      if (Build.VERSION.SDK_INT >= 21) {
         finishAndRemoveTask()
      } else {
         finish()
      }
   }

   private fun registerStopReceiver() {
      val filter = IntentFilter(PreviewProtocol.STOP_ACTION)
      if (Build.VERSION.SDK_INT >= 33) {
         registerReceiver(stopReceiver, filter, RECEIVER_NOT_EXPORTED)
      } else {
         @Suppress("DEPRECATION")
         registerReceiver(stopReceiver, filter)
      }
   }

   private fun applyOrientation(value: String) {
      requestedOrientation = when (value) {
         "portrait" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
         "landscape" -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
         "reverse_landscape", "reverseLandscape" -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
         else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
      }
   }

   private fun showFailure(throwable: Throwable) {
      PreviewLog(this).installCrashHandler()
      PreviewLog(this).crash(throwable.message.orEmpty(), throwable.stackTraceString())
      frame.removeAllViews()
      frame.setBackgroundColor(0xff050507.toInt())
      frame.addView(
         TextView(this).apply {
            setTextColor(0xffff7a7a.toInt())
            textSize = 15f
            text = "Sketch preview failed\n\n${throwable.message.orEmpty()}"
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
         },
         FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
      )
   }

   private fun startStartupWatchdog() {
      if (watchdogStarted) {
         return
      }
      watchdogStarted = true
      mainHandler.postDelayed({ reportStartupState() }, STARTUP_WATCHDOG_DELAY_MS)
   }

   private fun reportStartupState() {
      val currentSketch = sketch ?: return
      val frameCount = currentSketch.longField("frameCount")
      val width = currentSketch.longField("width")
      val height = currentSketch.longField("height")
      val finished = currentSketch.booleanField("finished")
      val looping = currentSketch.booleanField("looping")
      val graphics = currentSketch.fieldValue("g")
      val surface = currentSketch.fieldValue("surface")
      val renderer = graphics?.javaClass?.name ?: "null"
      val surfaceType = surface?.javaClass?.name ?: "null"

      if (frameCount > 0L) {
         log.info("Sketch Preview: first frame rendered. frameCount=$frameCount, size=${width}x$height, renderer=$renderer.")
         return
      }
      if (finished) {
         log.info("Sketch Preview: sketch finished before a rendered frame was reported. size=${width}x$height, renderer=$renderer.")
         return
      }

      val message = "Sketch Preview hang: no frame was rendered after ${STARTUP_WATCHDOG_DELAY_MS / 1000}s. " +
         "frameCount=$frameCount, size=${width}x$height, finished=$finished, looping=$looping, renderer=$renderer, surface=$surfaceType."
      log.crash(message, threadDump())
   }

   companion object {
      private const val STARTUP_WATCHDOG_DELAY_MS = 5000L
   }
}

private fun LoadedSketch.call(name: String, vararg args: Any?) {
   val types = args.map {
      when (it) {
         is Int -> Int::class.javaPrimitiveType
         is IntArray -> IntArray::class.java
         is Array<*> -> Array<String>::class.java
         is Intent -> Intent::class.java
         null -> Intent::class.java
         else -> it.javaClass
      }
   }.toTypedArray()
   runCatching {
      pAppletClass.getMethod(name, *types).invoke(instance, *args)
   }
}

private fun LoadedSketch.longField(name: String): Long {
   val value = fieldValue(name)
   return when (value) {
      is Number -> value.toLong()
      else -> -1L
   }
}

private fun LoadedSketch.booleanField(name: String): Boolean {
   return fieldValue(name) as? Boolean ?: false
}

private fun LoadedSketch.fieldValue(name: String): Any? {
   var type: Class<*>? = instance.javaClass
   while (type != null) {
      try {
         val field = type.getDeclaredField(name)
         field.isAccessible = true
         return field.get(instance)
      } catch (_: NoSuchFieldException) {
         type = type.superclass
      } catch (_: Throwable) {
         return null
      }
   }
   return null
}

private fun threadDump(): String {
   return Thread.getAllStackTraces().entries
      .sortedBy { it.key.name }
      .joinToString("\n\n") { (thread, stack) ->
         buildString {
            append("Thread \"")
            append(thread.name)
            append("\" ")
            append(thread.state)
            stack.forEach { element ->
               append('\n')
               append("  at ")
               append(element)
            }
         }
      }
}

private object ViewId {
   private var next = 1
   fun next(): Int {
      if (Build.VERSION.SDK_INT >= 17) {
         return android.view.View.generateViewId()
      }
      return next++
   }
}
