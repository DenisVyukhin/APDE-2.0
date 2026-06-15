# APDE 2.0: README для разработчиков

[![Технологии APDE 2.0](https://skillicons.dev/icons?i=processing,androidstudio,java,kotlin,gradle&theme=light)](https://skillicons.dev)

APDE 2.0 - Android-редактор Processing-скетчей. Этот документ нужен, чтобы быстро разобраться в проекте: где живет UI, как хранится скетчбук, что происходит после нажатия Run и какие части кода еще тянутся из раннего MVP.

Пользовательское описание приложения лежит в [README.md](README.md).

> [!IMPORTANT]
> Текущий запуск скетча идет через `com.apde2.preview`. Проект копируется во временный снимок, `.pde`-код превращается в Java-класс `PApplet`, проходит через ECJ и D8, а затем запускается в отдельном приложении Sketch Preview.

## С чего начать

### Куда смотреть в коде

| Хочешь понять | Открой |
| --- | --- |
| Главный экран и основные пользовательские сценарии | `app/src/main/java/com/apde2/MainActivity.java` |
| Сам редактор кода | `CodeEditorView.java`, `EditorTheme.java`, `editor_themes.json` |
| Скетчбук, вкладки и файлы проекта | `SketchStore.java` |
| Настройки приложения | `AppSettings.java`, `SettingsActivity.java`, `AppStrings.java` |
| Свойства конкретного скетча | `SketchPropertiesActivity.java`, `preview/SketchSettingsReader.kt` |
| Сборку и запуск превью | `preview/SketchPreviewController.kt`, `preview/ProcessingAndroidPreviewPipeline.kt` |
| Приложение, в котором исполняется превью | `sketch-preview/src/main/java/com/apde2/sketchpreview/PreviewActivity.kt` |

### Быстрая проверка сборки

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Нужны Android SDK Platform 35 и JDK 17. Если Gradle не находит SDK, задай путь в `local.properties`:

```properties
sdk.dir=/absolute/path/to/Android/sdk
```

> [!NOTE]
> Сборка `:app` тянет за собой и приложение превью. Перед упаковкой Gradle подготавливает набор инструментов для компиляции скетча и кладет APK модуля `:sketch-preview` в assets редактора.

## Как устроен репозиторий

```text
.
|-- app/                         основной редактор APDE
|   |-- src/main/java/com/apde2/ UI, редактор, хранилище, настройки
|   |-- src/main/java/com/apde2/preview/
|   |                               сборка и запуск превью на Kotlin
|   |-- src/main/assets/themes/     JSON-темы редактора
|   `-- src/test/                   unit-тесты генератора исходников
|-- sketch-preview/                отдельное приложение для запуска скетча
|-- docs/                          план разработки и схема Sketch Preview
|-- build.gradle                  версии Android/Kotlin plugins
`-- settings.gradle               модули `:app` и `:sketch-preview`
```

| Модуль | Роль |
| --- | --- |
| `:app` | Редактор, скетчбук, настройки, файловый UI и сборка превью |
| `:sketch-preview` | Приложение, которое получает dex и данные скетча, а затем показывает `PApplet` через `PFragment` |

## Технологии

| Область | Что используется |
| --- | --- |
| Android | Native Android, `minSdk 26`, `compileSdk 35`, `targetSdk 35` |
| Сборка | Gradle Wrapper 8.9, Android Gradle Plugin 8.7.3 |
| Языки | Java 17 в большей части UI-кода, Kotlin в конвейере превью и части виджетов |
| UI | Android Views, собранные кодом; Compose применяется точечно через `AbstractComposeView` |
| Файлы | `File` для управляемого скетчбука, SAF/`DocumentFile` для внешних папок |
| Редактор | Собственные view-компоненты, regex-подсветка, JSON-темы, JetBrains Mono |
| Компиляция превью | ECJ для Java-исходников, D8 из R8 для dex |
| Processing | `processing-core.jar`, который Gradle упаковывает в assets набора сборки |
| Тесты | unit-тесты на JUnit 4 |

Версии и зависимости лучше сверять в [build.gradle](build.gradle), [app/build.gradle](app/build.gradle) и [sketch-preview/build.gradle](sketch-preview/build.gradle).

## Карта кода

### Редактор и интерфейс

Главный экран пока большой и императивный. `MainActivity` программно собирает верхнюю панель, вкладки, редактор, консоль, файловую боковую панель, диалоги инструментов и поток запуска скетча. XML layout-файлы здесь не являются главным описанием UI.

| Компонент | Что делает |
| --- | --- |
| `CodeEditorView` | Редактирование текста, undo/redo, автоотступы, автозакрытие пар, форматирование, инкрементальная подсветка |
| `LineNumberView` | Номера строк рядом с редактором |
| `EditorTheme` | Загружает цвета и правила подсветки из JSON themes asset |
| `SettingsActivity` | Язык, виброотклик, шрифты, табуляция, темы |
| `SketchPropertiesActivity` | Имя скетча, package/version/SDK/orientation и импорт файлов в `data/` |
| `AppStrings` | Русские и английские тексты основного UI |

Compose здесь не основа интерфейса. Он используется для отдельных элементов управления, например `ControlIconButton`, `FileIconView` и иконок консоли.

### Скетчбук и файлы

`SketchStore` ведет управляемый скетчбук и `.pde`-вкладки. Основной вариант хранения - внешний каталог `Sketchbook`; если он недоступен, используется app-private directory. Внешние папки открываются через Android Storage Access Framework.

Новый управляемый проект начинается с такой структуры:

```text
Sketchbook/
  Sketches/
    NewSketch/
      sketch.pde
      sketch.properties
      data/
```

`sketch.properties` уже создается рядом с проектом. При этом значения с экрана свойств сейчас хранятся в `SharedPreferences`, а перед запуском превью их читает `SketchSettingsReader`.

### Что происходит после Run

Путь запуска проще всего читать как конвейер:

```text
MainActivity
  -> SketchPreviewController
  -> SketchSnapshotter
  -> ProcessingSourceGenerator
  -> AndroidSketchCompiler: ECJ -> D8
  -> SketchPreviewLauncher
  -> sketch-preview PreviewActivity
  -> Processing PFragment
```

По шагам это выглядит так:

1. `SketchSnapshotter` копирует проект в `cacheDir/sketch-preview/snapshot`, сортирует `.pde`-вкладки и отсекает пустые скетчи.
2. `ProcessingSourceGenerator` генерирует Java-класс `PApplet`, переносит `data/`, `assets/`, `code/`, `android/` и добавляет мосты для консоли и ошибок во время работы.
3. `ToolchainLocator` извлекает из assets `android.jar` и `processing-core.jar`.
4. `AndroidSketchCompiler` компилирует Java через ECJ, превращает `.class`-файлы в dex через D8 и выдает `sketch.dex` вместе с `data.zip`.
5. `SketchPreviewLauncher` передает результаты приложению Sketch Preview через `FileProvider`.
6. `PreviewActivity` загружает dex через `PathClassLoader`, создает объект скетча и подключает `processing.android.PFragment`.

Редактор и Sketch Preview общаются явными intent/broadcast-сообщениями:

| Канал | Значение |
| --- | --- |
| Запуск | `com.apde2.preview.RUN_SKETCH` |
| Остановка | `com.apde2.preview.STOP_SKETCH` |
| Вывод в консоль | `com.apde2.preview.LOG` |
| Ошибка во время работы | `com.apde2.preview.CRASH` |

Подробный разбор вынесен в [docs/sketch-preview.md](docs/sketch-preview.md).

> [!WARNING]
> `ProcessingCompiler`, `SketchProgram`, `Value`, `CompileResult`, `Diagnostic` и `SketchRunnerView` остались от раннего Canvas MVP. Они все еще лежат в `:app`, но текущая кнопка Run уже работает через новый конвейер превью.

## Что важно знать о сборке

`app/build.gradle` подключает сгенерированные assets к основному набору исходников. Поэтому обычная сборка редактора перед упаковкой выполняет две важные задачи:

| Gradle-задача | Что делает |
| --- | --- |
| `prepareSketchPreviewToolchain` | Берет `android.jar` из локального SDK и извлекает `processing-core.jar` из Processing Android Mode release |
| `prepareSketchPreviewInstaller` | Собирает `:sketch-preview:assembleDebug` и кладет `sketch-preview.apk` в assets редактора |

На первой сборке нужен доступ к сети, если архив Processing Android Mode еще не подготовлен локально. Сейчас он скачивается через `latest`, так что набор инструментов пока не закреплен на конкретной версии.

## Как вносить изменения

Кодовая база уже сложилась со своими правилами. Лучше опираться на них:

- Java, Kotlin и Gradle файлы визуально держатся 3-пробельного отступа.
- Новые цвета интерфейса и подсветки лучше вести через `EditorTheme` и JSON themes, а не добавлять отдельные literals в виджеты.
- Локализуемый текст основного UI пока живет в `AppStrings`.
- View-компоненты, которые должны переживать смену темы без пересоздания экрана, обычно реализуют `ThemeAware`.
- При работе с файлами важно не смешивать управляемый `File` sketchbook и внешние SAF/`DocumentFile` деревья.
- Логику сборки превью лучше держать в `com.apde2.preview`, а не наращивать `MainActivity`.

По форме код неоднородный. Старые Java activity крупные и держат много логики пользовательских сценариев, а свежий слой превью уже разбит на небольшие Kotlin-классы и модели данных. Для новой логики второй подход обычно легче поддерживать.

## Как проверять изменения

Автотесты пока точечные. Сейчас `ProcessingSourceGeneratorTest` проверяет детали генерации исходников, например Processing-функции приведения типов и стандартные `import`. Для новых изменений в слое превью полезнее всего добавлять узкие тесты вокруг генерации исходников, разбора диагностики и правил создания снимка проекта.

Для ручной проверки рискованных изменений достаточно пройти такой набор:

1. Создать скетч и добавить несколько `.pde`-вкладок.
2. Нажать Run без установленного Sketch Preview, установить его и запустить скетч повторно.
3. Получить ECJ-ошибку в `.pde` и убедиться, что diagnostic попал в консоль редактора.
4. Проверить `print`/`println` и падение скетча во время работы приложения превью.
5. Запустить скетч с файлами в `data/`.
6. Открыть управляемый скетчбук и внешнюю папку через системный выбор файлов.

## Что еще не закрыто

- `Import Library` и `AI Agent` в меню инструментов пока заглушки.
- Компилятор превью еще не делает полный сценарий экспорта, подписи и установки APK.
- Permissions, полное редактирование manifest, поиск библиотек, объединение ресурсов и инкрементальный кэш компиляции еще не сведены в законченный контракт слоя сборки.
- `MainActivity`, `CodeEditorView`, `SketchStore` и `SketchPropertiesActivity` уже крупные. Новые обязанности лучше выносить осознанно.

Ближайшие этапы разработки собраны в [docs/BUILD_PLAN.md](docs/BUILD_PLAN.md).
