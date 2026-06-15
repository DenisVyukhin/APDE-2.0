package com.apde2.sketchpreview

import android.content.Context
import android.content.Intent

internal class PreviewLog(private val context: Context) {
   fun info(message: String) {
      broadcast(PreviewProtocol.LOG_ACTION, message)
   }

   fun crash(message: String, stacktrace: String = "") {
      broadcast(PreviewProtocol.CRASH_ACTION, message, stacktrace)
   }

   fun installCrashHandler() {
      Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
         crash(
            "Uncaught sketch runtime error on ${thread.name}: ${throwable.javaClass.simpleName}: ${throwable.message.orEmpty()}",
            throwable.stackTraceString()
         )
      }
   }

   private fun broadcast(action: String, message: String, stacktrace: String = "") {
      val intent = Intent(action)
         .setPackage(PreviewProtocol.EDITOR_PACKAGE)
         .putExtra(PreviewProtocol.EXTRA_MESSAGE, message)
         .putExtra(PreviewProtocol.EXTRA_STACKTRACE, stacktrace)
      context.sendBroadcast(intent)
   }
}
