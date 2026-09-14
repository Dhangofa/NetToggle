# NetToggle

<div align="center">
  <img src="https://raw.githubusercontent.com/Dhangofa/NetToggle/refs/heads/main/fastlane/metadata/android/en-US/images/icon.png" alt="NetToggle Icon" width="128">
  
  [![Build status](https://img.shields.io/github/actions/workflow/status/Dhangofa/NetToggle/build.yml?label=Build%20Status&status=Passing)](https://github.com/Dhangofa/NetToggle/actions/workflows/build.yml)
  [![GitHub release](https://img.shields.io/github/v/release/Dhangofa/NetToggle?label=Release&color=B57EDC)](https://github.com/Dhangofa/NetToggle/releases)
  [![License](https://img.shields.io/github/license/Dhangofa/NetToggle?label=License&color=FF9100)](LICENSE)

</div>
<p align="center">
  <a href="https://f-droid.org/packages/com.dhangofa.networktoggle">
    <img src="https://f-droid.org/badge/get-it-on.png" alt="Get it on F-Droid" height="71" align="middle"></a>
  <a href="https://apt.izzysoft.de/packages/com.dhangofa.networktoggle">
    <img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroidButtonGreyBorder_nofont.png" alt="Get it on IzzyOnDroid" height="48" align="middle"></a>
  <a href="https://github.com/dhangofa/NetToggle/releases/latest">
    <img src="https://raw.githubusercontent.com/Kunzisoft/Github-badge/main/get-it-on-github.png" alt="Get it on GitHub" height="71" align="middle"></a>
  <a href="https://rookieenough.github.io/Orion-Data/redirect.html?id=nettoggle">
    <img src="https://raw.githubusercontent.com/RookieEnough/Orion-Store/refs/heads/main/assets/orion-badge.png" alt="Get it on Orion Store" height="48" align="middle"></a>
  <a href="https://www.openapk.net/nettoggle/com.dhangofa.networktoggle/">
    <img src="https://www.openapk.net/images/openapk-badge.png" alt="Get it on OpenAPK" height="69" align="middle"></a>
</p>

---

## 📖 Overview

NetToggle is a lightweight Android Quick Settings (QS) tile app for cellular network control. It allows you to create custom tile cycles, target one or both SIMs, build capability-aware shortcuts, execute secure broadcast automations, and troubleshoot network states with detailed diagnostics.

NetToggle executes privileged telephony commands via **Root** or **Shizuku** that standard Android apps cannot access. For older Android releases (Android 7–11), it utilizes an efficient headless reflection mechanism, while Android 12+ relies on native direct command dispatching.

---

## ⚙️ Network Modes

* **Preferred 5G (5G/4G/3G/2G):** Recommended for daily use. Allows 5G SA and NSA (including UW, UC, 5G+, or 5G++ where supported) with LTE and legacy fallback.
* **5G Only (NR Only):** Disables 4G, 3G, and 2G. Intended strictly for 5G SA networks. Service and calls may drop on NSA networks or without SA and VoNR provisioning.
* **Preferred 4G (4G/3G/2G):** Disables 5G while retaining LTE and legacy fallback. Useful for battery saving, reducing device heat, or stabilizing connections in fringe 5G areas.
* **4G Only (LTE/LTE-A):** Locks to LTE without 3G/2G fallback. Calls require VoLTE; carrier aggregation is controlled by the modem and network.
* **Preferred 3G (3G/2G):** Disables 4G and 5G while allowing supported 3G and 2G networks.
* **2G Only (GSM/EDGE):** Locks to 2G where legacy service remains available.

> **Note:** If calls or cellular service fail in an *Only* mode, switch back to **Preferred 5G** or **Preferred 4G**.

---

## 🔄 Quick Tile Cycle

* **Custom Sequence:** Choose an ordered cycle of 2 or 3 modes.
* **Live State:** Shows the active mode with an Auto, SIM 1, SIM 2, or Both badge.
* **Auto Restore:** Automatically restores your preferred mode after a configurable cooldown if Android, thermal management, or the carrier reverts it.
* **Efficient Refresh:** Controlled checks avoid continuous polling and unnecessary battery drain.

---

## 📱 Target SIM Selection

* **Auto:** Dynamically resolves the active data subscription and maps it to the physical SIM slot using native Android APIs.
* **SIM 1 or SIM 2:** Applies network mode changes to the selected physical SIM slot.
* **Both SIMs:** Reconciles and synchronizes mutually supported modes across both SIMs simultaneously and reports any partial failures.

> **Tip:** If Auto detection fails on unusual hardware, select a SIM manually.

---

## 🪄 Shortcuts & OS Routines

* **Capability-Aware Actions:** Create up to 4 routine shortcuts with custom names, network modes, and SIM targets.
* **1-Tap Launcher Widgets:** Pin direct toggle actions to your home screen launcher.
* **OEM Automation:** Integrates seamlessly with **Samsung Modes & Routines**, **Google Assistant**, and third-party taskers.

---

## 📡 Broadcast Automation & ADB

Control NetToggle programmatically from **Tasker**, **MacroDroid**, **Automate**, or **ADB** shell scripts:

* **Opt-In Security:** External automation is disabled by default and requires opt-in with a secret authorization token.
* **Live ADB Command Preview:** Interactive builder generates ready-to-run shell commands and intent extras with one-tap clipboard copy.
* **Sample ADB Shell Broadcast:**
  ```bash
  am broadcast -a com.dhangofa.networktoggle.SET_NETWORK_MODE \
    -n com.dhangofa.networktoggle/.AutomationReceiver \
    --es mode "5G" \
    --ei sim 1 \
    --es token "YOUR_SECRET_TOKEN"
  ```
* **Supported Modes:** `5G_ONLY` (or `5G`), `4G_ONLY` (or `4G`, `LTE`), `PREF_5G` (or `PREFERRED_5G`), `PREF_4G` (or `PREFERRED_4G`), `PREF_3G` (or `PREFERRED_3G`), `2G_ONLY` (or `2G`)
* **Supported SIM Values:** `1` (SIM 1), `2` (SIM 2), `3` (Both)

---

## 🔌 Requirements & Permissions

* **Root or Shizuku Required:** Either must be active and authorized. Shizuku is recommended and can run through Root, ADB, or Wireless Debugging (trusted forks may add start-on-boot, watchdog, or TCP mode). Root mode supports **Magisk**, **KernelSU**, **APatch**, and compatible `su` managers.
* **Read Phone State:** Required for fast native SIM mapping and hardware capability detection. Without it, privileged shell fallbacks are used where available.
* **Run at Startup:** Briefly clears stale tile state after boot or app update. No persistent background service is used.

---

## 🧠 Smart Capability Filtering

* **Hardware Baseband Detection:** Evaluates device baseband properties (`ro.telephony.default_network`) to hide unsupported network modes.
* **Dual-SIM Mutual Support:** When targeting Both SIMs, the UI automatically offers only modes supported by both slots.
* **2G/3G Sunset Registry:** Built-in global carrier database dynamically hides deprecated legacy network options on known sunset networks.

---

## 🎨 Design & Themes

* Clean Material 3 interface optimized for both portrait and landscape orientations.
* Interactive setup card, segmented controls, Fluid Animations.
* Supports **Auto (System)**, **Light**, **Dark**, and high-contrast **AMOLED** themes with custom tactile feedback.

---

## 🛠️ Diagnostics & Troubleshooting

* **In-App Diagnostic Logs:** View and copy detailed reports including executed commands, exit codes, raw output (stdout/stderr), exceptions, execution state, and SIM resolution.
* **Error Banners:** Immediate visual feedback with recovery guidance when a command or permission fails.

---

## 📸 Screenshots

<table>
  <tr>
    <td width="25%" align="center"><img src="https://raw.githubusercontent.com/Dhangofa/NetToggle/refs/heads/main/fastlane/metadata/android/en-US/images/phoneScreenshots/Frame 1.png" alt="Multi Theme" width="100%"></td>
    <td width="25%" align="center"><img src="https://raw.githubusercontent.com/Dhangofa/NetToggle/refs/heads/main/fastlane/metadata/android/en-US/images/phoneScreenshots/Frame 2.png" alt="Setup Card" width="100%"></td>
    <td width="25%" align="center"><img src="https://raw.githubusercontent.com/Dhangofa/NetToggle/refs/heads/main/fastlane/metadata/android/en-US/images/phoneScreenshots/Frame 3.png" alt="Target SIM Setup" width="100%"></td>
    <td width="25%" align="center"><img src="https://raw.githubusercontent.com/Dhangofa/NetToggle/refs/heads/main/fastlane/metadata/android/en-US/images/phoneScreenshots/Frame 4.png" alt="Quick Tile Cycle Setup" width="100%"></td>
  </tr>
  <tr>
    <td width="25%" align="center"><img src="https://raw.githubusercontent.com/Dhangofa/NetToggle/refs/heads/main/fastlane/metadata/android/en-US/images/phoneScreenshots/Frame 5.png" alt="Routine Shortcuts" width="100%"></td>
    <td width="25%" align="center"><img src="https://raw.githubusercontent.com/Dhangofa/NetToggle/refs/heads/main/fastlane/metadata/android/en-US/images/phoneScreenshots/Frame 6.png" alt="Broadcast Automation" width="100%"></td>
    <td width="25%" align="center"><img src="https://raw.githubusercontent.com/Dhangofa/NetToggle/refs/heads/main/fastlane/metadata/android/en-US/images/phoneScreenshots/Frame 7.png" alt="Guides & Documentation" width="100%"></td>
    <td width="25%" align="center"><img src="https://raw.githubusercontent.com/Dhangofa/NetToggle/refs/heads/main/fastlane/metadata/android/en-US/images/phoneScreenshots/Frame 8.png" alt="Tile In Action" width="100%"></td>
  </tr>
</table>

---

## 📱 Supported Android Versions & ROMs

- **Minimum Version:** Android 7.0 (Nougat, API 24)
- **Target Version:** Android 17 (API 37)

---

## 🔐 Privacy & Security

NetToggle is built strictly as an offline utility:

- 🚫 **Zero Ads**
- 🚫 **Zero Analytics or Telemetry**
- 🚫 **Zero Trackers**
- 🚫 **No `android.permission.INTERNET` declared** (cannot connect to the web)
- 🚫 **Zero Background Data Transmission**
- 🚫 **Zero Personal Data Collection**

**Permission Transparency:**
* `READ_PHONE_STATE`: Strictly used to query active physical SIM slot configurations and detect carrier capability ceilings via Android's native `SubscriptionManager`. All operations are executed 100% locally on your device.


---

## 🛠️ Build From Source

NetToggle is built with Kotlin, Java, and the standard Android Gradle toolchain.

```bash
git clone https://github.com/Dhangofa/NetToggle.git
cd NetToggle
./gradlew assembleRelease
```

---

## 📚 Documentation & Guides

For step-by-step setup guides, routine integrations, and detailed technical troubleshooting, check out the official Wiki:

- [1. Execution Mode Configuration](https://github.com/Dhangofa/NetToggle/wiki/1.-Execution-Mode-Configuration)
- [2. Target SIM Setup & Quick Tile Cycle Guide](https://github.com/Dhangofa/NetToggle/wiki/2.-Target-SIM-Setup-&-Quick-Tile-Cycle-Guide)
- [3. Adding the Tile to Quick Settings](https://github.com/Dhangofa/NetToggle/wiki/3.-Adding-the-Tile-to-Quick-Settings)
- [4. App Shortcuts & Built-in OS Routines](https://github.com/Dhangofa/NetToggle/wiki/4.-App-Shortcuts-&-Built%E2%80%90in-OS-Routines)
- [5. Broadcast Automation (Tasker, MacroDroid, Automate)](https://github.com/Dhangofa/NetToggle/wiki/5.-Broadcast-Automation-(Tasker,-MacroDroid,-Automate))
- [Frequently Asked Questions (FAQ)](https://github.com/Dhangofa/NetToggle/wiki/Frequently-Asked-Questions-(FAQ))

---

## 💬 Community & Support

* **Bug Reports & Feature Requests:** [GitHub Issues](https://github.com/Dhangofa/NetToggle/issues)
* **Community Discussion:** [Telegram Group](https://t.me/dhangofas_projects_chat)

---

## 📜 License

NetToggle is open-source software licensed under the **GNU General Public License v3.0**.
See the [LICENSE](LICENSE) file for details.

---

## 🧾 Notices

NetToggle uses original vector assets and icons created or customized specifically for this project.

- The NetToggle launcher icon and QS network icons were created for this project.
- UI outlines and vector assets are customized for Material Design 3 guidelines.
- Third-party trademarks (such as Android, Samsung One UI, Magisk, Shizuku, GitHub, Telegram) belong to their respective copyright holders.

See [NOTICE.md](NOTICE.md) for further information.
