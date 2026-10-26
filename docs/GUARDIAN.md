# คนดีผีคุ้ม — guardian rescue, Android 0.41.0

Players match the original expressive ghost characters to protect a human doing good deeds. The 32 rescue stages in the 120-level campaign each have an authored opening, incident, two intervention objectives, and outcome. Eight intervention mechanisms share four original environments. This is not 32 distinct environment paintings.

Openings are seven-second in-engine animated cinematics using the same scene and character as play, not downloaded executable content or pre-rendered video. Skip begins gameplay immediately. The rescue clock advances only in active foreground play, pauses for the intro, menus, shop and background, and persists with saved progress. Freeze gives eight seconds; the shield saves a deadline once with ten seconds; extra moves also grant fifteen seconds. Boss turn-based rules are unchanged.

Collected target ghosts charge the scene mechanism; cleared blockers open the escape route. Both must complete before escape, then celebration and reward. Rewards are credited once through the existing saved-run guard.

## Patches

Existing 0.40.0 clients cannot acquire new renderer/clock code via their schema-2 balance patch. They must install 0.41.0 once. Existing schema-2 balance data is retained. The separate `guardian-manifest.json` is schema 3 and requires app version 41. Its immutable `guardian-N.json` contains exactly the 32 rescue episodes, with level, mechanism (0–7), seconds (60–300), title, kindness, incident, action1, action2, ending. Each text is nonempty and at most 100 characters. Downloaded data is bounded, checksummed, validated, and atomically replaced; offline or malformed data keeps bundled/last-valid episodes. Future artwork or new mechanism code still requires an app build.

## Verification

Campaign rule tests cover all 120 stages. GuardianClockTest exercises elapsed time, freezing, deadline and extension bounds. Android smoke checks test intro timer isolation, pause, freeze, shield, timeout, scene mechanisms, rewards and persistence on API 24 and 35. API 35 also records an actual gameplay showcase. The recording has no system audio capture; game audio is active during play.
