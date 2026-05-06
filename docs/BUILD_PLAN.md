# APDE 2.0 Build Plan

## Phase 1: Editor MVP

- Native Android Java project.
- Programmatic UI without external dependencies.
- Editor tabs.
- Syntax highlighting.
- Persistent sketch storage.
- Diagnostics panel.
- Preview runner for supported Processing calls.

Status: implemented.

## Phase 2: Processing language coverage

- Variables and expressions.
- `mouseX`, `mouseY`, `pmouseX`, `pmouseY`.
- `if`, `for`, functions.
- More drawing APIs: `arc`, `triangle`, `quad`, `beginShape`.
- Touch input mapping.
- Better parser instead of regex-line parsing.

## Phase 3: Real Android compiler backend

- Extract compiler interface:
  - `compile(files, target)`
  - `diagnostics`
  - `artifact`
- Add PDE preprocessor compatible with Processing Java mode. Status: first project-generator layer implemented.
- Generate Processing Android project snapshot with `PApplet`, Android manifest, launcher activity, Gradle files, and Processing Android core dependency. Status: implemented.
- Bundle Processing Android runtime.
- Add ECJ Java compilation on-device.
- Add D8 dex generation.
- Build debug APK and sign it.
- Install and launch generated sketch package.

## Phase 4: IDE quality

- File tree and sketchbook.
- Rename tabs.
- Error line gutter.
- Formatting.
- Search/replace.
- Export/share APK.
- Library manager.
- Examples browser.
