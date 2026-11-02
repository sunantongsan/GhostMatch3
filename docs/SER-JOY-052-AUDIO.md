# 0.52.0 randomized user audio

Questions, choices, answer explanations and artwork remain unchanged from 0.51.0.

- Laugh bag: existing laugh + all three new Dragon Studio laughs; entire clips retained.
- Correct-answer / match-three completion bag: existing correct, existing complete, and the supplied crowd cheer (3.8-second excerpt with fade, finishing before spoken explanation).
- Music bag: existing quiz music, existing break music, full supplied Bouncy Again opening. Advances automatically when a song finishes and when switching game modes.
- Each bag uses all its clips once per shuffled round and prevents adjacent repeats across rounds.
- Pause/ad/background stops effects and pauses music; resume continues music. Mute blocks both, and release frees players.
- New audio is mono 22.05kHz / 80kbps MP3, keeping short SoundPool samples below its decoded-size limit. Original uploads are preserved.

Validation: deterministic multi-seed shuffle coverage; Android decoding, actual reaction streams, track completion, mute/unmute and pause/resume regression. Existing 156-stage and ad-return tests retained.
