# Bright Setup

First-run setup for Android TV boxes, in the style of the [Bright](https://github.com/omenight/bright-launcher) launcher. It replaces the
vendor setup wizard on the Verona H313 box (Android 10) and walks a new owner through three steps.

- **Remote** - keeps Bluetooth discovery running and pairs the first device that looks like a remote
  (uncategorized or remote-control peripheral class, or a name with remote, RCU or пульт). Anything else nearby can
  be picked by hand. Pairing confirmation is answered in the background, no system dialog.
- **Language** - Russian or English, applied system-wide at once.
- **Wi-Fi** - network list with an on-screen D-pad keyboard for the password, the box's own keyboard cannot be
  driven from a remote. Ethernet counts as online.

On start it sets the time zone to Europe/Moscow with network time. On finish it marks the device provisioned,
user-disables other system launchers when Bright is installed (so Bright is the only Home left, they can be turned
back on in the Apps screen), disables its own Home activity and opens the launcher.

## Build

```shell
./gradlew assembleRelease    # signed with the debug key, it ships inside the system image
```

## Install

It has to be a privileged system app in the device's only `SETUP_WIZARD` activity. In the Verona project that is
`tools/patch-system-brightsetup.sh` (system image plus the privapp whitelist) and `tools/patch-product-provision.sh`
(drops the AOSP Provision stub). Once it is in the image, newer builds go on with `adb install -r` and stay
privileged.
