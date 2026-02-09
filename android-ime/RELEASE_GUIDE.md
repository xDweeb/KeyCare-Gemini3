# KeyCare IME - Release & Update Guide

## Version Management

### Current Version
- **versionCode**: 2
- **versionName**: 1.0.1
- **compileSdk**: 35
- **targetSdk**: 35

### Play Store Requirements (February 2026)
- Apps must target at least **API level 35** (Android 15)
- **versionCode** must be incremented for each release

## Update Issues - Common Causes & Fixes

### 1. App Not Updating on Device
**Cause:** versionCode not incremented or signing key changed.

**Fix:**
- Always increment `versionCode` in `app/build.gradle` before each release
- Use the SAME signing key (keystore) for all releases
- If keystore is lost, you must create a new app listing

### 2. Signing Configuration

The release AAB is signed using:
- **Keystore file**: `app/keycare-release.keystore`
- **Key alias**: `keycare`
- **Signing config**: Defined in `app/build.gradle`

⚠️ **IMPORTANT**: Never change or lose the keystore file. If you do:
- Existing users CANNOT update to new versions
- You must publish as a completely new app

### 3. Version Incrementing Rules

| Release Type | versionCode | versionName |
|--------------|-------------|-------------|
| Initial      | 1           | 1.0.0       |
| Bug fix      | 2           | 1.0.1       |
| Minor update | 3           | 1.1.0       |
| Major update | 10          | 2.0.0       |

## Build Commands

### Generate Release AAB
```bash
cd "0x01 Taibi El Yakouti/android-ime"
./gradlew bundleRelease
```

**Output location:**
```
app/build/outputs/bundle/release/app-release.aab
```

### Generate Debug APK (for testing)
```bash
./gradlew assembleDebug
```

**Output location:**
```
app/build/outputs/apk/debug/app-debug.apk
```

### Generate Universal APK from AAB (for local testing)
Requires `bundletool`:
```bash
java -jar bundletool.jar build-apks \
  --bundle=app/build/outputs/bundle/release/app-release.aab \
  --output=keycare.apks \
  --mode=universal \
  --ks=app/keycare-release.keystore \
  --ks-key-alias=keycare \
  --ks-pass=pass:keycare2026 \
  --key-pass=pass:keycare2026

unzip keycare.apks -d extracted
# Install extracted/universal.apk
```

## Checklist Before Release

- [ ] Increment `versionCode` in `app/build.gradle`
- [ ] Update `versionName` appropriately
- [ ] Test on multiple devices
- [ ] Verify signing with release keystore
- [ ] Run `./gradlew bundleRelease`
- [ ] Upload AAB to Play Console
- [ ] Test via internal testing track first
