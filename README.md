# 🔵 BluFi: Offline Bluetooth Chat and File Sharing App
_Connect. Chat. Share. No internet required._

This project is an advanced Android application that uses native Bluetooth APIs (both classic Bluetooth and BLE) to enable secure, peer-to-peer communication and file transfer between nearby devices without relying on Wi-Fi or mobile data.

Perfect for environments with limited or no connectivity, such as classrooms, travel, emergencies, or remote areas.

## ✨ Features
* **100% Offline P2P:** Communication works entirely via direct Bluetooth connection.
* **Secure Messaging:** Send instant messages between paired devices.
* **File Transfer:** Share photos, videos, and documents quickly using optimized Bluetooth data transfer.
* **Local Data Storage:** All chat history and files are stored securely on the user's device using Room Database.
* **Clean Architecture:** Built using modern Android development practices (e.g., MVVM/Clean Architecture).

## 🛠️ Tech Stack & Requirements

* **Language:** Kotlin
* **IDE:** Android Studio
* **Min SDK:** Android 6.0 (Marshmallow - API 23) or higher
* **Database:** Room Persistence Library (for local storage)
* **Connectivity:** Android Bluetooth Classic / BLE APIs
* **Development:** Gradle, MVVM Architecture

## 🚀 Getting Started

### Prerequisites

You must have Android Studio installed with the necessary SDKs.

### Installation & Build

1.  **Clone the Repository:**
    ```bash
    git clone [https://github.com/](https://github.com/)MonkeyDCharann/BluFi.git
    ```
2.  **Open in Android Studio:** Open the cloned `BluFi` folder as an existing Android Studio project.
3.  **Sync Gradle:** Allow Gradle to build and sync the project dependencies.
4.  **Run:** Select the `app` module and run the application on two separate physical devices or emulators with Bluetooth capability enabled.

## 🔑 Signing Information (Release Key)
The release `.aab` file for the Google Play Store is signed using a secure Keystore.

| Item | Status |
| :--- | :--- |
| **Keystore File** | `BluFi_Key.jks` (Stored securely outside the repo) |
| **Key Alias** | `blufi_upload_key` |
| **Fingerprint** | SHA-256: [Insert SHA-256 fingerprint here once you upload the key to Play Console] |
| **Build Variant** | `release` |

***

## 🛡️ Privacy Policy (Google Play Console URL)
**Note:** This section serves as the required public disclosure for the Google Play Store listing.

**[BluFi: Privacy Policy]**

**Effective Date:** October 6, 2025

**1. Data Collection and Storage**

* **No Personal Data Collection:** The **BluFi** app does not collect, transmit, store, or share any personal data from your device to the developer (me) or to any third parties.
* **Local Data Storage Only:** All messages, files, and chat history created within the app are stored **exclusively on your device** using the local **Room Database**. This data is not sent to any external server.
* **Ephemeral Data:** Data is only exchanged directly between peer devices via a secure Bluetooth connection. This communication is **end-to-end local**.

**2. Permissions Used**

The app requires permission to access nearby devices (Bluetooth) to scan for, connect to, and communicate with other devices for its core function (chat and file sharing).

**3. Third-Party Services**

* This application uses **no third-party** analytics, advertising, or payment SDKs that would collect or share user data.

**4. Contact Us**

If you have any questions about this Privacy Policy, please contact the developer at: `devicharandasari019@gmail.com`

***

## 📧 Contact
Project Link: `https://github.com/MonkeyDCharann/BluFi`
Developer: `Devicharan Dasari`
Email: `devicharandasari019@gmail.com`
