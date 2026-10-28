# Water Island

A water-reminder app for Android phones and Galaxy Watches.

- **Every hour** (8:00 to 22:00) it reminds you to drink water.
- On Android 16 phones with an "island" (for example Vivo's Atomic Island) the reminder shows up in the **island** around the camera, with a **+1 glass** button.
- On any other phone it shows a **normal notification** with the same button. You can also pick this yourself in Settings.
- A **Galaxy Watch app** shows your progress, lets you add water, buzzes your wrist for each reminder, and keeps your count in sync with the phone.
- It keeps a weekly graph, a streak, a list of today's drinks, and can **export** your data as Excel, CSV or JSON.

Your data stays on your devices. There are no accounts and no servers of our own.

> **Tested on:** a Vivo X300 Pro (Android 16) and a Galaxy Watch 7. The Galaxy Watch 8 should work the same way but has **not** been tested. Older Android phones (8 to 15) get the normal notification; that path was checked on the Vivo phone with the style switched to "Normal notification", not on an older phone.

---

## What you need

| Thing | Details |
| --- | --- |
| Android phone | Android 8 or newer. The island needs Android 16 and a phone whose software draws it. |
| Computer | Windows, macOS or Linux, with about 5 GB free disk space. |
| USB cable | To connect the phone to the computer once. |
| Galaxy Watch (optional) | Galaxy Watch 7 or 8, paired to the phone with the **Galaxy Wearable** app. |
| Wi-Fi | The watch and the computer must be on the same Wi-Fi to install the watch app. |

You do **not** need to know how to program.

## How the project is organised

There are two apps in this folder. They are built together and **must share the same package name and signing key**, or the phone and the watch cannot talk to each other.

| Folder | What it is |
| --- | --- |
| `app/` | The phone app. |
| `wear/` | The watch app (Wear OS). |

Both use the package name `com.kunal.waterisland`.

---

## Quick install: use the ready-made files (no building)

If someone gave you this project with an **`apk`** folder, you can skip Android Studio and install the two files directly.

| File | Goes on | Needs |
| --- | --- | --- |
| `WaterIsland-phone-1.2.apk` | Your Android phone | Android 8 or newer |
| `WaterIsland-watch-1.2.apk` | Your Galaxy Watch | A Wear OS watch (the author tested a Galaxy Watch 7) |
| `SHA256SUMS.txt` | Nothing; it is a list of checksums to check the files are not damaged | |

> **Install both from the same folder.** The two apps were built together and signed with the same key. That is what lets the phone and watch talk to each other. If you mix files from different builds, or rebuild only one of them yourself, the sync will stop working.

### Install on the phone

1. Copy `WaterIsland-phone-1.2.apk` to the phone (USB cable, Google Drive, WhatsApp to yourself, or Nearby Share).
2. Open the file from the **Files** app (or from the chat or browser you received it in).
3. Android asks to allow **Install unknown apps** for the app you opened it from. Turn it on, go back, and tap **Install**.
4. If **Play Protect** says it does not recognise the developer, tap **More details > Install anyway**. This warning appears for any app that is not from the Play Store.
5. Open **Water Island** and follow **Step 4: First-time setup on the phone** below.

### Install on the watch

A watch cannot open an APK by itself, so this part needs a computer. You do **not** need Android Studio, only the small `adb` tool.

1. On the computer, get **[Android platform-tools](https://developer.android.com/tools/releases/platform-tools)** (a zip you just unpack).
2. Follow **Step 5b and 5c** below to turn on developer mode on the watch and connect the computer to it (`adb pair`, then `adb connect`).
3. In a terminal, in the folder with the APK files, run (use your watch's address from `adb devices`):

```bash
adb -s 192.168.1.25:40567 install -r WaterIsland-watch-1.2.apk
```

4. Open **Water Island** on the watch, tap **Allow** for notifications, then continue from **Step 5e**.

### Check the files are not damaged (optional)

```bash
# Windows (PowerShell)
Get-FileHash WaterIsland-phone-1.2.apk -Algorithm SHA256

# macOS or Linux
shasum -a 256 WaterIsland-phone-1.2.apk
```

The long code it prints must match the line for that file in `SHA256SUMS.txt`.

---

## Step 1: Install the tools

1. Install **[Android Studio](https://developer.android.com/studio)**. It includes everything else you need (Java and the Android tools).
2. Open Android Studio once and let it finish its first-time setup.
3. In Android Studio go to **Settings > Languages & Frameworks > Android SDK** (on macOS: **Android Studio > Settings**). On the **SDK Platforms** tab tick **Android 16 (API 36)**. On the **SDK Tools** tab make sure **Android SDK Platform-Tools** is ticked. Click **Apply**.

## Step 2: Get the code and open it

If someone shared it with you on GitHub:

```bash
git clone <the-repository-address>
```

Then in Android Studio choose **Open** and pick the `water_island` folder. Wait until the progress bar at the bottom finishes (the first time it downloads libraries, which can take a few minutes).

## Step 3: Install the app on your phone

1. On the phone, open **Settings > About phone** and tap **Build number** 7 times. This turns on Developer options.
2. Open **Settings > Developer options** and turn on **USB debugging**.
3. Plug the phone into the computer. When the phone asks **Allow USB debugging?**, tap **Allow**.
4. In Android Studio, pick your phone in the device list at the top, choose **app** next to it, and press the green **Run** button.

Or from a terminal inside the project folder:

```bash
# Windows (PowerShell)
.\gradlew.bat :app:installDebug

# macOS or Linux
./gradlew :app:installDebug
```

If more than one device is connected (for example the phone **and** a watch), use Android Studio's device list instead, or install with `adb -s <device-id> install -r app/build/outputs/apk/debug/app-debug.apk`.

## Step 4: First-time setup on the phone

Open **Water Island**, then:

1. Allow **notifications** when asked (Android 13 and newer).
2. Tap the **gear** at the top right to open **Settings**.
3. Tap **Reminders** so it says **On**. Reminders are off until you do this.
4. Under **Permissions**, tap every row that does not show a tick:
   - **Live updates (system island)**: only appears on Android 16.
   - **Notifications**
   - **Exact alarms**: lets the reminder arrive on the hour.
   - **Ignore battery optimisation**: stops the phone from putting the app to sleep.
5. Many phone makers (Vivo, Xiaomi, Oppo, Samsung and others) also kill background apps. If reminders stop, allow **Water Island** to **auto-start** and to run in the **background** in your phone's battery settings.

Tap **Test reminder now** (bottom of Settings) to see what a reminder looks like.

### Island or normal notification?

In **Settings > Reminder style** you can switch between:

- **Island**: the Android 16 "Live Update" shown in the island and expanded card.
- **Normal notification**: a regular notification with a progress bar and a **+1 glass** button.

Phones older than Android 16 always use the normal notification.

---

## Step 5 (optional): Put the app on your Galaxy Watch

The watch app is installed from your computer over Wi-Fi. This is a one-time setup.

### 5a. Pair the watch with the phone

Use the **Galaxy Wearable** app on the phone, and keep the phone's **Bluetooth on**. The watch must say it is connected.

### 5b. Turn on developer mode on the watch

1. On the watch open **Settings > About watch > Software**.
2. Tap **Software version** 5 times. It says developer mode is on.
3. Go back to **Settings > Developer options** and turn on **ADB debugging** and **Debug over Wi-Fi**.
4. Make sure the watch is on the **same Wi-Fi as your computer** (**Settings > Connections > Wi-Fi**). Set a longer screen timeout while you do this, because the connection drops when the watch screen sleeps.

### 5c. Connect the computer to the watch

1. On the watch open **Developer options > Debug over Wi-Fi > Pair new device**. It shows an **IP address with a port** and a **six-digit code**.
2. On the computer, in a terminal, run (use your own numbers; the code expires in about a minute):

```bash
adb pair 192.168.1.25:37123 123456
```

3. Back on the watch's **Debug over Wi-Fi** screen there is a **different** IP and port. Connect to that one:

```bash
adb connect 192.168.1.25:40567
adb devices
```

You should see your watch in the list (a model like `SM_L3xx`).

> If `adb` is not found, it lives in the Android SDK's `platform-tools` folder. On Windows that is usually `C:\Users\<you>\AppData\Local\Android\Sdk\platform-tools`.

### 5d. Build and install the watch app

```bash
# Windows (PowerShell)
.\gradlew.bat :wear:assembleDebug

# macOS or Linux
./gradlew :wear:assembleDebug
```

Now install it on **the watch only**, using the watch's address from `adb devices`:

```bash
adb -s 192.168.1.25:40567 install -r wear/build/outputs/apk/debug/wear-debug.apk
```

> **Important:** do **not** run `installDebug` for the watch while your phone is plugged in. The watch app and the phone app share a package name, so Gradle would try to put the watch app on your phone and replace the real app. Always use `adb -s <watch-address> install` as above.

### 5e. First start on the watch

1. Open **Water Island** on the watch (it is in the app list; the icon is a white drop outline).
2. Tap **Allow** when it asks about notifications.
3. Swipe sideways between four pages: **Today**, **This week**, **Change today**, **Style**.

### 5f. Optional: a tile and a watch-face complication

- **Tile (quick add):** swipe to your tiles, scroll to the end, choose **Add tile**, then **Water**.
- **Complication (on your watch face):** touch and hold the watch face, tap **Edit**, pick a slot, and choose **Water Island**.

### Watch 7 and Watch 8

The watch app targets Wear OS 3 and newer, so it is built to work on both. Only the Galaxy Watch 7 has been tested. If you try a Watch 8, the steps above are the same.

---

## Using the app

**Phone**
Everything is counted in **glasses**. **3 glasses = 1 litre**, so one glass is 1/3 litre. There are no millilitre amounts.

- Tap **+ 1 glass** after you drink, or **âˆ’ 1 glass** to fix a mistake.
- The big number is today's glasses, with the litres and your goal underneath.
- The cards show your week as bars and a trend line, with your average and best day.
- **Settings** has the reminder interval, sound, **Watch style**, daily goal (in glasses), **Remind on watch**, **Reminder style**, **Export data** and **Reset today**.
- **Export data** (Excel, JSON or CSV) lists glasses and litres per day, today's log, and your settings.

**Watch**
- **Today:** rising water that fills toward your goal. Tap the water to add 1 glass.
- **This week:** the bars for the week.
- **Change today:** **+ 1 glass** and **âˆ’ 1 glass** buttons. When a reminder arrives the watch buzzes and shows a card with **+1 glass**.
- **Tile:** **âˆ’1** and **+1** buttons.

## How the phone and watch stay in sync

The phone is the master copy. It sends the watch your count and week. When you add water on the watch, the watch sends that drink to the phone, which saves it and sends back the new numbers. If the phone is out of reach, the watch shows the drink straight away and sends it when they reconnect. This uses Google's Wear OS Data Layer, normally over Bluetooth.

## Troubleshooting

| Problem | Try this |
| --- | --- |
| No reminders at all | Settings > **Reminders** must say **On**. Allow exact alarms and background running (Step 4). Open the app once after any update. |
| Reminders only after opening the app | Your phone is stopping the app. Allow **auto-start** and set battery to **Unrestricted** for Water Island. |
| The island does not show | It needs Android 16 and a phone that draws Live Updates. Switch **Reminder style** to **Normal notification**. |
| Watch shows `0` and does not update | Turn the phone's **Bluetooth on** and open **Galaxy Wearable** to check the watch says connected. Then open Water Island on the phone once. |
| Watch never buzzes | The watch only buzzes while it is **on your wrist** and connected. Check **Remind on watch** is **On** in the phone's Settings. |
| `adb devices` says `offline` or `unauthorized` | Wake the watch screen and reconnect with `adb connect`. For the phone, replug the cable and tap **Allow**. |
| `adb pair` fails | The code expired. Tap **Pair new device** again and use the new code straight away. |
| Phone and watch will not sync after you rebuild | Both apps must be built on the **same computer** (same debug key). Reinstall both. |

## Sharing the app with other people

### Making the files

Build both apps, then copy the two APKs into the `apk` folder:

```bash
# Windows (PowerShell)
.\gradlew.bat clean :app:assembleRelease :wear:assembleRelease

# macOS or Linux
./gradlew clean :app:assembleRelease :wear:assembleRelease
```

The files appear at:

- `app/build/outputs/apk/release/app-release.apk` (phone)
- `wear/build/outputs/apk/release/wear-release.apk` (watch)

Rename them to `WaterIsland-phone-<version>.apk` and `WaterIsland-watch-<version>.apk`, and make a `SHA256SUMS.txt` with a line per file in the form `<checksum>  <file name>` (the commands in the checksum section above print the checksum).

### What to send

Send **both** APKs, `SHA256SUMS.txt`, and this README. Good ways to share:

- **GitHub Release (recommended):** on your repository, create a **Release** and attach the files. The `.gitignore` keeps APKs out of the code itself, so do not commit them.
- **Zip file** through Google Drive, a messaging app or email.
- **USB cable** or Nearby Share, for people nearby.

### Updating on someone's device

Install the newer APK over the old one and their data is kept. This only works if both APKs were signed with the **same key** as the version they already have. If it says the app is not compatible, uninstall the old one first. That erases its data, so use **Settings > Export data** before you do.

### Things to know

- **These builds are not debuggable**, so other apps and USB tools cannot read the app's data. They are still signed with the debug key from the computer that built them, which is fine for friends and family but not for the public (see the release steps below).
- **Sync needs the same key on both apps.** Never share the phone file from one build and the watch file from another, and tell people not to rebuild only one app.
- **Play Protect** may warn about an unknown developer. It is a normal warning for apps outside the Play Store.
- **The island** only appears on Android 16 phones that draw it. Everyone else gets the normal notification (**Settings > Reminder style**).
- **Watch support:** the author tested a Galaxy Watch 7 and a Vivo phone only. Other watches and phones may work but are untested.

### For a proper public release

1. Make a **release signing key** with Android Studio (**Build > Generate Signed App Bundle / APK > Create new**).
2. Build **both** apps (`app` and `wear`) with the **same** key. Without that the watch will not sync.
3. Keep the key file and its passwords safe and **never** put them in git. If you lose the key you cannot update the app for people who installed it.
4. To publish on Google Play you need a Play Console account, and the phone and watch apps are uploaded under the same package name (`com.kunal.waterisland`) so they install together.

---

## For the project owner: putting this on GitHub

```bash
git init
git add .
git commit -m "Water Island phone and Galaxy Watch apps"
git branch -M main
git remote add origin <your-repository-address>
git push -u origin main
```

The included `.gitignore` already keeps build folders, `local.properties` (your own SDK path) and signing keys out of the repository. Gradle's wrapper files (`gradlew`, `gradlew.bat` and the `gradle/` folder) **are** committed on purpose, so anyone can build with one command.

Before you share it, add a **LICENSE** file so people know how they may use the code.

## Project layout

```
water_island/
  app/     phone app (reminders, island, graphs, export)
  wear/    watch app (pages, tile, complication, reminder card)
  apk/     ready-made install files and checksums (not kept in git)
  gradle/  Gradle wrapper
  README.md
```
