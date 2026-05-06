# APDE 2.0

MVP Android-приложения для редактирования и запуска Processing Java Android Mode скетчей.

## Что уже есть

- Многотабовый редактор `.pde`.
- Добавление и удаление табов.
- Автосохранение табов в `SharedPreferences`.
- Подсветка Java/Processing ключевых слов, чисел, строк и комментариев.
- Панель диагностик компиляции с ручным изменением высоты.
- Canvas runner для быстрого запуска MVP-скетчей внутри приложения.
- Компиляционный слой `ProcessingCompiler`, который превращает поддержанные PDE-команды в исполняемую модель `SketchProgram`.
- Единый файл темы `EditorTheme.java` для цветов интерфейса и правил синтаксиса.

## Поддержанный MVP-синтаксис

```java
void setup() {
   size(640, 480);
}

void draw() {
   background(18, 20, 25);
   fill(41, 182, 166);
   noStroke();
   ellipse(320, 240, 160, 160);
   rect(20, 20, 100, 80);
   line(0, 0, width, height);
   text("APDE 2.0", 250, 250);
}
```

Цвета поддерживают `gray` и `r, g, b`. Для координат доступны числа, `width`, `height`, `frameCount`, а также простой `sin(frameCount)` / `cos(frameCount)`.

## Как открыть

1. Установить Android Studio для Apple Silicon.
2. Через Android Studio поставить Android SDK Platform 35.
3. Открыть папку `APDE 2.0`.
4. Дождаться Gradle sync.
5. Запустить `app` на эмуляторе или физическом Android-устройстве.

В текущей машине системный JDK не зарегистрирован, но найден JDK внутри Processing:

```bash
export JAVA_HOME="/Applications/Processing.app/Contents/app/resources/jdk"
```

Gradle wrapper уже добавлен. После установки Android SDK можно проверить сборку так:

```bash
cd "APDE 2.0"
./gradlew assembleDebug
```

Если Android SDK установлен нестандартно, создай `local.properties`:

```properties
sdk.dir=/Users/denisvuhin/Library/Android/sdk
```

Текущая проверка дошла до Android Gradle Plugin и остановилась на ожидаемой ошибке `SDK location not found`.

## Следующий инженерный слой

Текущий MVP runner нужен, чтобы приложение уже редактировало и запускало скетчи. Полноценный Processing Android Mode compiler должен добавляться отдельным backend-слоем, не ломая UI:

1. PDE preprocessor: объединение табов, генерация Java-класса, разбор imports.
2. Processing Android core: подключение `processing-core` / Android runtime.
3. Java compiler: ECJ или другой compiler, который работает на Android.
4. Dex step: D8/R8 для `.class -> .dex`.
5. APK builder/signing: сборка debug APK на устройстве.
6. Install/run: запуск собранного sketch Activity через intent.

Ключевые точки расширения уже выделены: `ProcessingCompiler`, `CompileResult`, `Diagnostic`, `SketchProgram`, `SketchRunnerView`.

## Темы и синтаксис

Текущая тема находится в:

```text
app/src/main/java/com/apde2/EditorTheme.java
```

Там задаются:

- цвета редактора, табов, консоли и иконок запуска;
- цвета категорий подсветки;
- regex-правила `SyntaxRule`.

Для нескольких встроенных тем следующий шаг простой: добавить методы вроде `dark()`, `light()`, `highContrast()` и переключатель активной темы. Для пользовательских тем позже лучше добавить JSON-файлы в `assets/themes/` и загрузчик, который будет собирать такой же `EditorTheme`.
