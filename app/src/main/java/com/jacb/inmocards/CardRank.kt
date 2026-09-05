package com.jacb.inmocards

enum class CardRank(val label: String) {
    ACE("A"),
    TWO("2"),
    THREE("3"),
    FOUR("4"),
    FIVE("5"),
    SIX("6"),
    SEVEN("7"),
    EIGHT("8"),
    NINE("9"),
    TEN("10"),
    JACK("J"),
    QUEEN("Q"),
    KING("K");

    companion object {
        fun fromLabel(value: String): CardRank? = entries.firstOrNull { it.label == value }
    }
}

enum class RankGroup { LOW, NEUTRAL, TEN_VALUE, ACE }

fun CardRank.group(): RankGroup = when (this) {
    CardRank.ACE -> RankGroup.ACE
    CardRank.TWO, CardRank.THREE, CardRank.FOUR,
    CardRank.FIVE, CardRank.SIX -> RankGroup.LOW
    CardRank.SEVEN, CardRank.EIGHT, CardRank.NINE -> RankGroup.NEUTRAL
    CardRank.TEN, CardRank.JACK, CardRank.QUEEN, CardRank.KING -> RankGroup.TEN_VALUE
}
