# Ghost Match 3

Android match-3 test game inspired by the supplied reference mockup.

## v0.3.0 test build

- Swipe adjacent ghosts on a 7×7 board.
- Collect white, green, and purple ghosts to fill the three goals.
- Separate level, score, moves, and three-star goal progress panels.
- Seven usable boosters: free swap, hammer, row clear, column clear, automatic helper, color-changing wand, and five extra moves.
- Animated ghosts, sparkles, combo feedback, a haunted backdrop, pause button, and level-complete overlay.
- Offline play. No login, ads, network permission, or server.

The artwork is still drawn at runtime, not a production sprite-asset recreation of the supplied mockup. The original mockup is a visual reference, not an exported sprite pack. A later art pass should replace the code-drawn characters and booster icons with dedicated transparent PNG/WebP assets and add movement/cascade animations. The test APK is not ready for Play Store distribution.

## Install

Download `GhostMatch3-v0.3.0.apk` from [Releases](https://github.com/sunantongsan/GhostMatch3/releases/tag/v0.3.0). Android may warn about a new, sideloaded developer. If an older debug build is installed with a different test signature, uninstall the old version first.

## Build

GitHub Actions runs a debug APK build and signature verification on each push to main, then attaches the APK to a versioned Release. This is a testing build, not a signed Play Store AAB.
