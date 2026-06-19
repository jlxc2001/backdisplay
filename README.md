# MikuTextDisplayNode

A lightweight Android text display node for ultra-wide strip screens such as `1920x158` Android 5.1.1 embedded displays.

## Device target

- Android 5.1.1 / SDK 22 supported
- minSdk 21
- targetSdk 22, optimized for old embedded devices and sideloading
- armeabi-v7a friendly; pure Java, no native libraries
- Designed for `1920x158`, but it also adapts to other landscape sizes

## v1.2 features

- Long press the display screen to open the local settings page
- Adjustable text color
- Adjustable background color
- Optional background image
- Optional IP display on the strip screen
- Keep-screen-on switch
- Default brightness is 100%, adjustable in local settings
- UDP / HTTP / ADB text display control
- Ultra-wide centered text; long text scrolls automatically
- Boot auto-start

## Local settings

Long press anywhere on the display screen to open the settings bar.

Because the target screen is only `1920x158`, the settings UI is a horizontal scroll bar. Swipe left/right to see all controls.

Available controls:

- Text color cycle
- Background color cycle
- Select background image
- Import `/sdcard/miku_text_bg.png` or `/sdcard/miku_text_bg.jpg` as background image
- Clear background image
- Show IP on/off
- Keep screen on/off
- Brightness down/up
- Restore default brightness
- Reset all defaults
- Return to display

ADB open settings directly:

```bat
adb shell am start -n com.jlxc.mikutextdisplay/.SettingsActivity
```

## Network protocol

Default ports:

- UDP: `47230`
- HTTP: `47231`

### UDP

Send UTF-8 text to the display device:

```text
SHOW:左方扫描
```

Also supported:

```text
TEXT:右方扫描
CLEAR
PING
```

If the UDP packet does not start with a command prefix, the whole packet is displayed as text.

### HTTP

```text
http://DISPLAY_IP:47231/show?text=左方扫描
http://DISPLAY_IP:47231/clear
http://DISPLAY_IP:47231/status
http://DISPLAY_IP:47231/ping
```

`/status` returns current text, IP, color settings, background image state, IP-display state, keep-screen-on state and brightness.

## ADB test

Start app:

```bat
adb shell am start -n com.jlxc.mikutextdisplay/.MainActivity
```

Show text:

```bat
adb shell am broadcast -a com.jlxc.mikutextdisplay.SHOW --es text "敌机！敌机！敌机！"
```

Clear:

```bat
adb shell am broadcast -a com.jlxc.mikutextdisplay.CLEAR
```

Open settings:

```bat
adb shell am start -n com.jlxc.mikutextdisplay/.SettingsActivity
```

Push a background image by ADB, then use the settings button `导入/sdcard图`:

```bat
adb push your_background.png /sdcard/miku_text_bg.png
adb shell am start -n com.jlxc.mikutextdisplay/.SettingsActivity
```

## Build

GitHub Actions workflow is included. Upload the whole folder to GitHub, then run the Android CI workflow. APK path:

```text
app/build/outputs/apk/release/app-release.apk
```

## Build note

This project intentionally keeps `targetSdk 22` for Android 5.1.1 embedded devices. GitHub Actions excludes `lintVitalRelease`, and `app/build.gradle` disables the Play target-SDK lint check because this APK is intended for sideloading, not Google Play release.
