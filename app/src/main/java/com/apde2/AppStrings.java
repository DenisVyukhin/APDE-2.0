package com.apde2;

import android.content.Context;

import java.util.Locale;

final class AppStrings {
   enum Key {
      SETTINGS,
      GENERAL,
      INTERFACE_LANGUAGE,
      ENGLISH,
      RUSSIAN,
      HAPTIC_FEEDBACK,
      EDITOR,
      FONT_SIZE,
      TAB_SIZE,
      FILE_TEMPLATE,
      AUTO_CLOSE_BRACKETS_AND_QUOTES,
      CONSOLE,
      APPEARANCE,
      THEME,
      DARK,
      LIGHT,
      APDE_LIGHT,
      EDITOR_READY,
      OPENED_FOLDER,
      NOTHING_TO_UNDO,
      NOTHING_TO_REDO,
      OPEN_FILE_TO_START_EDITING,
      ERRORS,
      SAVED_SKETCH_TO,
      STARTING_SKETCH_RUN,
      PREPARING_PROJECT_SNAPSHOT,
      LOADING_1,
      LOADING_2,
      LOADING_3,
      COMPILER_AVAILABLE_NOT_CONNECTED,
      SKETCH_STARTED,
      SKETCH_STOPPED,
      CREATED_FILE,
      NEW_FILE,
      CREATE,
      DELETED,
      OPEN_ELLIPSIS,
      FILES,
      SKETCHES,
      EXAMPLES,
      LIBRARY_EXAMPLES,
      RECENT,
      CHOOSE_FOLDER_TO_SHOW_FILES,
      THIS_PROJECT_IS_EMPTY,
      NEW_FILE_MENU,
      NEW_FOLDER_MENU,
      RENAME,
      DELETE,
      OPEN,
      NEW_FOLDER,
      FOLDER,
      COULD_NOT_CREATE_FOLDER,
      CREATED_FOLDER,
      COULD_NOT_CREATE_FILE,
      CHOOSE_WRITABLE_FOLDER_FIRST,
      EXAMPLE_PROJECT_READ_ONLY,
      DELETE_FOLDER,
      DELETE_FILE,
      DELETE_QUESTION,
      COULD_NOT_DELETE,
      COULD_NOT_RENAME,
      RENAMED_TO,
      IS_NOT_A_TEXT_FILE,
      OPENED,
      COULD_NOT_READ,
      COULD_NOT_SAVE_EXTERNAL_FILE,
      UNTITLED,
      NEW_SKETCH,
      SAVE_SKETCH,
      RECENT_SKETCHES,
      NO_RECENT_SKETCHES,
      LOAD_SKETCH,
      SAVE_TO_SKETCHBOOK,
      MOVE_TO_SKETCHBOOK,
      RENAME_SKETCH,
      DELETE_DRAFT,
      DELETE_SKETCH,
      TOOLS,
      SKETCH_PROPERTIES,
      AUTO_FORMAT,
      COLOR_SELECTOR,
      FIND_REPLACE,
      FIND,
      REPLACE,
      PREVIOUS,
      NEXT,
      REPLACE_ALL,
      MATCH_CASE,
      MORE,
      LESS,
      SEARCH_FIELD_EMPTY,
      NO_MATCHES_FOUND,
      FIND_MATCH_COUNT,
      REPLACED_OCCURRENCES,
      IMPORT_LIBRARY,
      OPEN_REFERENCE,
      AI_AGENT,
      NO_EDITOR_AVAILABLE,
      OPEN_A_PDE_FILE_FIRST,
      AUTO_FORMAT_WORKS_ONLY_FOR_PDE,
      THERE_IS_NO_CODE_TO_FORMAT,
      CODE_IS_ALREADY_FORMATTED,
      CODE_FORMATTED,
      COPY,
      CLOSE,
      CLIPBOARD_IS_UNAVAILABLE,
      COPIED,
      NEW_TAB,
      RENAME_TAB,
      DELETE_TAB,
      IS_NOT_IMPLEMENTED_YET,
      CANCEL,
      SAVE,
      ENTER_A_VALID_NAME,
      ENTER_A_VALID_VALUE,
      CANNOT_CREATE_IN_SYSTEM_FOLDER,
      COULD_NOT_RENAME_EXTERNAL_FILE,
      RENAMED_TAB_TO,
      NO_ERRORS,
      NO_CONSOLE_OUTPUT,
      CONSOLE_MESSAGE_COPIED
      ,
      SKETCH_DISPLAY_NAME,
      PACKAGE_NAME,
      VERSION_CODE,
      VERSION_NAME,
      SKETCH_PERMISSIONS,
      TARGET_SDK,
      MINIMUM_SDK,
      LOCKED_ORIENTATION,
      ADD_FILE,
      CHANGE_SKETCH_ICON,
      NONE,
      PORTRAIT,
      LANDSCAPE,
      REVERSE_LANDSCAPE
   }

