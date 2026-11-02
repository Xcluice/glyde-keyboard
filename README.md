# Glyde Keyboard

Glass + gradient Android keyboard (Kotlin, custom Canvas view, Compose settings app).

## Roadmap
- [x] **Phase 1** — core typing, glass & gradient themes, key animations, haptics, settings app, icon
- [ ] **Phase 2** — swipe typing, offline suggestions, clipboard history, snippets
- [ ] **Phase 3** — Hindi / Urdu / Kashmiri layouts + transliteration
- [ ] **Phase 4** — emoji / GIF, voice typing, translator, toolbar, incognito, backup

## Build
CI builds a debug APK on every push (Actions → "Build APK" → artifact `glyde-debug-apk`).
Locally: `gradle assembleDebug` (Gradle 8.7+, JDK 17).
