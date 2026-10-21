# AutoHz Max — RedMagic Auto refresh rate up to 144 / 165 Hz

Tiny LSPosed module for **RedMagic (ZTE/nubia) phones on RedMagic OS 11 (Android 16)**.

> ⚠️ **Android 15 (RedMagic OS 10) is not supported since version 1.3.** Running 165 Hz through the module on Android 15
> global firmware caused screen flicker and reboots. On Android 15, version 1.3 hooks nothing, and if you had picked the
> 165 Hz choice that 1.2 added to Settings, it sets the refresh rate back to 144 Hz after the next reboot. On Android 15,
> update to 1.3 once (so that reset runs), then switch the module off or uninstall it.

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

**Settings** in the scope only updates the Auto choice's description so it names the panel's top rate (for example
165 Hz instead of 120 Hz). RedMagic OS 11 lists the fixed 165 Hz choice by itself.

If the app says the system framework part is "not loaded" after a reboot, the module is not active for System
Framework: re-check the scope in LSPosed / Vector and reboot again.

## How it works
`com.android.server.am.GlobalSettingFpsController.convertModeToValue(0)` picks the Auto high step (120 on a 120+ Hz panel).
The module hooks it (and `getSettingFps`) and replaces the result with `ScreenRefreshRateController.sHighestFps`, which the
firmware fills from the panel's real display modes at boot. Nothing is hard-coded to 165: with a stock DTBO you get 144.
Skipped when the developer "Lock refresh rate" switch is on or when a fixed mode is selected.

## Versions
- **1.3**: Android 16 only. Nothing is hooked on Android 15, and a leftover 165 Hz choice from 1.2 is set back to 144 Hz there.
  The 165 Hz choice that 1.2 added to Settings on Android 15 is removed.
- **1.2**: added a 165 Hz choice to Settings on Android 15 (withdrawn in 1.3: flicker and reboots on Android 15 global firmware).
- **1.1**: status page in the app, log lines in logcat.

## Notes
- Needs root + LSPosed/Vector like any Xposed module. No settings are written, nothing persists — uninstall or disable to revert.
- Only the **Auto** mode is affected. Fixed 144/165 modes behave as before.
- Battery: Auto still idles at 60 Hz, so the cost is the same as stock Auto with a higher ceiling while you interact.
- If the log says `ZTE refresh-rate controller not found`, that firmware moved/renamed the engine — open an issue with the firmware version.

Part of the [RM Tweaks](https://github.com/HammadY2346/rm-tweaks) work by Hammad Yasin (HammadY2346); the same switch lives
there as *Android framework → Auto refresh rate goes up to 165 Hz*.
