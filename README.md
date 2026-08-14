# NetToggle

<div align="center">
  <img src="https://github.com/Dhangofa/NetToggle/blob/main/fastlane/metadata/android/en-US/images/icon.png?raw=true" alt="NetToggle Icon" width="128">
  
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
</p>

---

## 📖 Overview

NetToggle is a lightweight Android Quick Settings tile app for switching cellular network modes directly from the QS panel. It is built for users whose Android ROM, OEM skin, or carrier configuration hides strict 5G/4G controls or makes network mode switching inconvenient.

NetToggle uses privileged shell access through **Root** or **Shizuku** to run Android telephony commands that normal Android apps cannot access. Network behavior can still depend on the device, ROM, modem, SIM setup, and carrier support.

## ⭐ What NetToggle Can Do

- Switch network modes directly from a Quick Settings tile
- Force **5G Only** where supported
- Force **4G Only / LTE Only** where supported
- Set **Preferred 5G** or **Preferred 4G**
- Use **Root** or **Shizuku** execution mode
- Target **Auto**, **SIM 1**, or **SIM 2**
- Handle single-SIM and dual-SIM devices
- Avoid background polling and unnecessary battery use
- Keep a clean single-screen setup UI with light/dark styling

## 🚧 Why It Was Made
Modern Android skins, often restrict or completely hide advanced network mode selections (such as locking the device strictly to 5G or 4G to prevent unwanted network drops). Furthermore, standard third-party Android apps are only providing pref 4G and pref 5G not locking to 4G only or 5G only. 

NetToggle bridges this gap by completely bypassing standard Android APIs. Instead of asking the system to change the network, it executes raw baseband telephony shell commands (`cmd phone set-allowed-network-types-for-users`) as a privileged user, direct network mode switching on supported devices/ROMs. 

## ⚙️ Working Modes & Requirements
To execute the privileged baseband commands, NetToggle requires an elevated execution environment. It features a built-in UI to select between two independent working modes:

### 1. Root Mode (`su`)
* **Requirements:** A rooted device (e.g., Magisk, Apatch or KernelSU).

### 2. Shizuku Mode (ADB)
* **Requirements:** The [Shizuku](https://shizuku.rikka.app/) application installed and running.


## 💳 Target SIM Selection

* **Auto**: Detects the active data subscription and maps it to the correct physical SIM slot.
* **SIM 1**: Applies network mode changes to physical SIM slot 1.
* **SIM 2**: Applies network mode changes to physical SIM slot 2.

This helps on dual-SIM devices where Android subscription IDs do not always match physical SIM slot numbers.

## 📸 Screenshots

<table>
  <tr>
    <td width="16.66%" align="center"><img src="https://github.com/Dhangofa/NetToggle/blob/main/fastlane/metadata/android/en-US/images/phoneScreenshots/1.png?raw=true" alt="1" width="100%"></td>
  <td width="16.66%" align="center"><img src="https://github.com/Dhangofa/NetToggle/blob/main/fastlane/metadata/android/en-US/images/phoneScreenshots/2.png?raw=true" alt="2" width="100%"></td>
    <td width="16.66%" align="center"><img src="https://github.com/Dhangofa/NetToggle/blob/main/fastlane/metadata/android/en-US/images/phoneScreenshots/3.png?raw=true" alt="3" width="100%"></td>
    <td width="16.66%" align="center"><img src="https://github.com/Dhangofa/NetToggle/blob/main/fastlane/metadata/android/en-US/images/phoneScreenshots/4.png?raw=true" alt="4" width="100%"></td>
    <td width="16.66%" align="center"><img src="https://github.com/Dhangofa/NetToggle/blob/main/fastlane/metadata/android/en-US/images/phoneScreenshots/5.png?raw=true" alt="5" width="100%"></td>
    <td width="16.66%" align="center"><img src="https://github.com/Dhangofa/NetToggle/blob/main/fastlane/metadata/android/en-US/images/phoneScreenshots/6.png?raw=true" alt="6" width="100%"></td>
  </tr>
</table>



## 📱 Supported Android Versions

- **Minimum:** Android 7.0, API 24
- **Target:** Android 16, API 36

Tested device/ROM examples include:

- Samsung One UI
- HyperOS / MIUI
- OriginOS / ColorOS / OxygenOS
- Android custom/AOSP-based ROMs

Other OEM skins and custom ROMs may also work. If NetToggle does not work on your device, please open an issue with device details and logs.

## 🔐 Privacy

NetToggle is designed to work offline.

- No ads
- No analytics
- No telemetry
- No trackers
- No internet permission
- No background network communication
- No personal data collection
- No personal data transmission

External links are opened only when the user manually taps the GitHub or Telegram icons in the app.

## 🛠️ Build From Source

NetToggle can be built using the standard Android Gradle toolchain.

```bash
git clone https://github.com/dhangofa/NetToggle.git
cd NetToggle
./gradlew assembleRelease
```

## 🚀 Planned Improvements

- Custom QS tile cycle selection
- Preferred 3G mode
- Preferred 2G mode
- Copyable command error details for debugging

## 💬 Contact

For project-related questions, issue follow-up, or contact verification:

- [Github issues](https://github.com/Dhangofa/NetToggle/issues)
- [Telegram](https://t.me/dhangofas_project/2)


For bugs or feature requests, please use GitHub Issues when possible so device details, logs, and discussions stay trackable.

## 📜 License
NetToggle is licensed under the **GNU General Public License v3.0**.
See the [LICENSE](https://github.com/Dhangofa/NetToggle/tree/main/LICENSE) file for the full license text.

## 🧾 Notices

NetToggle uses custom vector assets created or edited for this project.

- The NetToggle launcher icon was created for this project using AI-assisted generation and manual vector editing.
- The Quick Settings network icon was created for this project.
- The app UI uses custom outlined vector icons created or edited for this project.
- The GitHub and Telegram icons are used only as external link indicators.
- GitHub and Telegram trademarks remain the property of their respective owners.
- NetToggle does not use third-party icon packs as app identity assets.

See [NOTICE.md](NOTICE.md) for more details.
