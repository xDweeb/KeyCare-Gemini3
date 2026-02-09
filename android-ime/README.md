# KeyCare IME - Android Keyboard

A real Android keyboard (Input Method Editor) that integrates with the KeyCare AI safety API to analyze text as you type.

## Features

- **Real Keyboard**: Full QWERTY keyboard that can be set as default
- **Real-time Analysis**: Analyzes text with 700ms debounce after typing stops
- **Risk Indicators**: Shows SAFE (green), OFFENSIVE (orange), or DANGEROUS (red) badges
- **Rewrite Suggestions**: Get AI-generated alternatives (Calm, Firm, Educational)
- **One-tap Replace**: Tap a suggestion to replace your current text

## Project Structure

```
android-ime/
├── app/
│   ├── src/main/
│   │   ├── java/com/keycare/ime/
│   │   │   ├── KeyCareIME.java      # Main keyboard service
│   │   │   └── SetupActivity.java   # Onboarding activity
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   ├── keyboard_view.xml    # Keyboard UI
│   │   │   │   └── activity_setup.xml   # Setup screen
│   │   │   ├── xml/
│   │   │   │   └── ime_method.xml       # IME configuration
│   │   │   ├── drawable/                # Backgrounds & badges
│   │   │   └── values/                  # Strings, themes, colors
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── build.gradle
├── settings.gradle
└── gradle.properties
```

## Requirements

- Java 17
- Gradle 8.x
- Android SDK (API 26+)
- Physical Android phone for testing
- ADB installed

## Build Instructions

### 1. Generate Gradle Wrapper (first time only)

```powershell
cd "0x01 Taibi El Yakouti\android-ime"
gradle wrapper --gradle-version 8.7
```

### 2. Build Debug APK

```powershell
.\gradlew.bat assembleDebug
```

APK location: `app\build\outputs\apk\debug\app-debug.apk`

### 3. Install on Phone

Connect your phone via USB with USB debugging enabled:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## Setup on Phone

1. **Open KeyCare Keyboard app** from your app drawer
2. **Tap "Enable KeyCare Keyboard"** → Toggle ON in system settings
3. **Go back** to the app
4. **Tap "Set as Default Keyboard"** → Select "KeyCare Keyboard"
5. **Test the connection** to ensure the API server is reachable

## API Configuration

The keyboard connects to:
```
http://192.168.1.10:8000
```

To change this, edit the `BASE_URL` constant in:
- `KeyCareIME.java` (line 23)
- `SetupActivity.java` (line 19)

### Required API Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/health` | GET | Connection test |
| `/detect` | POST | Analyze text for risk |
| `/rewrite` | POST | Get rewrite suggestions |

## How It Works

1. **User types** → Each keystroke updates local buffer
2. **700ms pause** → Triggers `/detect` API call
3. **Response received** → Updates risk badge and score
4. **If risky** → Shows "Rewrite" button
5. **User taps Rewrite** → Calls `/rewrite` API
6. **Suggestions appear** → User taps one to replace text

## Keyboard Layout

```
┌───┬───┬───┬───┬───┬───┬───┬───┬───┬───┐
│ q │ w │ e │ r │ t │ y │ u │ i │ o │ p │
├───┼───┼───┼───┼───┼───┼───┼───┼───┼───┤
│   │ a │ s │ d │ f │ g │ h │ j │ k │ l │
├───┼───┼───┼───┼───┼───┼───┼───┼───┼───┤
│ ⇧ │ z │ x │ c │ v │ b │ n │ m │   │ ⌫ │
├───┼───┴───┴───┴───┴───┴───┴───┼───┼───┤
│123│ ,  │      space      │ . │ ↵ │
└───┴────┴───────────────────┴───┴───┘
```

## Troubleshooting

### "Cannot reach server"
- Ensure your API server is running: `python api.py`
- Check that your phone and computer are on the same WiFi network
- Verify the IP address is correct (use `ipconfig` to find your PC's IP)

### Keyboard not appearing
- Make sure the keyboard is enabled in system settings
- Try setting it as default again via input method picker

### Build fails
- Run `gradle wrapper --gradle-version 8.7` to regenerate wrapper
- Ensure JAVA_HOME points to Java 17

## License

Part of the KeyCare project for the Enactus Morocco National Competition 2024.
