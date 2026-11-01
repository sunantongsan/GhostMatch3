# ท่านเซ่อจ่อย (Than Ser Joy)

Android joke-choice game in the existing `com.sunantongsan.ghostmatch3` application.

- 22 Thai jokes supplied by the project owner; three illustrated symbol choices each.
- Correct: pondering reaction followed by congratulations, then next question. Reset wrong streak.
- Wrong 1–2: playful mocking reaction and punchline. Explicit rewarded-ad button; only earned reward followed by dismissal advances.
- Wrong 3: 10 valid match-three moves, then next question; no extra ad. No win requirement.
- Ad unavailable: retry, stay, or complete one match-three round as an offline alternative.
- Quiz phase/streak and break board persist across lifecycle/process recreation. Legacy campaign progress is untouched in its separate preference file.
- Uses the original Google TEST rewarded ad ID and existing age/privacy flow. Official demo ad mode bypasses publisher UMP configuration; the UMP flow remains for a future production configuration. No production ads.

## Artwork
`app/src/main/assets/ser-joy-animation.webp`: generated from the owner's yellow-shirt character and exaggerated-expression reference. Prompt: twelve 4x3 sequential waist-up animation keyframes: arm lift and blink / pointing and open-close laughter / scratching, realization, pointing and thumbs up, white background, bold Thai gag-cartoon outlines, no text. Built-in image generation; frames selected with per-action timing at runtime, with the animation clock paused in background. The original ghost sprite art, match-three engine, and audio are reused.

## Checks
CI compiles, lints, tests the quiz state machine and retained board, then runs device smoke tests and captures actual Android screenshots. Test APK is debug-signed, not the Play upload key.

Legacy public `docs/patches` endpoints remain for already-installed versions; the new quiz does not load old campaign patches. Old story renderer, rescues, boss presentation, shop, map, and unused packaged art have been removed. The existing engine retains its tested rules internally; the quiz only invokes its short clear-board variant.

`app_icon.webp` is a generated single close-up of the same yellow-shirt character with a cheeky laughing expression on a golden background, without text.
