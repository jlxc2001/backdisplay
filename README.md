# MikuTextDisplayNode

A lightweight Android text display node for ultra-wide strip screens such as `1920x158` Android 5.1.1 embedded displays.

## Device target

- Android 5.1.1 / SDK 22 supported
- minSdk 21
- armeabi-v7a friendly; pure Java, no native libraries
- Designed for `1920x158`, but it also adapts to other landscape sizes

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

## Build

GitHub Actions workflow is included. Upload the whole folder to GitHub, then run the Android CI workflow. APK path:

```text
app/build/outputs/apk/release/app-release.apk
```


## Build note

This project intentionally keeps `targetSdk 22` for Android 5.1.1 embedded devices. GitHub Actions excludes `lintVitalRelease`, and `app/build.gradle` disables the Play target-SDK lint check because this APK is intended for sideloading, not Google Play release.
