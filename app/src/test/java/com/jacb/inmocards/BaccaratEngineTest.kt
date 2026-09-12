package com.jacb.inmocards

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException
import kotlin.random.Random

class BaccaratEngineTest {
    @Test
    fun bankerThirdCardTableMatchesEveryEntry() {
        val drawingValues = listOf(
            (0..9).toSet(), (0..9).toSet(), (0..9).toSet(),
            setOf(0, 1, 2, 3, 4, 5, 6, 7, 9), setOf(2, 3, 4, 5, 6, 7),
            setOf(4, 5, 6, 7), setOf(6, 7), emptySet(), emptySet(), emptySet()
        )
        for (banker in 0..9) {
            assertEquals(banker <= 5, BaccaratRules.bankerDraws(banker, null))
            for (third in 0..9) {
                assertEquals("Banker $banker / player third $third",
                    third in drawingValues[banker], BaccaratRules.bankerDraws(banker, third))
            }
        }
    }

    @Test
    fun naturalsAndPlayerDrawBoundaries() {
        for (player in 0..9) for (banker in 0..9) {
            assertEquals(player in 8..9 || banker in 8..9, BaccaratRules.isNatural(player, banker))
        }
        for (total in 0..9) assertEquals(total in 0..5, BaccaratRules.playerDraws(total))
    }

    @Test
    fun freshEightDeckBenchmark() {
        val result = BaccaratEngine().calculate(ProbabilityEngine(decks = 8).baccaratCounts())!!
        assertEquals(0.458597422632763, result.banker, 1e-10)
        assertEquals(0.446246609343596, result.player, 1e-10)
        assertEquals(0.095155968023640, result.tie, 1e-10)
    }

    @Test
    fun everyShoeIsNormalizedAndChangesWithItsComposition() {
        val outcomes = mutableListOf<BaccaratChances>()
        for (decks in listOf(2, 4, 6, 8)) {
            val engine = ProbabilityEngine(decks = decks)
            val result = BaccaratEngine().calculate(engine.baccaratCounts())!!
            outcomes += result
            assertEquals(1.0, result.banker + result.player + result.tie, 1e-10)
            assertTrue(result.banker > result.player)
            engine.record(CardRank.FOUR)
            assertNotEquals(result, BaccaratEngine().calculate(engine.baccaratCounts()))
            engine.undo(CardRank.FOUR)
            assertEquals(result, BaccaratEngine().calculate(engine.baccaratCounts()))
        }
        assertEquals(4, outcomes.toSet().size)
    }

    @Test
    fun exactEnumerationMatchesIndependentPhysicalCardPermutations() {
        val random = Random(20260912)
        val shoes = listOf(
            listOf(0, 0, 0, 0, 0, 0), listOf(1, 1, 1, 1, 1, 1),
            listOf(4, 4, 4, 4, 4, 4), listOf(0, 1, 3, 6, 8, 9),
            listOf(2, 2, 3, 5, 7, 8, 9)
        ) + List(12) { List(6 + random.nextInt(3)) { random.nextInt(10) } }
        shoes.forEach { shoe ->
            val counts = IntArray(10)
            shoe.forEach { counts[it]++ }
            val copy = counts.copyOf()
            val actual = BaccaratEngine().calculate(counts)!!
            val expected = physicalCardOracle(shoe)
            assertEquals("$shoe banker", expected.banker, actual.banker, 1e-10)
            assertEquals("$shoe player", expected.player, actual.player, 1e-10)
            assertEquals("$shoe tie", expected.tie, actual.tie, 1e-10)
            assertArrayEquals(copy, counts)
        }
    }

    @Test
    fun fewerThanSixCardsAreUnavailable() {
        for (size in 0..5) assertNull(BaccaratEngine().calculate(intArrayOf(size, 0, 0, 0, 0, 0, 0, 0, 0, 0)))
    }

    @Test
    fun cancellationIsHonored() {
        Thread.currentThread().interrupt()
        try {
            BaccaratEngine().calculate(ProbabilityEngine().baccaratCounts())
            fail("Expected cancellation")
        } catch (expected: CancellationException) {
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            Thread.interrupted()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeCountsAreRejected() {
        BaccaratEngine().calculate(IntArray(10) { -1 })
    }

    @Test(expected = IllegalArgumentException::class)
    fun oversizedCountsAreRejected() {
        BaccaratEngine().calculate(IntArray(10) { Int.MAX_VALUE })
    }

    // Separate oracle: enumerate labeled physical-card permutations, then evaluate each deal.
    // Deliberately does not call BaccaratRules or use weighted point-value recursion.
    private fun physicalCardOracle(cards: List<Int>): BaccaratChances {
        val used = BooleanArray(cards.size)
        val drawn = IntArray(6)
        val wins = LongArray(3)
        var permutations = 0L
        fun evaluate() {
            var p = (drawn[0] + drawn[2]) % 10
            var b = (drawn[1] + drawn[3]) % 10
            if (p < 8 && b < 8) {
                var next = 4
                var third = -1
                if (p < 6) {
                    third = drawn[next++]
                    p = (p + third) % 10
                }
                val drawBanker = if (third == -1) b < 6 else when (b) {
                    0, 1, 2 -> true
                    3 -> third != 8
                    4 -> third >= 2 && third <= 7
                    5 -> third >= 4 && third <= 7
                    6 -> third == 6 || third == 7
                    else -> false
                }
                if (drawBanker) b = (b + drawn[next]) % 10
            }
            wins[if (b > p) 0 else if (p > b) 1 else 2]++
            permutations++
        }
        fun permute(index: Int) {
            if (index == 6) {
                evaluate()
                return
            }
            cards.indices.forEach { card ->
                if (!used[card]) {
                    used[card] = true
                    drawn[index] = cards[card]
                    permute(index + 1)
                    used[card] = false
                }
            }
        }
        permute(0)
        return BaccaratChances(wins[0].toDouble() / permutations,
            wins[1].toDouble() / permutations, wins[2].toDouble() / permutations)
    }
}
