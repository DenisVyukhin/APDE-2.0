package com.apde2.preview

internal object PreviewProtocol {
   const val PREVIEW_PACKAGE = "com.apde2.sketchpreview"
   const val RUN_ACTION = "com.apde2.preview.RUN_SKETCH"
   const val STOP_ACTION = "com.apde2.preview.STOP_SKETCH"
   const val LOG_ACTION = "com.apde2.preview.LOG"
   const val CRASH_ACTION = "com.apde2.preview.CRASH"

   const val EXTRA_RUN_ID = "com.apde2.preview.extra.RUN_ID"
   const val EXTRA_DEX_URI = "com.apde2.preview.extra.DEX_URI"
   const val EXTRA_DATA_URI = "com.apde2.preview.extra.DATA_URI"
   const val EXTRA_DEXED_LIB_URIS = "com.apde2.preview.extra.DEXED_LIB_URIS"
   const val EXTRA_PACKAGE_NAME = "com.apde2.preview.extra.PACKAGE_NAME"
   const val EXTRA_CLASS_NAME = "com.apde2.preview.extra.CLASS_NAME"
   const val EXTRA_ORIENTATION = "com.apde2.preview.extra.ORIENTATION"
   const val EXTRA_MESSAGE = "com.apde2.preview.extra.MESSAGE"
   const val EXTRA_STACKTRACE = "com.apde2.preview.extra.STACKTRACE"
}
