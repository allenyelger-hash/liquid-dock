# Liquid Dock

A floating Liquid Glass bottom navigation dock for Jetpack Compose on Android, featuring a frosted G2-continuous glass capsule base, an overhanging 3D magnifying water-droplet lens with real-time AGSL refraction and chromatic dispersion (Android 13+ / API 33+), spring-driven velocity deformation, and graceful fallbacks down to API 26.

## Modules

- **`:liquid-dock`** — Reusable Android Compose library (`com.liquiddock`) with Kotlin explicit API mode and Maven/JitPack publishing configured.
- **`:sample`** — Showcase & visual benchmark app (`com.liquiddock.sample`) with four scenes (dark animated feed, pure-white message list, colorful media grid, and an orthogonal optical ruler with uniform white/black fields).

## Requirements & Compatibility

Before integrating `:liquid-dock` into an Android app, note the current toolchain constraints:

- **Android minSdk**: `26` (full AGSL shader refraction on API 33+, translucent blur fallback on API 31–32, opaque fallback on API 26–30)
- **Android compileSdk**: `37` (required by Compose BOM `2026.09.00` / Compose `1.12.1`)
- **JDK**: `17+`
- **Kotlin / AGP 9 note**: `io.github.kyant0:backdrop:2.0.1` and this library compile with Kotlin `2.4.20` metadata. On AGP 9.x (whose default built-in Kotlin targets `2.2.x`), add the following to your consumer `gradle.properties` and apply `org.jetbrains.kotlin.android` (`2.4.20+`):
  ```properties
  android.builtInKotlin=false
  android.newDsl=false
  ```

## Installation (JitPack)

1. Add JitPack to your `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

2. Add the dependency to your module's `build.gradle.kts` (replace `<owner>` and `<tag>` with your GitHub repository owner and release tag):

```kotlin
dependencies {
    implementation("com.github.<owner>:liquid-dock:<tag>")
}
```

## Quick Start

Wrap your screen content in `LiquidDockHost` — it manages the live `Backdrop` recording, window insets (`contentWindowInsets`), and bottom scroll clearance automatically:

```kotlin
val tabs = listOf(
    DockTab("Home"),
    DockTab("Inbox", badgeCount = 13),
    DockTab("Discover"),
    DockTab("Profile"),
)

LiquidDockHost(
    tabs = tabs,
    selectedIndex = selectedIndex,
    onSelect = { selectedIndex = it },
    actionIcon = Icons.Filled.Add,
    actionContentDescription = "Create",
    onAction = { /* handle detached circular action button */ },
) { dockClearance ->
    // Pass `dockClearance` to your scrollable content's bottom padding
    MyContent(bottomPadding = dockClearance)
}
```

## Customization & Advanced Usage

- **Tab count**: Supports **2 to 5** tabs (`DockTab(label, badgeCount)`). Positive `badgeCount` renders a circular pill badge (`99+` when > 99) and appends the count to accessibility semantics.
- **Colors**: Pass `colors = LiquidDockDefaults.colors(...)` (`contentColor`, `badgeContainerColor`, `badgeContentColor`, `blurFallbackColor` for API 31–32, `legacyFallbackColor` for API 26–30). On API 33+, the base surface adapts dynamically to the live `Backdrop`.
- **Detached action button**: Provide both `actionIcon` and `onAction` (plus a meaningful `actionContentDescription`), or omit both (`null`) to display the capsule dock alone.
- **Custom Backdrop & WebView**: If your screen needs multi-layer composition or contains a `WebView` that should not be wrapped inside `LayerBackdrop`, call `LiquidGlassDock(backdrop = customBackdrop, ...)` directly and pad your scrollable content using `dockClear(screenWidth) + navBarBottom`.

## Platform Support

| Android API | Rendering Path |
|---|---|
| **API 33+ (Android 13+)** | Live AGSL `RuntimeShader`: adaptive frosted glass base, 360° concentric meniscus lens, separate label/badge refraction & 7-band spectral dispersion, external `ClipOp.Difference` shadows |
| **API 31–32 (Android 12–12L)** | Real-time backdrop blur, translucent dark pill indicator, library rim highlight |
| **API 26–30 (Android 8–11)** | Opaque neutral fallback surface with the same geometry, gestures, and spring animations |

## Building & Testing

```bash
./gradlew :liquid-dock:testDebugUnitTest :liquid-dock:lintDebug :liquid-dock:assembleRelease
./gradlew :sample:assembleDebug
```

The sample Debug APK is output to `sample/build/outputs/apk/debug/sample-debug.apk`.

## License & Attribution

Licensed under the [Apache License, Version 2.0](LICENSE). See [NOTICE](NOTICE).

Portions of `liquid-dock/src/main/java/com/liquiddock/internal/` are adapted from [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) (Apache License 2.0). See [THIRD_PARTY_NOTICES/AndroidLiquidGlass.md](THIRD_PARTY_NOTICES/AndroidLiquidGlass.md) for full provenance and adaptation details.
