package com.jacb.inmocards

data class RankChance(
    val rank: CardRank,
    val remaining: Int,
    val probability: Double
)

data class BlackjackChances(
    val low: Double,
    val neutral: Double,
    val tenValue: Double,
    val ace: Double
)

class ProbabilityEngine(
    seenRanks: List<CardRank> = emptyList(),
    private val decks: Int = 2
) {
    private val initialPerRank = decks * 4
    private val remaining = CardRank.entries.associateWith { initialPerRank }.toMutableMap()

    init {
        seenRanks.forEach { rank ->
            if ((remaining[rank] ?: 0) > 0) remaining[rank] = remaining.getValue(rank) - 1
        }
    }

    val totalRemaining: Int
        get() = remaining.values.sum()

    val seenCount: Int
        get() = decks * 52 - totalRemaining

    fun remaining(rank: CardRank): Int = remaining.getValue(rank)

    fun record(rank: CardRank): Boolean {
        val count = remaining.getValue(rank)
        if (count <= 0) return false
        remaining[rank] = count - 1
        return true
    }

    fun undo(rank: CardRank) {
        remaining[rank] = (remaining.getValue(rank) + 1).coerceAtMost(initialPerRank)
    }

    fun rankChances(): List<RankChance> {
        val total = totalRemaining
        return CardRank.entries.map { rank ->
            val count = remaining.getValue(rank)
            RankChance(rank, count, if (total == 0) 0.0 else count.toDouble() / total)
        }.sortedWith(compareByDescending<RankChance> { it.probability }.thenBy { it.rank.ordinal })
    }

    fun blackjackChances(): BlackjackChances {
        val total = totalRemaining
        if (total == 0) return BlackjackChances(0.0, 0.0, 0.0, 0.0)
        fun chance(group: RankGroup): Double = remaining
            .filterKeys { it.group() == group }
            .values.sum().toDouble() / total
        return BlackjackChances(
            low = chance(RankGroup.LOW),
            neutral = chance(RankGroup.NEUTRAL),
            tenValue = chance(RankGroup.TEN_VALUE),
            ace = chance(RankGroup.ACE)
        )
    }
}
