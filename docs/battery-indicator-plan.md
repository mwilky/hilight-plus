# Battery indicator: implementation plan

Status: proposed, awaiting go-ahead. Scope agreed in conversation on 2026-09-15.

## Scope (v1)

- Charging gauge, full state, and low-battery warning on the 8-LED ring.
- Battery-only patterns, kept out of the app/contact rule editor.
- Visibility: face-down only, or always.
- Battery display is a *layer* beneath alerts. Calls always interrupt it. Notifications interrupt it by default, with a toggle to let battery win over notifications.
- Colour: auto gradient by level (red → amber → green) or a fixed colour.
- Quiet hours respected via the existing INHERIT / ALWAYS / SKIP tri-state. DND is not applied to battery.
- Settings live on a third Home tab.
- Onboarding lists Battery as a feature.

Out of scope: custom colour bands, always-on-while-unplugged level display, blending alerts over the gauge, LED test button (can be added later).

## Design summary

### Model (`Models.kt`, `RuleJson.kt`)

```kotlin
enum class BatteryVisibility(val id: String) { OFF, FACE_DOWN_ONLY, ALWAYS }
enum class BatteryPattern(val id: String, val titleRes: Int) {
    GAUGE,        // static clockwise fill, fractional leading LED
    CHARGE_FILL,  // gauge + breathing leading LED + droplet running into the fill
    GRADIENT_RING // all 8 lit, hue by level
}
enum class BatteryFullTimeout(val id: String, val minutes: Int?) { STAY_ON, ONE_MIN, FIVE_MIN, THIRTY_MIN }

data class BatterySettings(
    val visibility: BatteryVisibility = OFF,
    val chargingPattern: BatteryPattern = CHARGE_FILL,
    val autoColor: Boolean = true,
    val color: Long = DEFAULT_BATTERY_COLOR,
    val lowWarningEnabled: Boolean = true,
    val lowThresholdPercent: Int = 20,
    val fullTimeout: BatteryFullTimeout = FIVE_MIN,
    val overridesNotifications: Boolean = false,
    val quietHoursMode: QuietHoursMode = INHERIT
) { fun toJson(); companion fun fromJson() }
```

`BatteryPattern` is a separate enum, so `RuleEditor.kt:255` (`PatternMode.entries.filter { it != OFF }`) is untouched and battery patterns never appear in rule editors.

### Renderer (`core/PatternRenderer.kt`)

New function alongside `renderFrame`, not an extension of it:

```kotlin
fun renderBatteryFrame(
    pattern: BatteryPattern, levelPercent: Int, charging: Boolean, full: Boolean, low: Boolean,
    autoColor: Boolean, fixedColor: Long, brightness: Float, elapsedTimeMs: Long, ledCount: Int
): IntArray
```

Behaviour:

- **Colour source**: `autoColor` → hue interpolated 0° (red) → 40° (amber at ~50%) → 120° (green). Fixed → `fixedColor`.
- **GAUGE**: `filled = level / 100 * count`. Whole LEDs at full brightness, leading LED scaled by the fractional remainder (min 0.15 so it is visible), rest off.
- **CHARGE_FILL**: GAUGE, plus leading LED breathes (2000 ms), plus every ~3000 ms a single dim droplet travels from LED 0 to the leading LED over ~600 ms.
- **GRADIENT_RING**: all LEDs in the level colour, slow breathe when charging, solid when not.
- **full**: overrides pattern. All LEDs solid in level colour, single sweep every 10 s (reuse comet maths).
- **low && !charging**: gauge of remaining LEDs with a slow heartbeat (pulse maths, 1800 ms) in the level colour.

### Engine (`core/LightEngine.kt`)

- Replace the four hardcoded `ambient*` fields with:
  - `batteryConfig: BatterySettings?` (set from app), `batteryLevel`, `batteryCharging`, `batteryFull`.
  - `batteryLayerActive()`: config visibility != OFF, and (charging, or full within timeout, or low with warning enabled).
  - `batteryVisible()`: `batteryLayerActive() && AlertRenderPolicy.canShowAlert(requiresFaceDown = visibility == FACE_DOWN_ONLY, DndMode.ALWAYS, quietHoursMode, ...)`.
  - `fullSinceMs` recorded when `batteryFull` flips true, for the timeout.
- `tick()` priority: test → call → (if `!overridesNotifications || !batteryVisible()`) direct → cyclic → **battery** → off. When battery renders, call `renderer.renderBatteryFrame` instead of `renderFrame`.
- `removeAlert` / `clearAlert` (`LightEngine.kt:350, 366`): replace `ambientPattern == "off"` checks with `!batteryVisible()`.
- `turnOff` unchanged. Master off is handled app-side by pushing a config with visibility OFF.
- New: `setBatteryConfig(json: String)`, `setBatteryState(level: Int, charging: Boolean, full: Boolean)`.

### AIDL / daemon / bridge

- `IHiLightService.aidl`: `void setBatteryConfig(String json); void setBatteryState(int level, boolean charging, boolean full);`
- `HiLightDaemonService.kt`: forward both to the engine.
- `ShizukuBridge.kt`: `setBatteryConfig`, `setBatteryState` via `runRemote`.

### App side (`LightController.kt`)

The daemon runs under Shizuku and cannot register broadcasts, so battery comes from the app process, which is kept alive by the notification listener service, same as the DND receiver today.

