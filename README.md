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

NetToggle is a lightweight Android Quick Settings tile app for switching cellular network modes directly from the QS panel. It is built for users whose Android ROM, OEM skin, or carrier configuration hides strict 5G/4G controls or makes network mode switching inconvenient.

NetToggle uses privileged shell access through **Root** or **Shizuku** to run Android telephony commands that normal Android apps cannot access. For older devices (Android 7-11), it utilizes a seamless headless Java payload, while Android 12+ relies on native routing. Network behavior can still depend on the device, ROM, modem, SIM setup, and carrier support.

## ⭐ What NetToggle Can Do

- Switch network modes directly from a Quick Settings tile
- Force **5G Only** or **4G Only / LTE Only** where supported
- Set **Preferred 5G**, **Preferred 4G**, **Preferred 3G**, or **2G Only**
- **Custom QS Tile Cycle**: Select 2 or 3 modes to cycle through in your preferred order
- Use **Root** or **Shizuku** execution mode
- Target **Auto**, **SIM 1**, or **SIM 2**
- **Smart Capability Filtering**: Dynamically hides unsupported modes based on your device hardware capability and a built-in global 2G/3G sunset carrier registry
- **Diagnostic Tracking**: Built-in error reporting and shell output logs (stdout/stderr) for easy troubleshooting
- A clean, modern UI featuring interactive morphing carousels, segmented controls, and light/dark styling

## 🚧 Why It Was Made
Modern Android skins often restrict or completely hide advanced network mode selections (such as locking the device strictly to 5G or 4G to prevent unwanted network drops). Furthermore, standard third-party Android apps are only providing pref 4G and pref 5G, not locking to 4G only or 5G only. 

NetToggle bridges this gap by completely bypassing standard Android APIs. Instead of asking the system to change the network, it executes raw baseband telephony commands (via native shell or secure service reflection) as a privileged user, enabling direct network mode switching on supported devices/ROMs. 

## ⚙️ Working Modes & Requirements
To execute the privileged baseband commands, NetToggle requires an elevated execution environment. It features a built-in interactive setup carousel to select between two independent working modes:

### 1. Root Mode (`su`)
* **Requirements:** A rooted device (e.g., Magisk, Apatch, or KernelSU).

### 2. Shizuku Mode (`ADB`)
* **Requirements:** The [Shizuku](https://shizuku.rikka.app/) or its working forks application installed and running.

## 💳 Target SIM Selection

* **Auto**: Detects the active data subscription and maps it to the correct physical SIM slot using native APIs.
* **SIM 1**: Applies network mode changes to physical SIM slot 1.
* **SIM 2**: Applies network mode changes to physical SIM slot 2.

This helps on dual-SIM devices where Android subscription IDs do not always match physical SIM slot numbers.

## 🔄 Quick Tile Cycle Configuration

NetToggle allows you to build a personalized sequence of network modes that the Quick Settings tile will cycle through when tapped. 

* **Selectable Modes:** You can choose from **5G Only**, **4G Only**, **Preferred 5G**, **Preferred 4G**, **Preferred 3G**, and **2G Only**.
* **Smart Filtering:** Modes that your device hardware or specific carrier does not support (based on our 2G/3G sunset registry) will be dynamically disabled to prevent misconfiguration.
* **Cycle Rules:** You must select a minimum of 2 modes and a maximum of 3 modes. The order in which you select them dictates the exact order the Quick Settings tile will follow.

## 📸 Screenshots

<table>
  <tr>
    <td width="25%" align="center"><img src="https://raw.githubusercontent.com/Dhangofa/NetToggle/refs/heads/main/fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" alt="1" width="100%"></td>
    <td width="25%" align="center"><img src="https://raw.githubusercontent.com/Dhangofa/NetToggle/refs/heads/main/fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" alt="2" width="100%"></td>
    <td width="25%" align="center"><img src="https://raw.githubusercontent.com/Dhangofa/NetToggle/refs/heads/main/fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" alt="3" width="100%"></td>
    <td width="25%" align="center"><img src="https://raw.githubusercontent.com/Dhangofa/NetToggle/refs/heads/main/fastlane/metadata/android/en-US/images/phoneScreenshots/4.png" alt="4" width="100%"></td>
    </tr>
  <tr>
    <td width="25%" align="center"><img src="https://raw.githubusercontent.com/Dhangofa/NetToggle/refs/heads/main/fastlane/metadata/android/en-US/images/phoneScreenshots/5.png" alt="5" width="100%"></td>
    <td width="25%" align="center"><img src="https://raw.githubusercontent.com/Dhangofa/NetToggle/refs/heads/main/fastlane/metadata/android/en-US/images/phoneScreenshots/6.png" alt="6" width="100%"></td>
    <td width="25%" align="center"><img src="https://raw.githubusercontent.com/Dhangofa/NetToggle/refs/heads/main/fastlane/metadata/android/en-US/images/phoneScreenshots/7.png" alt="7" width="100%"></td>
    <td width="25%" align="center"><img src="https://raw.githubusercontent.com/Dhangofa/NetToggle/refs/heads/main/fastlane/metadata/android/en-US/images/phoneScreenshots/8.png" alt="8" width="100%"></td>
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

Other OEM skins and custom ROMs may also work. If NetToggle does not work on your device, you can use the built-in diagnostic dialog to copy detailed execution logs and open an issue.

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

**Note on Permissions:** The `READ_PHONE_STATE` permission is strictly required to resolve active physical SIM slots via Android's native `SubscriptionManager` API and determine safe network capability ceilings. All processing remains 100% local.

External links are opened only when the user manually taps the GitHub or Telegram icons in the app.

## 🛠️ Build From Source

NetToggle can be built using the standard Android Gradle toolchain.

```bash
git clone [https://github.com/dhangofa/NetToggle.git](https://github.com/dhangofa/NetToggle.git)
cd NetToggle
./gradlew assembleRelease
```
## 📚 Documentation & Guides

For detailed setup instructions, troubleshooting, and answers to common questions, please refer to our official Wiki pages:

- [1. Execution Mode Configuration](https://github.com/Dhangofa/NetToggle/wiki/1.-Execution-Mode-Configuration)
- [2. Target SIM Setup & Quick Tile Cycle Guide](https://github.com/Dhangofa/NetToggle/wiki/2.-Target-SIM-Setup-&-Quick-Tile-Cycle-Guide)
- [3. Adding the Tile to Quick Settings](https://github.com/Dhangofa/NetToggle/wiki/3.-Adding-the-Tile-to-Quick-Settings)
- [Frequently Asked Questions (FAQ)](https://github.com/Dhangofa/NetToggle/wiki/Frequently-Asked-Questions-(FAQ))

## 💬 Contact

For project-related questions, issue follow-up, or contact verification:

- [Github issues](https://github.com/Dhangofa/NetToggle/issues)
- [Telegram](https://t.me/dhangofas_projects_chat)


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
- GitHub trademarks remain the property of their respective owners.
- NetToggle does not use third-party icon packs as app identity assets.

See [NOTICE.md](NOTICE.md) for more details.
