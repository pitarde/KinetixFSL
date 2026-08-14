# Quiz Game — implementation & how to add videos

The Quiz Game lives under `app/src/main/java/com/example/kinetixfsl/game/` and is
reached from the **Game** tab (the gamepad icon) in the bottom nav. It implements
the spec in `kinetixfsl-quiz-game-spec_1.md`: 10 levels across 3 sequential-unlock
tiers, offline-first, with a non-repeating first pass and retry-on-fail.

## File map

```
game/
├─ model/QuizModels.kt      Data + enums (Tier, QuestionType, QuizQuestion, LevelPlan…)
├─ data/
│  ├─ QuizContent.kt        The 3 tier banks (15/15/20 signs), built from FslSignData
│  ├─ QuizGenerator.kt      Selection algorithms A/B + retry, and question building
│  ├─ QuizStore.kt          Offline persistence (SharedPreferences + JSON)
│  └─ QuizRepository.kt     Progress, unlocking, resume, XP hook
├─ QuizGameViewModel.kt     The screen state machine
└─ ui/
   ├─ QuizGameRoot.kt       Entry point (wired into HomeScreen's Game tab)
   ├─ LevelMapScreen.kt     The level list (mockup #1)
   ├─ TutorialScreen.kt     The "New sign: …" 5-video phase (mockup #2)
   ├─ QuizScreen.kt         Hosts the 5 questions + Check/Next
   ├─ QuestionViews.kt      The 4 question types (mockups #3–#6)
   ├─ ResultScreen.kt       Pass/fail summary + retry
   ├─ SignVideo.kt          Real ExoPlayer clip OR VideoPlaceholderCard
   ├─ SignVideoAssets.kt    Resolves res/raw clip by sign id
   ├─ QuizChrome.kt         Shared X + progress bar
   └─ QuizIcons.kt          Hand-drawn vector icons
```

## The 4 question types

| Type | Prompt (mockup) | How it works |
|---|---|---|
| `WORD_FROM_VIDEO` | "Ano ang tama dito?" | Show the sign's video, pick the correct **word** from 4 |
| `VIDEO_FROM_WORD` | "Alin dito ang sign na X?" | Given a word, pick the correct **video** from 2 |
| `MATCH` | "Pagtugmain ang salita" | Tap a video then a word to connect 2 pairs |
| `TRUE_FALSE` | "Ito ba ay sign na X?" | Video + a claim → answer **Oo / Hindi** |

All four appear in every level (one repeats to make 5), shuffled.

## How questions map to your 7 categories

Every question is built from a sign in your existing `FslSignData`. The 3 banks
are **disjoint** and sized exactly to `levels × 5`:

- **Easy (Levels 1–3, 15 signs):** Greetings (4), Social (4), Numbers 1–5 (5), Eat & Drink (2)
- **Medium (Levels 4–6, 15 signs):** Sleep & Hungry (2), School (4), Numbers 0,6–9 (5), Alphabet A–D (4)
- **Hard (Levels 7–10, 20 signs):** Emergency (4), Alphabet E–T (16)

To change what's in a bank, edit the `bankOf(...)` lists in
[`QuizContent.kt`](../app/src/main/java/com/example/kinetixfsl/game/data/QuizContent.kt).
Sizes are asserted at startup, so keep them at 15 / 15 / 20.

---

## ▶ How to add the real videos later  (the important part)

**Convention:** a sign's clip is an `.mp4` in `app/src/main/res/raw`, named
**exactly by its sign id**. Nothing else changes — the quiz call site branches
automatically:

```kotlin
// SignVideo.kt — already wired
if (rawResId != 0) RealSignVideoPlayer(resId)   // a clip exists
else               VideoPlaceholderCard(word)   // still a placeholder
```

So to add a video: **drop `<signId>.mp4` into `res/raw` and rebuild.** No code
edit, no navigation change. Signs without a clip keep showing the lavender
placeholder card.

> ✅ **Letter A is already done** — `res/raw/alpha_a.mp4` exists, so Level with
> A plays your real clip today. Replace that file to swap in a better take.

### Filenames to use (sign id → file)

