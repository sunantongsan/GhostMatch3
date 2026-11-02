# Ser Joy 0.48.0: illustrated story stages

All 142 questions now use the same interactive scene renderer, with individually assigned environment, subject, three illustrated objects and resolution action in `app/src/main/assets/story/scenes.json`. The first 22 questions retain their text; the supplied 20 occupy 23–42; the previous 100 follow at 43–142. Door choices are hammer, bomb, fire. Historical content files remain available for reproducibility.

The old quiz cards are replaced by objects placed within the picture. Abstract answers (weekdays, numbers, song names) retain short inscriptions on their props. Native hit targets have accessible spoken labels. Long press reads an object label. Wrong answers animate laughter/head shake without exposing the punchline. Existing rewarded-ad and three-miss match-three gates remain, but completion returns to the same unsolved question. Only a correct answer followed by Next advances.

Every scene uses a 5.2-second pick-up/carry/resolution timeline with authored action parameters and shared animation effects. These are reusable 2D sprite transformations and effects, not 142 separately rendered videos. Door fire has its own frightened-door run and dust sequence; eggs crack, the toad jumps onto ice cream, and other actions use the corresponding effect groups. Animation pauses when inactive and locks choices during resolution. Restarting a solved question replays the resolution without granting extra progression.

Speech uses Android TextToSpeech in th-TH, honoring the existing sound setting. A Thai voice installed on the device is needed; question and punchline speech bubbles remain available if the engine is absent. No external TTS service or key is used.

## Assets and provenance

Created with the built-in image-generation tool for this project: a 16-environment atlas, eight 8x4 object atlases, a 4x4 special-prop atlas, and a 4x4 new character motion sheet based on the existing Ser Joy reference. Original generated PNGs were converted to WebP for packaging, preserving alpha. Art is shared across scenes; each question selects its objects/environment/action, rather than claiming 142 unique background paintings.

Prompt set: modern colorful Thai comedic adventure backgrounds (hallway, home, market, school, river, bedroom, bank, hospital, kitchen, library, court, castle, workshop, garden, cinema, temple); isolated illustrated objects ordered by the existing choice catalog; special angry/frightened door, hammer, bomb, torch and story props; same Ser Joy character speaking, laughing/head-shaking, carrying, walking, and celebrating. Transparent atlases use consistent grid cells. `story/icons.json` records the catalog mapping. Additional explicit overrides distinguish the supplied jokes' pictorial choices.

## Delivery and compatibility

Content v4 is a schema-1 data patch compatible with app 43+ (142 entries, checksum verified), so the revised joke order can reach existing clients. The new renderer, artwork, Thai speech and retry semantics require installation of app 48 through the normal Android/Google Play update path. They cannot be installed through the old text-only patch loader. App 48 bundles v4 for offline use. Future patches adding new scene IDs must also provide compatible app artwork/scene data; do not publish unsupported counts to this renderer.

Validation includes full content/art reference coverage, preserved old tail, Java state-machine tests, Android builds/lint, all 142 scene instantiations and hit rectangles, real touch selection of all door choices, restart gates, pause/resume, sample cutscene screenshots, and match-three returning to the unsolved question. Live rewarded ad delivery and availability/quality of device Thai voices are not simulated by the device test.
