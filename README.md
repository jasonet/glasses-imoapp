<div align="center">

# INMO Card Lab

**CLASSIC GAMES. VISIBLE PROBABILITY.**

A wearable research prototype for tabletop gaming, probability education, and spatial computing.

**INMO Air2 / Two decks / On-device vision / Local session history**

[Download v0.1](https://github.com/jasonet/glasses-imoapp/releases/tag/v0.1) &nbsp; | &nbsp; [Architecture](#system-architecture) &nbsp; | &nbsp; [Quick Start](#quick-start) &nbsp; | &nbsp; [Responsible Use](#responsible-use)

</div>

---

## The Table Becomes a Probability Lab

A card enters the frame. Its rank is recognized. A compact display updates the remaining shoe and the distribution of the next draw, directly in the wearer's field of view.

INMO Card Lab explores how smart glasses can make the mathematics of classic card games tangible. Version **0.1** uses **two-deck blackjack** as its reference scenario: 104 cards, 13 ranks, and an evolving distribution derived from recorded observations. Recognition, probability calculations, and session storage run on the glasses.

Built for controlled simulations, game-design workshops, and wearable interface research, this is an early engineering prototype. It recognizes **one card corner at a time inside a central guide**, rather than automatically interpreting an entire table.

## Experience Scenarios

| Setting | The experience | Research focus |
| --- | --- | --- |
| **The Probability Table** | Two decks on green felt. Present a card, watch the numbers shift, and discuss why the next-draw distribution changes. | Sampling without replacement and conditional probability. |
| **The Wearable Demo** | A dark, high-contrast display places ranks and colored status indicators over a subdued camera preview. Hide the preview to keep the numbers in focus. | Glanceable information and hands-free interaction. |
| **The Game-Design Workshop** | Record a controlled dealing sequence, inspect the remaining composition, and undo a mistaken observation. | Transparent numerical feedback for tabletop simulations. |
| **The Vision Bench** | Present known cards under different lighting, distances, and angles, then compare recognition with the actual sequence. | Template calibration and observation reliability. |

These scenarios describe guided prototype use. Version 0.1 does not include automated benchmarking, a table simulator, or multi-player tracking.

## What v0.1 Delivers

- **On-device rank recognition.** CameraX feeds a pure Kotlin template recognizer for A, 2-10, J, Q, and K, tailored to the Air2's 32-bit environment.
- **A two-deck probability model.** Eight cards per rank at initialization; the display sorts all 13 ranks by remaining-card probability.
- **Blackjack-oriented groups.** Low cards `2-6`, neutral cards `7-9`, ten-value cards `10/J/Q/K`, and aces appear as four separate summaries.
- **Temporal confirmation.** Three consecutive matching analyzed frames confirm a rank; two consecutive frames with no recognized rank re-arm detection.
- **Persistent sessions.** SQLite stores session identifiers, ranks, timestamps, and undo markers. Relaunching restores the active shoe.
- **Display and camera controls.** Switch between preview and reduced-analysis display modes, or pause the camera completely.

## System Architecture

```mermaid
flowchart TB
    table["Controlled tabletop<br/>One card corner in the guide"]

    subgraph device["INMO Air2 / Android / On-device processing"]
        direction TB
        camera["Camera 0<br/>CameraX + Camera2"]
        frames["CardAnalyzer<br/>640 x 480 requested / latest frame only<br/>Throttling + rotation + central crop"]
        vision["RankTemplateRecognizer<br/>Brightness check + Otsu threshold<br/>Connected components + glyph matching"]
        gate["RankStabilizer<br/>3 matching analyses to confirm<br/>2 blank analyses to re-arm"]
        activity["MainActivity<br/>Confirmed observation + session controls"]
        engine["ProbabilityEngine<br/>104-card inventory<br/>13 ranks + 4 group probabilities"]
        db[("CardDatabase / SQLite<br/>Sessions + timestamped observations<br/>Undo markers")]
        hud["Glasses display<br/>Rank ordering + remaining counts<br/>Group probabilities + status"]
        controls["User controls<br/>Undo / new shoe<br/>Preview toggle / camera pause"]

        camera --> frames --> vision --> gate --> activity
        activity -->|"Record / undo / reset"| engine
        activity -->|"Persist accepted observations"| db
        db -->|"Restore active-session ranks"| engine
        engine --> hud
        gate -->|"Recognition state"| hud
        controls --> activity
        controls -->|"Rebind / unbind camera"| camera
        controls -->|"350 / 650 ms interval"| frames
        camera -. "Optional preview" .-> hud
    end

    table --> camera

    classDef capture fill:#102d35,stroke:#49c9d9,color:#edfaff;
    classDef logic fill:#173b2f,stroke:#69d7aa,color:#effff6;
    classDef output fill:#3b3020,stroke:#e7bd65,color:#fff8e8;
    class table,camera,frames capture;
    class vision,gate,activity,engine,db logic;
    class hud,controls output;
```

The analyzer uses a single worker thread and `KEEP_ONLY_LATEST` backpressure. Confirmed ranks reach the activity, which updates the in-memory inventory, writes the observation to SQLite, and refreshes the display. On launch, stored active-session observations reconstruct that inventory. Frames are processed in memory; the app has no image/video recording pipeline or declared `INTERNET` permission.

Source entry points: [camera analysis](app/src/main/java/com/jacb/inmocards/CardAnalyzer.kt), [recognition](app/src/main/java/com/jacb/inmocards/RankTemplateRecognizer.kt), [confirmation](app/src/main/java/com/jacb/inmocards/RankStabilizer.kt), [probabilities](app/src/main/java/com/jacb/inmocards/ProbabilityEngine.kt), [storage](app/src/main/java/com/jacb/inmocards/CardDatabase.kt), and [interface](app/src/main/java/com/jacb/inmocards/MainActivity.kt).

## The Probability Model

For rank `r`, with `c(r)` recorded observations and `N` cards remaining:

```text
remaining(r) = 8 - c(r)
N            = sum of remaining cards across all 13 ranks
P(next = r)  = remaining(r) / N, for N > 0
```

| Display group | Ranks | Initial cards | Initial probability |
| --- | --- | ---: | ---: |
| Low | 2, 3, 4, 5, 6 | 40 | 38.46% |
| Neutral | 7, 8, 9 | 24 | 23.08% |
| Ten-value | 10, J, Q, K | 32 | 30.77% |
| Ace | A | 8 | 7.69% |

Each individual rank starts at `8 / 104`, approximately **7.69%**. After one ace is recorded, the next-ace probability becomes `7 / 103`, approximately **6.80%**; each other rank becomes `8 / 103`, approximately **7.77%**. The app rounds displayed percentages to one decimal place, so displayed totals can differ slightly from 100%.

The model assumes a uniformly shuffled shoe, no replacement during the session, and an accurate record of removed cards. Missed cards, duplicate observations, unrecorded removals, or reshuffling without a reset invalidate the recorded composition. An exhausted rank cannot be recorded again; an empty shoe displays zero probabilities until a new session is started. These values describe the next draw, not a hand's win probability or a guaranteed outcome.

## Quick Start

**Target:** INMO Air2 running Android 9 / API 28 with `armeabi-v7a`. The application minimum is API 26. The published v0.1 artifact is a **debug APK for prototype testing**; its interface currently uses Chinese labels.

1. Download [the APK](https://github.com/jasonet/glasses-imoapp/releases/download/v0.1/INMO-Card-Lab-0.1-debug.apk) and [SHA256SUMS.txt](https://github.com/jasonet/glasses-imoapp/releases/download/v0.1/SHA256SUMS.txt) into the same directory.
2. Enable USB debugging on the glasses, connect them to the computer, and approve the device authorization prompt.
3. Verify and install from that directory:

```bash
# macOS: verify the APK against the published checksum.
grep 'INMO-Card-Lab-0.1-debug.apk$' SHA256SUMS.txt | shasum -a 256 -c -
adb devices
adb install -r INMO-Card-Lab-0.1-debug.apk
adb shell am start -n com.jacb.inmocards/.MainActivity
```

Grant camera permission when prompted. Begin with a fresh two-deck shoe. Hold one card's corner numeral or letter in the yellow guide until confirmation, then remove it until the app is ready for the next card. Three analyzed matches take roughly a second in preview mode; actual timing depends on device throughput and recognition stability.

The four bottom controls, from left to right, are **Undo**, **New shoe (long press)**, **Display mode**, and **Pause/resume camera**. Undo reverses the latest recorded rank. Starting a new shoe creates a new active session and retains earlier session records in the database.

## Display and Power Behavior

| Mode | Camera preview | Recognition | Intended use |
| --- | --- | --- | --- |
| Preview | Visible, with alignment guide | At most one analysis per 350 ms | Positioning cards and evaluating recognition. |
| Reduced-analysis display | Preview and guide hidden | At most one analysis per 650 ms | Keeping probability readouts visible with less processing. |
| Paused | Hidden; camera use cases unbound | Stopped | Reviewing the current display between observations. |

The camera configuration requests 640 x 480 analysis frames and a 5-15 FPS capture range; the device determines the negotiated capture behavior. Capture FPS and recognition cadence are separate. Status text refreshes only when its message or color changes. The screen remains awake while the activity is active, including during camera pause. Battery savings have not yet been quantified.

## Responsible Use

The intended deployment is **consensual, non-wagering research and education**: private tabletop simulations, approved demonstrations, and game-design experiments. Obtain participant consent and venue approval before camera use. Do not use the prototype for covert assistance or in games, competitions, or venues that prohibit electronic devices.

This repository makes no claim of gaming certification, regulatory approval, or suitability for regulated deployment. Version 0.1 has no betting, payment, wager-sizing, or automated strategy functionality. Any commercial deployment needs a separate review of applicable rules, permissions, and data handling.

Recognition and observation storage are local to the application. Android backup is currently enabled in the manifest, so OS or device backup behavior must be reviewed before promising strict device-only data retention. Session records include timestamps; plan their retention and deletion for any organized study.

## Engineering Scope and Next Steps

The current recognizer uses generated glyph templates and needs calibration against real card fonts, lighting, motion, and viewing angles. It does not distinguish suits, physical card identities, table seats, player hands, or the dealer's upcard. Temporal confirmation reduces repeated observations but cannot guarantee physical-card deduplication. No recognition-accuracy or battery-life benchmark is published.

Planned research directions, **not included in v0.1**:

- Evaluate recognition against labeled, consented card samples and measure missed and duplicate observations.
- Add table regions and explicit hand assignment before exploring bust probabilities or dealer outcome distributions.
- Measure camera, CPU, and display energy use, then tune analysis cadence and rendering.
- Explore a more minimal monochrome display with sparse colored indicators and English UI localization.

## Build and Project Notes

The project uses Kotlin, Java 17, Android SDK 34, and CameraX 1.3.4. The recognizer runs without ML Kit or TensorFlow Lite inference; this avoids the native OCR path associated with `SIGILL` on the target device.

With JDK 17 and Android SDK 34 configured through `ANDROID_HOME` or an untracked `local.properties`:

```bash
./gradlew testDebugUnitTest assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. Unit tests cover the probability engine; they do not establish camera accuracy or hardware performance.

See the [v0.1 release](https://github.com/jasonet/glasses-imoapp/releases/tag/v0.1) for the APK, source archive, and checksums. The original [core feature specification](CORE_FEATURE_PROMPT.md) and [release notes](RELEASE_NOTES_0.1.md) are available in Chinese.
