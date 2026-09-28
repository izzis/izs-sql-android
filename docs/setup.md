# Setup — izs SQL Android

> Back to [README](../README.md) · Architecture: [architecture.md](architecture.md)

## Requirements

| Tooling | Version |
|---------|---------|
| Android Studio | Quail or newer (AGP 9.4.1, Kotlin 2.3.21) |
| JDK | 17+ ( `compileSdk 37`, `minSdk 26`, `targetSdk 37`, `jvmTarget 17` ) |
| Android SDK | `compileSdk 37` (platform `android-37.2`), NDK not required |
| Gradle | Wrapper `gradlew` checked in (`gradle-9.8.0`), no local install needed |

Runtime dependencies (see `app/build.gradle.kts`):

- `androidx.compose:compose-bom:2026.09.00`, `material3`, extended icons, `navigation-compose:2.10.1`, `activity-compose:1.13.0`
- `hilt-android:2.60.1` + `ksp 2.3.12`, `androidx.hilt:hilt-*:1.4.0`, `room:2.8.5` + `ksp`
- `security-crypto` removed — live creds use Android Keystore AES-GCM directly, no Jetpack dep
- `mariadb-java-client:2.4.4` (latest version compatible with Android's `java.sql`/regex)
- `jsch:2.28.7` (maintained mwiede fork), `kotlinx-coroutines-android:1.11.0`
- `core-ktx:1.19.0`, `lifecycle-*:2.11.0`, test `junit:1.3.0` / `espresso:3.7.0`

## Quick start — from zero to APK (Option C: build from source)

This is the **developer build path** (no Play Store / no prebuilt APK yet). Do it once — every next build is just `./gradlew assembleDebug`.

### 1. Download the source

**Via git (recommended):**
```bash
git clone git@github.com:izzis/izs-sql-android.git
# or HTTPS if you have no SSH key:
# git clone https://github.com/izzis/izs-sql-android.git
cd izs-sql-android
```

**Via ZIP (no git needed):**
GitHub → **Code ▼ → Download ZIP** → extract → `cd izs-sql-android` (same folder as `gradlew`). Functionally identical to `git clone`.

### 2. Install prerequisites

#### Ubuntu / Debian via `apt` (includes everything you need except the Android SDK)

```bash
sudo apt update
sudo apt install -y openjdk-17-jdk git wget unzip adb
# verify
java -version      # must be 17.x
adb --version
git --version
```

Other distros:
- Fedora/RHEL: `sudo dnf install java-17-openjdk git wget unzip android-tools`
- Arch: `sudo pacman -S jdk17-openjdk git wget unzip android-tools`
- macOS: `brew install openjdk@17 android-platform-tools git wget unzip`
- Windows: install **Android Studio Hedgehog+** (bundles JDK 17 + SDK + adb) and use PowerShell/`gradlew.bat` — no `apt` needed.

> No separate Gradle install needed — the repo ships the **Gradle Wrapper** (`gradlew`/`gradlew.bat`). Do not `apt install gradle`.

#### Android SDK — pick one of the two:

**A) You already have Android Studio Hedgehog or newer**
Nothing extra to install. Just tell Gradle where the SDK is (next section). Default locations: Linux `~/Android/Sdk`, macOS `~/Library/Android/sdk`, Windows `%LOCALAPPDATA%\Android\Sdk`.

**B) Headless / without Android Studio (command-line tools only)**

```bash
# 1. Create SDK root
mkdir -p ~/Android/Sdk/cmdline-tools
cd /tmp

# 2. Download command-line tools (Linux)
wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip

# 3. Unpack as "latest"
unzip -q commandlinetools-linux-11076708_latest.zip
mkdir -p ~/Android/Sdk/cmdline-tools/latest
mv cmdline-tools/* ~/Android/Sdk/cmdline-tools/latest/

# 4. Add to PATH for this shell (and add to ~/.bashrc / ~/.zshrc for persistence)
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_SDK_ROOT="$HOME/Android/Sdk"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

# 5. Accept licenses + install required SDK components (Android 37 matches compileSdk 37)
yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-37.2" "build-tools;36.0.0"

# 6. Verify
sdkmanager --list | grep -E "platforms;android-37|build-tools;36"
adb --version
```

`build-tools` `36.0.0` and `platforms;android-37.2` are the minimum required by `app/build.gradle.kts` (`compileSdk 37`, `targetSdk 36`). Newer patch versions also work.

### 3. Point Gradle at the SDK

`local.properties` is **gitignored** — never commit it.

```bash
cd /path/to/izs-sql-android

# Option 1: create local.properties (most reliable)
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
cat local.properties
# expected: sdk.dir=~/Android/Sdk  (adjust to your actual path)

# Option 2: env var fallback (works if local.properties is absent)
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_SDK_ROOT="$HOME/Android/Sdk"
```

If Gradle still says `SDK location not found`, check `echo $ANDROID_HOME` and `ls ~/Android/Sdk/platforms`.

### 4. Build the APK

```bash
# Make wrapper executable (first time only, Linux/macOS)
chmod +x gradlew

# Debug APK (fast, unminified, debuggable)
./gradlew assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk

# Install to a connected device/emulator (USB debugging ON, or an AVD running)
adb devices                          # must list your device
adb install -r app/build/outputs/apk/debug/app-debug.apk
# or in one step:
./gradlew installDebug

# Without adb — manual install:
# Copy app/build/outputs/apk/debug/app-debug.apk to the phone
# and tap it in File Manager → Allow "Install unknown apps".

# Release APK (minified via ProGuard/R8, needs signing for distribution)
./gradlew assembleRelease
# output: app/build/outputs/apk/release/app-release.apk  (unsigned by default)
# To distribute, sign with your keystore — see Android docs "Sign your app".
```

Open the app, tap **+ New Connection**, fill host/port/username/password, toggle **Read only** if you want the session locked by default, optionally configure **SSH Tunnel** / **SSL**, then **Test** and **Save**.

### 5. Next builds

After the first setup you only need:

```bash
git pull            # or re-download ZIP if you used ZIP
./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Configuration

- `local.properties` — `sdk.dir` only; never commit secrets.
- `gradle.properties` — `org.gradle.jvmargs=-Xmx2048m`, `android.useAndroidX`, `kotlin.code.style=official`.
- `settings.gradle.kts` — single module `:app`, `repositories { google(), mavenCentral(), gradlePluginPortal() }`.
- `app/build.gradle.kts` — single source-of-truth for versions/SDK (see [Requirements](#requirements)).

No `.env`: DB/SSH passwords are stored per profile via `CredentialStore` (Android Keystore AES-GCM) and never written to `Room`.
