# Getting started: build and run tvOS Launcher

From a fresh machine to tvOS Launcher running on a TV (or the Android TV emulator) in about 20 minutes. No
Android Studio or Gradle needed: the project builds with the plain Android SDK command-line tools.

## 1. What you need

| | Version | Why |
|---|---|---|
| JDK | 17 or newer | `javac` compiles the Java sources (`--release 11`) |
| Android SDK build-tools | **34.0.0** | `aapt2`, `d8`, `zipalign`, `apksigner` |
| Android SDK platform | **android-34** | `android.jar` to compile against |
| Android SDK platform-tools | latest | `adb` to install and debug |
| A device | Android TV 7.0+ (API 24+) | A TV box/TV, or the Android TV emulator |

Tested devices: Xiaomi Mi Box 4 (Android 12, 32-bit ARM, Mali-450 GPU) and an SPPL Android 9 TV
(1 GB RAM). Low-end hardware is the target, so test on it when you can.

## 2. Install the tools

### macOS

```sh
brew install --cask temurin@17                      # JDK 17 (or any JDK 17+)
brew install --cask android-commandlinetools        # sdkmanager
export ANDROID_HOME="$HOME/Library/Android/sdk"
yes | sdkmanager --sdk_root="$ANDROID_HOME" --licenses
sdkmanager --sdk_root="$ANDROID_HOME" "platform-tools" "build-tools;34.0.0" "platforms;android-34"
```

### Linux (Debian/Ubuntu)

```sh
sudo apt install openjdk-17-jdk unzip
mkdir -p ~/Android/Sdk/cmdline-tools && cd ~/Android/Sdk/cmdline-tools
curl -O https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip commandlinetools-linux-*.zip && mv cmdline-tools latest
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
yes | sdkmanager --licenses
sdkmanager "platform-tools" "build-tools;34.0.0" "platforms;android-34"
```

### Windows

Use **WSL 2** (Ubuntu) and follow the Linux steps; `build.sh` is a bash script. To install on a TV
over the network from WSL, the TV and PC just need to be on the same LAN.

Put `ANDROID_HOME` (and `JAVA_HOME` if `javac` isn't on your `PATH`) in your shell profile.
`build.sh` reads them, and falls back to `~/Library/Android/sdk` (macOS) or `~/Android/Sdk` (Linux).

## 3. Get the code and build

```sh
git clone http://10.0.0.220/marketing/tvOS-Launcher.git    # or your fork
cd tvOS-Launcher
./build.sh                                                 # → build/tvos-home.apk
VERSION_CODE=88 VERSION_NAME=7.5 ./build.sh                 # release builds set a version
```

What `build.sh` does, in order: `aapt2` compiles resources and assets → `javac` compiles `src/` →
`d8` converts to DEX → `zipalign` → `apksigner` signs with a local debug key (`debug.keystore`,
created on first build, never committed). A full build takes
about 15 seconds. If a tool is missing it says which one and how to get it.

## 4. Run it on a TV

1. On the TV: **Settings → Device Preferences → About → Build**, click 7 times to unlock
   Developer options; then **Developer options → USB debugging / Network debugging** on.
2. From your computer (same network):

```sh
adb connect <tv-ip>:5555          # accept the "Allow debugging?" prompt on the TV
adb install -r build/tvos-home.apk
adb shell am start -n com.techgenius.tvoslauncher/.MainActivity
```

3. First launch runs the setup pages. To make tvOS Launcher the home screen, finish setup (it offers this),
   or on Google TV devices where the stock launcher always wins:
   `adb shell pm disable-user --user 0 com.google.android.tvlauncher` (undo with `pm enable`).

More on installing and permissions: [install.md](install.md).

## 5. Or use the Android TV emulator

```sh
sdkmanager --list | grep "android-tv"          # pick an Android TV image for your CPU (x86_64 or arm64-v8a)
sdkmanager "emulator" "system-images;android-<api>;android-tv;<abi>"
avdmanager create avd -n tv -k "system-images;android-<api>;android-tv;<abi>" -d tv_1080p
emulator -avd tv &
adb install -r build/tvos-home.apk
```

The emulator is fine for layout and logic. It does **not** show real performance (its GPU is far
faster than a TV box's), and it has no HDMI inputs, so check those on hardware.

## 6. Your first change

1. Pick a small thing, e.g. a label in `SettingsActivity.java`.
2. Build and install: `./build.sh && adb install -r build/tvos-home.apk`.
3. Jump straight to the screen you changed with the developer shortcuts below, and take a
   screenshot: `adb exec-out screencap -p > shot.png`.
4. Read **[CONTRIBUTING.md](../CONTRIBUTING.md)** before opening a pull request.

### Developer shortcuts (adb)

Open any screen without clicking through the UI:

| Command | Opens |
|---|---|
| `adb shell am start -n com.techgenius.tvoslauncher/.MainActivity --es panel cc` | Control Center (`inputs`, `weather`, `present` also work) |
| `adb shell am start -n com.techgenius.tvoslauncher/.MainActivity --es panel search --es q panchayat` | Search, with a query typed in |
| `adb shell am start -n com.techgenius.tvoslauncher/.MainActivity --ez pick_profile true` | Profile switcher |
| `adb shell am start -n com.techgenius.tvoslauncher/.SettingsActivity --es page "Top Shelf"` | A Settings page by its title |
| `adb shell am start -n com.techgenius.tvoslauncher/.SetupActivity --ei step 3` | Setup page 0–7 |
| `adb shell am start -n com.techgenius.tvoslauncher/.SetupActivity --ez skip true` | Marks setup as done |
| `adb shell am start -n com.techgenius.tvoslauncher/.SettingsActivity --es tmdb_token <key>` | Sets a TMDB key |
| `adb shell am start -n com.techgenius.tvoslauncher/.MainActivity --es perf weather` | Hides one layer to measure its GPU cost (`status`, `hero`, `dockglass`, `shadows`; `reset` restores) |

Useful logs: `adb logcat -s TvHome` (tvOS Launcher's own messages) and
`adb shell dumpsys gfxinfo com.techgenius.tvoslauncher` (frame times; see CONTRIBUTING.md).

## Troubleshooting

| Problem | Fix |
|---|---|
| `Missing …/aapt2` | Install `build-tools;34.0.0` and `platforms;android-34`, or set `ANDROID_HOME` |
| `javac not found` | Install JDK 17+ or set `JAVA_HOME` |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | The installed copy was signed with a different key (e.g. a release build): `adb uninstall com.techgenius.tvoslauncher` first (this clears its settings) |
| `adb: device offline` / `unauthorized` | Accept the prompt on the TV; `adb disconnect` then `adb connect` again |
| Home button still opens the old launcher | Finish setup's *Make tvOS Launcher the Home Screen*, or see install.md |
| Install over Wi-Fi is very slow | Normal for 1 MB+ over a busy network; it finishes — don't wrap it in fixed sleeps |