   private final String language;

   AppStrings(Context context) {
      this(new AppSettings(context).language());
   }

   AppStrings(String language) {
      this.language = AppSettings.LANGUAGE_RU.equals(language) ? AppSettings.LANGUAGE_RU : AppSettings.LANGUAGE_EN;
   }

   String language() {
      return language;
   }

   String text(Key key) {
      switch (key) {
         case SETTINGS: return tr("Settings", "Настройки");
         case GENERAL: return tr("General", "Общие");
         case INTERFACE_LANGUAGE: return tr("Interface Language", "Язык интерфейса");
         case ENGLISH: return "English";
         case RUSSIAN: return "Русский";
         case HAPTIC_FEEDBACK: return tr("Haptic Feedback", "Виброотклик");
         case EDITOR: return tr("Editor", "Редактор");
         case FONT_SIZE: return tr("Font Size", "Размер шрифта");
         case TAB_SIZE: return tr("Tab Size", "Размер табуляции");
         case FILE_TEMPLATE: return tr("File Template", "Шаблон файла");
         case AUTO_CLOSE_BRACKETS_AND_QUOTES: return tr("Auto-close Brackets and Quotes", "Автозакрытие скобок и кавычек");
         case CONSOLE: return tr("Console", "Консоль");
         case APPEARANCE: return tr("Appearance", "Внешний вид");
         case THEME: return tr("Theme", "Тема оформления");
         case DARK: return tr("Dark", "Темная");
         case LIGHT: return tr("Light", "Светлая");
         case APDE_LIGHT: return tr("APDE", "APDE");
         case EDITOR_READY: return tr("Editor ready.", "Редактор готов.");
         case OPENED_FOLDER: return tr("Opened folder %s.", "Открыта папка %s.");
         case NOTHING_TO_UNDO: return tr("Nothing to undo.", "Нечего отменять.");
         case NOTHING_TO_REDO: return tr("Nothing to redo.", "Нечего повторять.");
         case OPEN_FILE_TO_START_EDITING: return tr("Open a file to start editing.", "Открой файл, чтобы начать редактирование.");
         case ERRORS: return tr("Errors", "Ошибки");
         case SAVED_SKETCH_TO: return tr("Saved sketch to %s.", "Скетч сохранен в %s.");
         case STARTING_SKETCH_RUN: return tr("Starting sketch run.", "Запуск скетча.");
         case PREPARING_PROJECT_SNAPSHOT: return tr("Preparing project snapshot.", "Подготовка снимка проекта.");
         case LOADING_1: return tr("Loading.", "Загрузка.");
         case LOADING_2: return tr("Loading..", "Загрузка..");
         case LOADING_3: return tr("Loading...", "Загрузка...");
         case COMPILER_AVAILABLE_NOT_CONNECTED: return tr("MVP compiler is available but not connected.", "MVP-компилятор доступен, но не подключен.");
         case SKETCH_STARTED: return tr("Sketch started.", "Скетч запущен.");
         case SKETCH_STOPPED: return tr("Sketch stopped.", "Скетч остановлен.");
         case CREATED_FILE: return tr("Created %s.", "Создано: %s.");
         case NEW_FILE: return tr("New file", "Новый файл");
         case CREATE: return tr("Create", "Создать");
         case DELETED: return tr("Deleted %s.", "Удалено: %s.");
         case OPEN_ELLIPSIS: return tr("Open...", "Открыть...");
         case FILES: return tr("Files", "Файлы");
         case SKETCHES: return tr("Sketches", "Скетчи");
         case EXAMPLES: return tr("Examples", "Примеры");
         case LIBRARY_EXAMPLES: return tr("Library Examples", "Примеры библиотек");
         case RECENT: return tr("Recent", "Недавние");
         case CHOOSE_FOLDER_TO_SHOW_FILES: return tr("Choose a folder to show files.", "Выбери папку, чтобы показать файлы.");
         case THIS_PROJECT_IS_EMPTY: return tr("This project is empty.", "Этот проект пуст.");
         case NEW_FILE_MENU: return tr("New File", "Новый файл");
         case NEW_FOLDER_MENU: return tr("New Folder", "Новая папка");
         case RENAME: return tr("Rename", "Переименовать");
         case DELETE: return tr("Delete", "Удалить");
         case OPEN: return tr("Open", "Открыть");
         case NEW_FOLDER: return tr("New folder", "Новая папка");
         case FOLDER: return tr("Folder", "Папка");
         case COULD_NOT_CREATE_FOLDER: return tr("Could not create folder.", "Не удалось создать папку.");
         case CREATED_FOLDER: return tr("Created folder %s.", "Создана папка %s.");
         case COULD_NOT_CREATE_FILE: return tr("Could not create file.", "Не удалось создать файл.");
         case CHOOSE_WRITABLE_FOLDER_FIRST: return tr("Choose a writable folder first.", "Сначала выбери папку с доступом на запись.");
         case EXAMPLE_PROJECT_READ_ONLY: return tr("Example projects are read-only.", "Примеры доступны только для чтения.");
         case DELETE_FOLDER: return tr("Delete folder", "Удалить папку");
         case DELETE_FILE: return tr("Delete file", "Удалить файл");
         case DELETE_QUESTION: return tr("Delete %s?", "Удалить %s?");
         case COULD_NOT_DELETE: return tr("Could not delete %s.", "Не удалось удалить %s.");
         case COULD_NOT_RENAME: return tr("Could not rename %s.", "Не удалось переименовать %s.");
         case RENAMED_TO: return tr("Renamed to %s.", "Переименовано в %s.");
         case IS_NOT_A_TEXT_FILE: return tr("%s is not a text file.", "%s не является текстовым файлом.");
         case OPENED: return tr("Opened %s.", "Открыт %s.");
         case COULD_NOT_READ: return tr("Could not read %s.", "Не удалось прочитать %s.");
         case COULD_NOT_SAVE_EXTERNAL_FILE: return tr("Could not save external file.", "Не удалось сохранить внешний файл.");
         case UNTITLED: return tr("Untitled", "Без названия");
         case NEW_SKETCH: return tr("New Sketch", "Новый скетч");
         case SAVE_SKETCH: return tr("Save Sketch", "Сохранить скетч");
         case RECENT_SKETCHES: return tr("Recent Sketches", "Недавние скетчи");
         case NO_RECENT_SKETCHES: return tr("No recent sketches.", "Недавних скетчей нет.");
         case LOAD_SKETCH: return tr("Open Folder", "Открыть папку");
         case SAVE_TO_SKETCHBOOK: return tr("Save to Sketchbook", "Сохранить в Sketchbook");
         case MOVE_TO_SKETCHBOOK: return tr("Move to Sketchbook", "Переместить в Sketchbook");
         case RENAME_SKETCH: return tr("Rename Sketch", "Переименовать скетч");
         case DELETE_DRAFT: return tr("Delete Draft", "Удалить черновик");
         case DELETE_SKETCH: return tr("Delete Sketch", "Удалить скетч");
         case TOOLS: return tr("Tools", "Инструменты");
         case SKETCH_PROPERTIES: return tr("Sketch Properties", "Свойства скетча");
         case AUTO_FORMAT: return tr("Auto Format", "Автоформат");
         case COLOR_SELECTOR: return tr("Color Selector", "Выбор цвета");
         case FIND_REPLACE: return tr("Find/Replace", "Поиск/Замена");
         case FIND: return tr("Find", "Найти");
         case REPLACE: return tr("Replace", "Заменить");
         case PREVIOUS: return tr("Previous", "Назад");
         case NEXT: return tr("Next", "Далее");
         case REPLACE_ALL: return tr("Replace All", "Заменить все");
         case MATCH_CASE: return tr("Match Case", "Учитывать регистр");
         case MORE: return tr("More", "Еще");
         case LESS: return tr("Less", "Скрыть");
         case SEARCH_FIELD_EMPTY: return tr("Enter text to find.", "Введите текст для поиска.");
         case NO_MATCHES_FOUND: return tr("No matches found.", "Совпадений не найдено.");
         case FIND_MATCH_COUNT: return tr("%d of %d matches", "%d из %d совпадений");
         case REPLACED_OCCURRENCES: return tr("Replaced %d occurrences.", "Заменено совпадений: %d.");
         case IMPORT_LIBRARY: return tr("Import Library", "Импорт библиотеки");
         case OPEN_REFERENCE: return tr("Open Reference", "Открыть справочник");
         case AI_AGENT: return tr("AI Agent", "AI Agent");
         case NO_EDITOR_AVAILABLE: return tr("No editor available.", "Редактор недоступен.");
         case OPEN_A_PDE_FILE_FIRST: return tr("Open a .pde file first.", "Сначала открой .pde файл.");
         case AUTO_FORMAT_WORKS_ONLY_FOR_PDE: return tr("Auto Format works only for .pde files.", "Автоформат работает только для .pde файлов.");
         case THERE_IS_NO_CODE_TO_FORMAT: return tr("There is no code to format.", "Нет кода для форматирования.");
         case CODE_IS_ALREADY_FORMATTED: return tr("Code is already formatted.", "Код уже отформатирован.");
         case CODE_FORMATTED: return tr("Code formatted.", "Код отформатирован.");
         case COPY: return tr("Copy", "Копировать");
         case CLOSE: return tr("Close", "Закрыть");
         case CLIPBOARD_IS_UNAVAILABLE: return tr("Clipboard is unavailable.", "Буфер обмена недоступен.");
         case COPIED: return tr("%s copied.", "%s скопировано.");
         case NEW_TAB: return tr("New Tab", "Новая вкладка");
         case RENAME_TAB: return tr("Rename Tab", "Переименовать вкладку");
         case DELETE_TAB: return tr("Delete Tab", "Удалить вкладку");
         case IS_NOT_IMPLEMENTED_YET: return tr("%s is not implemented yet.", "%s пока не реализовано.");
         case CANCEL: return tr("Cancel", "Отмена");
         case SAVE: return tr("Save", "Сохранить");
         case ENTER_A_VALID_NAME: return tr("Enter a valid name", "Введите корректное имя");
         case ENTER_A_VALID_VALUE: return tr("Enter a valid value", "Введите корректное значение");
         case CANNOT_CREATE_IN_SYSTEM_FOLDER: return tr("Files and folders cannot be created here.", "Здесь нельзя создавать файлы и папки.");
         case COULD_NOT_RENAME_EXTERNAL_FILE: return tr("Could not rename external file.", "Не удалось переименовать внешний файл.");
         case RENAMED_TAB_TO: return tr("Renamed tab to %s.", "Вкладка переименована в %s.");
         case NO_ERRORS: return tr("No errors.", "Ошибок нет.");
         case NO_CONSOLE_OUTPUT: return tr("No console output.", "Консоль пуста.");
         case CONSOLE_MESSAGE_COPIED: return tr("Console message copied.", "Сообщение из консоли скопировано.");
         case SKETCH_DISPLAY_NAME: return tr("Sketch Display Name", "Отображаемое имя скетча");
         case PACKAGE_NAME: return tr("Package Name", "Имя пакета");
         case VERSION_CODE: return tr("Version Code", "Код версии");
         case VERSION_NAME: return tr("Version Name", "Имя версии");
         case SKETCH_PERMISSIONS: return tr("Sketch Permissions", "Разрешения скетча");
         case TARGET_SDK: return tr("Target SDK", "Target SDK");
         case MINIMUM_SDK: return tr("Minimum SDK", "Minimum SDK");
         case LOCKED_ORIENTATION: return tr("Locked Orientation", "Фиксированная ориентация");
         case ADD_FILE: return tr("Add File", "Добавить файл");
         case CHANGE_SKETCH_ICON: return tr("Change Sketch Icon", "Изменить иконку скетча");
         case NONE: return tr("None", "Нет");
         case PORTRAIT: return tr("Portrait", "Портретная");
         case LANDSCAPE: return tr("Landscape", "Альбомная");
         case REVERSE_LANDSCAPE: return tr("Reverse Landscape", "Обратная альбомная");
         default: return "";
      }
   }

   String format(Key key, Object... args) {
      return String.format(Locale.getDefault(), text(key), args);
   }

   private String tr(String en, String ru) {
      return AppSettings.LANGUAGE_RU.equals(language) ? ru : en;
   }
}
