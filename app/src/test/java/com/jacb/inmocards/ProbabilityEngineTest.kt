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
}
