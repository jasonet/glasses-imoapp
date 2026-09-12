package com.jacb.inmocards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProbabilityEngineTest {
    @Test
    fun initialTwoDeckProbabilitiesAreCorrect() {
        val engine = ProbabilityEngine()
        assertEquals(104, engine.totalRemaining)
        engine.rankChances().forEach { assertEquals(8.0 / 104.0, it.probability, 0.000001) }
        val groups = engine.blackjackChances()
        assertEquals(40.0 / 104.0, groups.low, 0.000001)
        assertEquals(24.0 / 104.0, groups.neutral, 0.000001)
        assertEquals(32.0 / 104.0, groups.tenValue, 0.000001)
        assertEquals(8.0 / 104.0, groups.ace, 0.000001)
    }

    @Test
    fun recordAndUndoUpdateRemainingCards() {
        val engine = ProbabilityEngine()
        assertTrue(engine.record(CardRank.KING))
        assertEquals(7, engine.remaining(CardRank.KING))
        assertEquals(103, engine.totalRemaining)
        engine.undo(CardRank.KING)
        assertEquals(8, engine.remaining(CardRank.KING))
        assertEquals(104, engine.totalRemaining)
    }

    @Test
    fun aRankCannotBeRecordedMoreThanEightTimes() {
        val engine = ProbabilityEngine()
        repeat(8) { assertTrue(engine.record(CardRank.ACE)) }
        assertFalse(engine.record(CardRank.ACE))
        assertEquals(0, engine.remaining(CardRank.ACE))
    }

    @Test
    fun everyPresetTracksItsOwnCapacityAndRestoresObservations() {
        ShoeConfig.presets.forEach { config ->
            val engine = ProbabilityEngine(decks = config.decks)
            val capacity = config.decks * 4
            assertEquals(config.totalCards, engine.totalRemaining)
            engine.rankChances().forEach { assertEquals(1.0 / 13, it.probability, 1e-12) }
            repeat(capacity) { assertTrue(engine.record(CardRank.ACE)) }
            assertFalse(engine.record(CardRank.ACE))
            assertEquals(config.totalCards - capacity, engine.totalRemaining)
            val restored = ProbabilityEngine(List(capacity) { CardRank.ACE }, config.decks)
            assertEquals(engine.rankChances(), restored.rankChances())
            restored.undo(CardRank.ACE)
            assertEquals(1, restored.remaining(CardRank.ACE))
            assertEquals(1.0, restored.rankChances().sumOf { it.probability }, 1e-12)
        }
    }

    @Test
    fun oneRemovedAceHasDeckDependentProbability() {
        listOf(2, 4, 6, 8).forEach { decks ->
            val engine = ProbabilityEngine(listOf(CardRank.ACE), decks)
            val chance = engine.rankChances().single { it.rank == CardRank.ACE }
            assertEquals((decks * 4 - 1).toDouble() / (decks * 52 - 1), chance.probability, 1e-12)
            val groups = engine.blackjackChances()
            assertEquals(1.0, groups.low + groups.neutral + groups.tenValue + groups.ace, 1e-12)
        }
    }

    @Test
    fun baccaratMergesOnlyTheZeroPointRanks() {
        val engine = ProbabilityEngine(listOf(CardRank.KING, CardRank.TEN, CardRank.ACE), 6)
        val counts = engine.baccaratCounts()
        assertEquals(94, counts[0])
        assertEquals(23, counts[1])
        (2..9).forEach { assertEquals(24, counts[it]) }
        assertEquals(engine.totalRemaining, counts.sum())
        counts[0] = 0
        assertEquals(94, engine.baccaratCounts()[0])
    }

    @Test
    fun emptyShoeAndUndoRemainBounded() {
        val engine = ProbabilityEngine(decks = 4)
        CardRank.entries.forEach { rank -> repeat(16) { engine.record(rank) } }
        assertEquals(0, engine.totalRemaining)
        engine.rankChances().forEach { assertEquals(0.0, it.probability, 0.0) }
        assertEquals(BlackjackChances(0.0, 0.0, 0.0, 0.0), engine.blackjackChances())
        repeat(20) { engine.undo(CardRank.KING) }
        assertEquals(16, engine.remaining(CardRank.KING))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsupportedDeckCount() {
        ProbabilityEngine(decks = 3)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOverfilledHistoryInsteadOfSilentlyChangingIt() {
        ProbabilityEngine(List(9) { CardRank.ACE })
    }

    @Test
    fun defaultRemainsTwoDeckBlackjack() {
        assertEquals(GameMode.BLACKJACK, ShoeConfig().mode)
        assertEquals(2, ShoeConfig().decks)
        assertEquals(listOf(2, 4, 6), GameMode.BLACKJACK.supportedDecks)
        assertEquals(listOf(2, 4, 6, 8), GameMode.BACCARAT.supportedDecks)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnavailableModeDeckCombination() {
        ShoeConfig(GameMode.BLACKJACK, 8)
    }
}
