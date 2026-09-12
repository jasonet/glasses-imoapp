package com.jacb.inmocards

enum class GameMode(val label: String, val supportedDecks: List<Int>) {
    BLACKJACK("21点", listOf(2, 4, 6)),
    BACCARAT("百家乐", listOf(2, 4, 6, 8))
}

data class ShoeConfig(val mode: GameMode = GameMode.BLACKJACK, val decks: Int = 2) {
    init {
        require(decks in mode.supportedDecks) { "Unsupported shoe: $mode / $decks" }
    }

    val label: String get() = "${mode.label} · ${decks}副"
    val totalCards: Int get() = decks * 52

    companion object {
        val presets: List<ShoeConfig> = GameMode.entries.flatMap { mode ->
            mode.supportedDecks.map { ShoeConfig(mode, it) }
        }
    }
}

data class ShoeSession(val id: Long, val config: ShoeConfig)
