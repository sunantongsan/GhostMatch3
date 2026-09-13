# Ghost Match 3

Original ghost-themed Android match-3 prototype with illustrated sprite assets, touch/swipe controls and staged effects. Inspired by common match-3 mechanics; no third-party game's artwork, music, names or level layouts are included.

## Test build v0.12.0

Download the APK from [Releases](https://github.com/sunantongsan/GhostMatch3/releases/tag/v0.12.0).

- Swap tween, ghost pop/burst, particles, cascading matches and visible falling pieces.
- Match four horizontally for a row power, vertically for a column power.
- Match five for a same-color clearing rainbow ghost.
- Match an L/T shape for an area burst. Powered ghosts can chain-react.
- Varied ghost collection goals; from level 6, clear frosted ghosts as an additional objective.
- Level progress saved locally on the device; boosters, pause and completion rewards.
- Original illustrated ghost sprites, magic item icons, anatomical skeleton victory dancer and haunted-valley background.
- First-launch guided swipe tutorial and gradual level goals with frost obstacles.

## Advertising test configuration

The test APK uses Google's official sample AdMob App ID and sample interstitial ad unit. These sample ads **do not earn revenue**. No other ad network is integrated. No ads appear in levels 1–3; later test interstitials may appear after the victory screen at levels 4, 7, 10, etc., after a short celebration, no more often than once every 90 seconds. If ad loading or consent fails, the game continues without an ad. A privacy-options entry point appears in the pause menu if the consent SDK indicates one is required.

Before any public Play Store release: register the app in your own AdMob publisher account, replace both sample IDs, configure the required privacy message and privacy policy, complete Play Data safety and ad declarations, test consent/privacy-options behavior in each target geography, and sign a production AAB with a stable private keystore. Do not publish this sample-ID debug build as a monetized version.

This is a debug test APK, not a Play Store build. Visual and gameplay tuning remains ongoing; Android compilation and APK signature checks do not substitute for hands-on phone testing. If Android cannot install over an older test APK because the signing key differs, uninstall the older test app first. That deletes its locally saved progress.

## Build

GitHub Actions builds and signature-verifies each push to main and publishes its test APK to a versioned release.