**Alphabet** — `alpha_a.mp4`, `alpha_b.mp4`, … `alpha_z.mp4`, plus
`alpha_enye.mp4` (Ñ), `alpha_ng.mp4` (NG).
*(The quiz currently uses A–T; the rest are ready if you extend the Hard bank.)*

**Numbers** — `num_0.mp4` … `num_9.mp4`

**Greetings** — `greet_kamusta.mp4`, `greet_salamat.mp4`, `greet_walang_anuman.mp4`, `greet_ayos_lang_ako.mp4`

**School** — `school_pagaaral.mp4`, `school_basahin.mp4`, `school_paaralan.mp4`, `school_magaaral.mp4`

**Emergency** — `emer_danger.mp4`, `emer_stop.mp4`, `emer_calm_down.mp4`, `emer_accident.mp4`

**Daily Needs** — `dn_eat.mp4`, `dn_drink.mp4`, `dn_sleep.mp4`, `dn_hungry.mp4`

**Social Interaction** — `soc_oo.mp4`, `soc_hindi.mp4`, `soc_kaibigan.mp4`, `soc_patawad.mp4`

> `res/raw` filenames must be lowercase and use only letters, digits and `_` —
> all the ids above already comply. The full id list is the source of truth in
> [`SignCategory.kt`](../app/src/main/java/com/example/kinetixfsl/modules/model/SignCategory.kt).

### Video guidance (from spec §7)
Cap each clip's duration and use **moderate** compression — but **don't** crush
finger/hand detail; gesture clarity matters more than file size for an FSL tool.
Do a quick visual QA pass on a couple of clips at your chosen setting before
encoding all 50.

---

## Animations & result screen

- **Tutorial** slides + fades between signs. The button left of **Next** steps
  **back** to the previous sign (dimmed/inert on the first one).
- **Answer feedback** pops a centered card ("Correct!" / "Not quite") over a dim
  scrim; its **Next** advances. The card hides instantly (no exit animation) so
  it never flashes the wrong state as the next question resets.
- **Sound + haptics** on Check: `res/raw/sfx_correct.mp3` / `sfx_wrong.mp3` play
  via `SoundPool`, with a matching vibration (needs the `VIBRATE` permission,
  already in the manifest). See `QuizFeedback.kt`.
- **Result screen** (`ResultScreen.kt`):
  - **Pass (≥4/5):** "Amazing!", the celebration illustration, a **confetti**
    burst, time + score stat pills, "Proceed to next lesson" / "Back to home page".
  - **Fail (<4/5):** "Good try!", "That's alright, better luck next time.", no
    confetti, "Try again" / "Back to home page".
  - To make confetti **perfect-score only**, change the branch in `ResultScreen`
    from `passed` to `correctCount == SIGNS_PER_LEVEL`.

### Result illustrations (drawables)

- **Celebration (pass):** already bundled as
  `res/drawable/quiz_celebration.webp` — extracted from your `quiz-done-icon.svg`.
- **"Better luck" (fail):** bundled as `res/drawable/quiz_try_again.xml` — a
  crisp Android **VectorDrawable** converted from `better-luck-icon.svg` (its
  gray background rect was dropped so it's transparent). Resolved by name at
  runtime, with a fallback vector if the drawable is ever missing.

## Behaviour notes & TODOs

- **Pass mark:** 4 of 5 correct (`PASS_THRESHOLD` in `QuizModels.kt`).
- **Persistence:** progress + the in-progress attempt are saved to
  `SharedPreferences` ("quiz_game") as JSON on every step, so the game survives a
  full app close and works fully offline. A resume popup ("Continue last
  session?") appears when you reopen the tab mid-attempt.
  *This deliberately avoids adding Room/KSP; swap `QuizStore` for Room later if
  you want the spec's exact table layout — nothing else needs to change.*
- **XP (spec §8):** stubbed via `QuizRepository.onLevelPassed(level, correct, passed, xp)`.
  Award real XP there once the scaling spec exists.
- **Firestore sync (spec §7):** local progress is the source of truth during
  play. Add a background sync (e.g. WorkManager) of unlocks/passes to
  `userProgress/{userId}` inside `QuizRepository.completeLevel(...)`.