- Register `ACTION_BATTERY_CHANGED` in `init` (sticky, so initial state arrives immediately). Derive level, charging (`BATTERY_STATUS_CHARGING`), full (`BATTERY_STATUS_FULL || level >= 100`). Push only when any of the three changes.
- Collect `store.battery` combined with `store.isEnabled`; push `setBatteryConfig(json)`, substituting visibility OFF when master is disabled.
- Re-push both in `onAvailabilityChanged` and in `pushLiveConditions`.
- Orientation: add `DeviceOrientationDetector.TOKEN_BATTERY`. Retain when `visibility == FACE_DOWN_ONLY` and the layer would be active (charging / full / low); release otherwise.

### Store (`AppStore.kt`)

- `KEY_BATTERY_JSON = stringPreferencesKey("battery_settings_json")`.
- `val battery: Flow<BatterySettings>`, `suspend fun setBattery(settings: BatterySettings)`.
- `SettingsSnapshot` gains `battery: BatterySettings`, populated in `buildSnapshot`, default in `DEFAULT_SETTINGS_SNAPSHOT`.

### UI

- `HomeScreen.kt`: `rememberPagerState { 3 }`; `HomePageToggle` becomes three connected buttons (leading / middle / trailing shapes) with `Icons.Rounded.BatteryChargingFull` and `home_tab_battery`. Page 2 → `HomeBatteryPage`.
- New `ui/HomeBatterySection.kt`:
  - `ShizukuStatusCard` at top, as the other pages do.
  - `SectionHero` with a live `DiffusedRingPreview` fed by `renderBatteryFrame` using the real level from `BatteryManager`, animating on `withFrameMillis`.
  - Master switch (visibility != OFF).
  - "Show" segmented: Face down only / Always.
  - Charging style chips, each with an `AnimatedRingBadge`-style preview rendered by `renderBatteryFrame` at a fixed 65 %.
  - Colour: Auto (by level) switch; when off, colour swatch reusing the picker from `RuleEditor`.
  - Low battery warning switch + threshold slider (5–50 %, step 5).
  - After full: STAY_ON / 1 min / 5 min / 30 min.
  - Notifications interrupt battery switch (inverse of `overridesNotifications`, phrased positively).
  - Quiet hours tri-state, reusing the existing rule editor row component.
- `HomeViewModel.kt`: `fun setBattery(settings: BatterySettings)`.
- `OnboardingScreen.kt` `FeaturesHighlightCard`: add a fourth `FeatureRow` (`Icons.Rounded.BatteryChargingFull`, `MaterialShapes.Pill`).
- `strings.xml`: `home_tab_battery`, battery pattern titles, section copy, onboarding feature title/desc.

## Steps and verification

1. **Model + JSON**: `BatterySettings`, enums, `toJson`/`fromJson`, defaults.
   → verify: new `BatterySettingsJsonTest` round-trips and tolerates missing fields (mirrors `RuleModelJsonTest`).
2. **Renderer**: `renderBatteryFrame` with the three patterns plus full/low overrides and gradient colour.
   → verify: `PatternRendererBatteryTest`: 0 % lights nothing, 100 % lights all, 62 % lights 4 full + partial 5th, full override fills all, low+unplugged has non-zero frame, auto colour at 0/50/100 is red/amber/green.
3. **Engine**: battery fields, `batteryLayerActive`/`batteryVisible`, tick priority, `overridesNotifications` branch, ambient removal, full timeout.
   → verify: `AlertRenderPolicyTest` unchanged and passing; add `LightEngineBatteryPriorityTest` if the engine can be driven without a lights service, otherwise verify by manual matrix in step 9.
4. **AIDL + daemon + bridge**: two new methods end to end.
   → verify: `./gradlew :app:assembleDebug` compiles (AIDL regen).
5. **Store + snapshot + view model**.
   → verify: existing tests pass; snapshot default has visibility OFF.
6. **LightController**: battery receiver, config push, orientation token, master-off gating.
   → verify: logcat shows one `setBatteryState` per real change, none per periodic tick.
7. **Battery tab UI** + three-way toggle.
   → verify: Compose previews for empty/charging/low states; tab switch animates through all three pages.
8. **Onboarding + strings**.
   → verify: onboarding preview shows four feature rows.
9. **Device verification** on the Pixel (adb on D:, see memory note):
   - Plug in at ~60 %: gauge shows 5 LEDs, leading one breathing, droplet visible.
   - Post a notification: alert shows, then returns to gauge on expiry.
   - Enable "battery overrides notifications": notification does not interrupt; incoming call still does.
   - Face-down only: ring dark until phone is flipped.
   - Quiet hours SKIP during window: ring dark while charging.
   - Master toggle off: ring dark, back on restores gauge.
   - Unplug below threshold: heartbeat gauge; above threshold: dark.
10. **Commit** as `feat(battery): charging and level indicator with dedicated Home tab`.

## Open decisions (defaults chosen, say if you disagree)

- Low threshold slider range 5–50 % in steps of 5, default 20.
- Full timeout default 5 min.
- Gradient hue stops: red 0 %, amber 50 %, green 100 %.
- No LED "Test" button on the battery tab in v1. The on-screen live ring is the preview.
- DND not applied to battery.
