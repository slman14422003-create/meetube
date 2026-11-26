---
name: meetube-glass
description: Progress log and rules for MeeTube's glass engine (GlassView, GlassSource, GlassDrawable, SheetGlass, NavLens, FocusTimer) and release shrinking. Read before touching glass code or build settings.
---

# Done

- Release shrinking: mobile and common release set minifyEnabled and shrinkResources true; benchmark matches release in both modules. mobile/proguard-rules.pro and common/proguard-rules.pro keep Glide, media3, sabr-media3 protobuf, BatterySaver reflection and the glass classes.
- Glass quality: Glass.QUALITY_FULL (7-tap lens), QUALITY_LIGHT (3-tap lens, small glass only), QUALITY_FLAT (no sampling, plain tint). Cached in a static, loaded by Glass.init, defaulted from NavLens.lowEnd. Glass.epoch() changes with it. Settings row under Customization > Effects, English and Arabic strings.
- GlassView: no lens for glassOnGlass or glass over 40% of the screen area. Position tracking is gated by GlassSource.version() plus a field-read stamp of the view's place up the parent chain; getLocationInWindow runs only when either changed, and not at all when the view is not shown, Flat, or below API 31.
- GlassDrawable: same version gating.
- GlassSource: node re-recorded only after a child or itself invalidated, resized, changed sampling, or the quality epoch changed; otherwise reused.
- SheetGlass: Flat takes no copy. Full takes live copies at most 10 per second at 1/12 scale, Light and others one copy at 1/8. Bitmaps reused (3 live, 1 still). Copies stop while either window is not visible or the sheet moved in the last 150 ms, and restart on window focus. From API 31 the GPU blurs the copy through a RenderNode for still sheets too; CPU blur stays below API 31.

# Rules

- Never read SharedPreferences while drawing; use Glass.quality() and Glass.liveBlur().
- Keep API below 31 and Battery Saver paths unchanged.
- Do not touch layouts or adapters in glass-only steps.
- No README files.

# Not verified

- No device or Gradle build was run in the sandbox: build and test on a device (scroll under glass, drag a sheet over the player, switch quality, release APK with R8).
