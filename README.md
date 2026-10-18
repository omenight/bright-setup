# Bright Setup

A minimal first-run setup wizard for Android TV, in the style of the [Bright](https://github.com/omenight/bright-launcher) launcher.

It replaces the stock setup wizard and walks a new owner through three steps, then hands the box over to Bright. No accounts, no sign-ins, no terms to accept.

## Steps

- **Remote** - Bluetooth discovery keeps running and the first device that looks like a remote is paired (uncategorized or remote-control peripheral class, or a name with remote, RCU or пульт). Anything else nearby can be picked by hand. The pairing confirmation is answered in the background, no system dialog.
- **Language** - Russian or English, applied system-wide at once.
- **Wi-Fi** - the network list with an on-screen D-pad keyboard for the password, since the system keyboard cannot always be driven from a remote. Ethernet counts as online.

On start it turns on network time and sets the time zone to Europe/Moscow. On finish it marks the device provisioned, disables the other system launchers when Bright is installed (the way the Apps screen does, so they can be turned back on there), disables its own Home activity and opens Bright.

## Install

Bright Setup needs privileged permissions, so it has to be part of the system image and cannot be installed from an APK alone. It targets Android 10 and newer.

1. Put the APK in `/system/priv-app/BrightSetup/BrightSetup.apk`.
2. Whitelist its permissions in `/system/etc/permissions/privapp-permissions-brightsetup.xml`:

   ```xml
   <permissions>
       <privapp-permissions package="com.omenight.bright.setup">
           <permission name="android.permission.BLUETOOTH_PRIVILEGED" />
           <permission name="android.permission.NETWORK_SETUP_WIZARD" />
           <permission name="android.permission.CHANGE_CONFIGURATION" />
           <permission name="android.permission.SET_TIME_ZONE" />
           <permission name="android.permission.WRITE_SECURE_SETTINGS" />
           <permission name="android.permission.CHANGE_COMPONENT_ENABLED_STATE" />
       </privapp-permissions>
   </permissions>
   ```

3. Remove the stock setup wizard (usually the AOSP `Provision` app or a vendor one), so Bright Setup is the only `SETUP_WIZARD` activity.

Once it is in the image, newer builds go on with `adb install -r` and stay privileged.

## Building

Requires JDK 17 and the Android SDK with platform 37.

```shell
./gradlew assembleDebug     # app/build/outputs/apk/debug/
./gradlew assembleRelease   # minified, signed with the debug key since it ships inside the system image
```

## License

GPL-3.0, see [LICENSE](LICENSE).
