# SerJoy 0.53.0 — sourced replacement

Levels 57–156 replace the old generated object scenarios with 100 question/answer pairs grounded in published Thai riddle collections. Each entry records its source; `serjoy-v7-sources.json` records the accepted answer separately for checking. Prompts are shortened/rephrased for standalone mobile play and spelling is normalized; distractors and brief explanations are editorial. These are not claims of verbatim reproduction or independently verified authorship.

First 56 records, all 156 IDs, saved question index, reward gates, match-three and audio remain unchanged. Source material is listed per question, including Sanook, JANTHAI, Wongnai, Postjung, Kroobannok, GotoKnow, NaniTalk, Kawtung and Dronk. Riddles, puns and trick questions are mixed; factual claims are not inferred from a joke's punchline.

New entries use `presentation: answer-cards`. The renderer takes visible and accessible answer labels directly from the question's choices, including complete reasons. Old scene target/choice sprites are suppressed for these entries so an unrelated old object cannot masquerade as the answer. Existing backgrounds and animated hero remain. Correctness still uses the canonical answer index, shuffled deterministically when generating the pack.

Content v7 requires app code 53. App 52 cannot safely display the new choices with its bundled old captions, so this is an app update rather than a text-only patch to app 52. Bundled v7 supersedes cached v6 offline. Subsequent compatible content can still update through the patch channel.

Validation: content checks verify all 100 source/answer pairs, stable first 56, IDs, dimensions, unique choices, pack equality and SHA-256. Android instrumentation renders all 156 levels, taps all 100 new correct answers, verifies canonical labels and disjoint hit areas, captures representative questions/reveals and runs the existing save/ad/audio/match-three regressions. Build, lint and device results are in the associated GitHub Actions run.
