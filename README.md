# AutoHz Max — RedMagic Auto refresh rate up to 144 / 165 Hz

Tiny LSPosed module for **RedMagic (ZTE/nubia) phones on RedMagic OS 10 / 11 (Android 15 / 16)**.

RedMagic's **Auto** refresh-rate mode only ever switches between **60 Hz and 120 Hz**, even though the panel does 144 Hz
(and 165 Hz with a 165 Hz DTBO mod). Picking the fixed 144 / 165 mode works, but then most apps are still sent to 120 Hz
by the smart engine and the panel never idles down. This module keeps **Auto** exactly as it is — idle at 60, games on
their Game Space setting — and simply raises Auto's high step to **the highest rate the panel reports** (144 stock,
165 with a 165 Hz mod). The Settings page, Game Space and the developer "Lock refresh rate" switch all keep working.

Made for the RedMagic 10S Pro (NX789J, firmware 11.0.11) and written against ZTE's `ScreenRefreshRateController`, which
is the same engine on the RedMagic 11 Pro (NX809J) and other RedMagic OS 11 phones — so it should work as-is there;
the LSPosed log tells you if it did not.

## Install
1. Download **[release/AutoHzMax.apk](release/AutoHzMax.apk)** (built by GitHub Actions from this repo) and install it.
2. LSPosed (or Vector) → Modules → **AutoHz Max** → switch it on and tick **System Framework** (`android`),
   **Settings** (`com.android.settings`) and **AutoHz Max** in its scope.
3. **Reboot** (framework modules only load at boot).
4. Settings → Display → Refresh rate → **Auto**. If Auto was already selected, pick another rate and then Auto again once.

Check: open the AutoHz Max app. It shows whether LSPosed loaded the module and what the hook in the system framework
reported, for example `working: Auto now goes 120 -> 165 Hz`. The same lines are in LSPosed / Vector → Logs →
**Modules** (lines starting with `AutoHzMax:`) and in `adb logcat -s AutoHzMax`. A refresh-rate overlay (Developer
options → Show refresh rate) should read 165/144 while scrolling and 60 when idle.

**Fixed 165 Hz on Android 15.** RedMagic OS 10's refresh-rate page only knows 60 / 90 / 120 / 144 Hz, so a 165 Hz panel
gets no 165 Hz choice. With Settings in the scope the module adds the panel's top rate there (it writes the same mode
RedMagic OS 11 uses for 165 Hz, and the framework part runs that mode at 165). On Android 16 the firmware already lists it
and nothing is added. The log says `Settings: added a 165 Hz choice` when it works.

If the app says the system framework part is "not loaded" after a reboot, the module is not active for System
Framework: re-check the scope in LSPosed / Vector and reboot again.

## How it works
`com.android.server.am.GlobalSettingFpsController.convertModeToValue(0)` picks the Auto high step (120 on a 120+ Hz panel).
The module hooks it (and `getSettingFps`) and replaces the result with `ScreenRefreshRateController.sHighestFps`, which the
firmware fills from the panel's real display modes at boot. Nothing is hard-coded to 165: with a stock DTBO you get 144.
Skipped when the developer "Lock refresh rate" switch is on or when a fixed mode is selected.

## Notes
- Needs root + LSPosed/Vector like any Xposed module. No settings are written, nothing persists — uninstall or disable to revert.
- Only the **Auto** mode is affected. Fixed 144/165 modes behave as before.
- Battery: Auto still idles at 60 Hz, so the cost is the same as stock Auto with a higher ceiling while you interact.
- If the log says `ZTE refresh-rate controller not found`, that firmware moved/renamed the engine — open an issue with the firmware version.

Part of the [RM Tweaks](https://github.com/HammadY2346/rm-tweaks) work by Hammad Yasin (HammadY2346); the same switch lives
there as *Android framework → Auto refresh rate goes up to 165 Hz*.
