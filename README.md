# Ghost Match 3

Original ghost-themed Android match-3 prototype with illustrated sprite assets, touch/swipe controls and staged effects. Inspired by common match-3 mechanics; no third-party game's artwork, music, names or level layouts are included.

## Test build v0.6.0

Download the APK from [Releases](https://github.com/sunantongsan/GhostMatch3/releases/tag/v0.6.0).

- Swap tween, ghost pop/burst, particles, cascading matches and visible falling pieces.
- Match four horizontally for a row power, vertically for a column power.
- Match five for a same-color clearing rainbow ghost.
- Match an L/T shape for an area burst. Powered ghosts can chain-react.
- Varied ghost collection goals; from level 6, clear frosted ghosts as an additional objective.
- Level progress saved locally on the device; boosters, pause and completion rewards.
- Original illustrated ghost sprites, booster icons and haunted-valley background.

This is a debug test APK, not a Play Store build. Visual and gameplay tuning remains ongoing; Android compilation and APK signature checks do not substitute for hands-on phone testing. If Android cannot install over an older test APK because the signing key differs, uninstall the older test app first. That deletes its locally saved progress.

## Build

GitHub Actions builds and signature-verifies each push to main and publishes its test APK to a versioned release.
