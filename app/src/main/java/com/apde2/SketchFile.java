package com.apde2;

final class SketchFile {
   String name;
   String code;
   String documentUri;
   String localProjectPath;

   SketchFile(String name, String code) {
      this(name, code, null, null);
   }

   SketchFile(String name, String code, String documentUri) {
      this(name, code, documentUri, null);
   }

   SketchFile(String name, String code, String documentUri, String localProjectPath) {
      this.name = name;
      this.code = code;
      this.documentUri = documentUri;
      this.localProjectPath = localProjectPath;
   }
}
