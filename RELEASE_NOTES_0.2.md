# INMO Card Lab 0.2

Configurable shoes and a separate baccarat probability model for the INMO Air2 prototype.

## Changes

- Blackjack supports 2, 4, and 6 decks. Fresh installations still default to 2 decks.
- Baccarat supports 2, 4, 6, and 8 decks. Two/four decks are simulation presets; eight decks is a Macao reference preset, not a universal venue claim.
- The selector starts a new session with the chosen configuration and preserves previous history. Long-press reset retains the current preset; restart restores it.
- Exact without-replacement baccarat enumeration includes naturals and all third-card rules. The display separates next-complete-round banker/player/tie outcomes from next-card rank probabilities and the zero-point summary.
- Calculations run on a dedicated worker, cancel superseded snapshots, and reject stale UI results. Fewer than six remaining cards makes round probabilities unavailable.
- SQLite v2 adds game mode, preserves v1 blackjack sessions, and creates replacement sessions transactionally. Failed observation writes no longer advance the displayed inventory.
- Percentages show up to two decimals. Camera callbacks are ignored during pause, configuration selection, and inactive lifecycle states.
- API 26 theme compatibility, explicit Camera2 opt-in, optional touchscreen declaration, and recognition-guide allocation cleanup.

## Verification

Validation on 2026-09-12: all 20 JVM tests passed; debug APK and Android test APK builds succeeded; Android lint completed with no errors (existing compatibility/localization warnings remain). The JVM suite includes all preset capacities, restored inventories, the complete third-card table, an eight-deck benchmark, and an independent physical-card permutation oracle.

The three Android instrumentation tests exercise database migration, restart, undo, and reset rollback. They compiled but were not executed: no ADB device or emulator was connected. Camera behavior, on-glasses layout, database migration on the device, and power measurements still require hardware testing.

## Model Scope

The baccarat outcome model starts a fresh round from the recorded remaining shoe; it does not infer player/banker ownership in an unfinished round. Record all exposed cards from a completed round before interpreting the next-round display. Recognition still uses one guided card corner at a time. Unseen removals and reshuffles can invalidate recorded composition. Cut cards, burn-card identities, side bets, commission, and payout calculations are not modeled.

Rule references and deck-count context are linked in [README.md](README.md). The prior [v0.1 release](https://github.com/jasonet/glasses-imoapp/releases/tag/v0.1) remains separate from these v0.2 build artifacts.
