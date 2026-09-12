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
    val decks: Int = 2
) {
    private val initialPerRank = decks * 4
    private val remaining = CardRank.entries.associateWith { initialPerRank }.toMutableMap()

    init {
        require(decks in listOf(2, 4, 6, 8)) { "Unsupported deck count: $decks" }
        seenRanks.forEach { rank ->
            require(record(rank)) { "Recorded shoe exceeds rank capacity: ${rank.label}" }
        }
    }

    val totalRemaining: Int
        get() = remaining.values.sum()

    val seenCount: Int
        get() = decks * 52 - totalRemaining

    fun remaining(rank: CardRank): Int = remaining.getValue(rank)

    fun baccaratCounts(): IntArray = IntArray(10).also { counts ->
        remaining.forEach { (rank, count) -> counts[rank.baccaratValue()] += count }
    }

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
