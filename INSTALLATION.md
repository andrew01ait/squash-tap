# Install, update and diagnose

[Official download page](https://squash-tap-andrew.endruuu.chatgpt.site/) · Current watch APK: **v0.14.0 / code 15**.

The APK runs on the watch. Wear Installer 2 is a helper on the Android phone; no PC development tools are required. The app is currently distributed by APK through this site, not the watch app store.

Choose a sport at launch, then create or resume a match. Players and Settings are shared. Existing squash history stays available after an update.

## Initial installation using an Android phone

1. Install [Wear Installer 2](https://play.google.com/store/apps/details?id=org.freepoc.wearinstaller2) on the phone and download the current watch APK from the official page.
2. Connect phone and watch to the same Wi-Fi network or connect the watch to the phone's hotspot. Find WLAN in the normal watch Settings, not only Developer options.
3. Enable watch Developer options by tapping Build number seven times. Firmware grouping can vary; on the OnePlus look under Settings → Other → About watch → Other version info. Enable ADB debugging and Wireless debugging.
4. Open the Wireless debugging details and use the **watch's** IP address in Wear Installer 2. Open Pair new device on the watch. In the installer's pairing field enter the six-digit pairing code, one space, then the pairing port; keep that watch screen open until pairing succeeds.
5. Return to the watch's main Wireless debugging screen. Connect the installer using its **watch IP:connection port**. This is a different port from the pairing port and may change.
6. Choose Custom APK, select the download and install. Open Squash Tap on the watch, then MENU → Settings to confirm the version.
7. Turn wireless debugging off when no longer needed. Starting play may request notifications for the quiet ongoing-match shortcut.

[Wear Installer 2 instructions](https://freepoc.org/wear-installer-2-help-page/) provide the helper's full walkthrough. Initial phone-only installation, the v0.1→v0.2 in-app update and retained score were user-reported on OnePlus Watch 2R / Wear OS 5; see [DEVICE_TESTS.json](DEVICE_TESTS.json). New versions still need their own acceptance checks.

## German OnePlus labels

| English | German labels to look for |
| --- | --- |
| Settings | Einstellungen |
| Other | Sonstiges / Andere |
| About watch | Über die Uhr / Info zur Uhr |
| Other version info | Weitere Versionsinformationen |
| Build number | Build-Nummer |
| Developer options | Entwickleroptionen |
| Wireless debugging | Drahtloses Debugging / Debugging über WLAN |
| Pair new device | Neues Gerät koppeln / Gerät mit Kopplungscode koppeln |
| IP address and port | IP-Adresse und Port |

## Updating an existing installation

Open MENU → Settings → Check for updates, enable the direct-install permission if requested, download the verified update and approve the watch's installation confirmation. Reopen Squash Tap afterward and check Settings for its version. If the system installer flow is unavailable, install the latest official APK over the existing app with Wear Installer 2.

An in-place update with the same package/signing key replaces the installed application; it does not install a separate v0.2/v0.3 app alongside it. Downloaded APK files may still remain wherever the user saved them. Do not uninstall or clear watch storage to update, because that can remove local scores/history.

## Quick acceptance after updating

- Check BO3/BO5, optional names, scoring, Undo and explicit NEXT between sets.
- Check Results, filters/pagination, manual set versus independent past-match entry, retirement and confirmed deletion.
- Check named Back, hardware Back and the native swipe gesture from nested panels. Return from a result should retain its filtered page and scroll.
- Check a QR transfer with synthetic data before important imports. Names added on the phone must not silently reassign a match.
- During real play check availability/ambient behavior; after Pause/completion check that normal watch sleep returns. Record battery observations separately from automated test outcomes.

## Diagnostics and common problems

MENU → Settings → Diagnostics offers an explicit Send to phone QR. Scan it and use the browser decoder to copy/save the report. This diagnostic QR is separate from results/name/CSV transfers. Treat the report and any active transfer link as private when sharing with support.

If installation cannot connect, refresh the watch IP/connection port; do not use the phone's hotspot IP or the pairing port. If the updater rejects an APK, keep the installed app and check that the download is the official package/newer version; signature validation must not be bypassed.

If the app returns to the watch face, record the version/time and export diagnostics. An activity pause, installation exit or missing battery exemption is not by itself evidence of a crash. Existing reports confirm foreground retention since v0.3, but do not establish every later firmware, swipe, ambient or battery scenario.
