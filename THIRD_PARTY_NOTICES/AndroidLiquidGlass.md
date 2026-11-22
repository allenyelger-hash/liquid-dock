# AndroidLiquidGlass / Backdrop

Copyright 2025 Kyant. Apache License, Version 2.0; full text in
`AndroidLiquidGlass-LICENSE.txt` in this directory and bundled in the library/sample assets.
Upstream does not supply a separate NOTICE file at this revision.

- Repository: https://github.com/Kyant0/AndroidLiquidGlass
- Verified branch: `kmp`
- Source revision: `65ab177e90e5c1d8c62e70cf7755841982da65f6`
- Libraries: `io.github.kyant0:backdrop:2.0.1`, `io.github.kyant0:shapes:1.2.1` (Maven Central)
- Documentation: https://kyant.gitbook.io/backdrop

The files in `liquid-dock/src/main/java/com/liquiddock/internal/` derive from:

- `components/LiquidBottomTabs.kt`
- `components/LiquidBottomTab.kt`
- `components/LiquidButton.kt`
- `utils/DampedDragAnimation.kt`
- `utils/InteractiveHighlight.kt`
- `utils/DragGestureInspector.kt`
- `internal/Shaders.kt` (optical shader inspiration)

Adaptations in this library include:
- Package relocation to `com.liquiddock` / `com.liquiddock.internal` and public API packaging (`LiquidGlassDock`, `LiquidDockHost`, `LiquidDockColors`, `DockTab`, `DockGeometry`).
- Android Compose frame-clock animation loops (`withFrameNanos`) for smooth 60–240Hz touch tracking, velocity-driven shape deformation, held-drag dwell switching, external-navigation precedence, and lifecycle cleanup.
- Single-scale responsive normalized `DockGeometry` supporting 2–5 tabs, optional badge counts, optional detached circular action button, and unified page clearance calculation.
- Globally C-infinity G2 continuous capsule SDF (`sdG2Capsule` / `gradG2Capsule`), 1:1 unscaled two-layer backdrop recording (`recordedBase` + `recordedLabels`), external `ClipOp.Difference` floating shadows (`DockFloatingShadowCache` / `DockLensShadowCache`), and API 33+ RuntimeShader optical transfer (`dockBaseGlass`, `dockLens`, `dockLabelLens`) with API 31–32 blur fallback and API 26–30 opaque fallback.

No upstream trademarks or endorsements are implied.
