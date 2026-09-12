package com.jacb.inmocards

import java.util.concurrent.CancellationException

data class BaccaratChances(val banker: Double, val player: Double, val tie: Double)

object BaccaratRules {
    fun isNatural(player: Int, banker: Int): Boolean = player >= 8 || banker >= 8

    fun playerDraws(total: Int): Boolean = total <= 5

    fun bankerDraws(total: Int, playerThird: Int?): Boolean = when {
        playerThird == null -> total <= 5
        total <= 2 -> true
        total == 3 -> playerThird != 8
        total == 4 -> playerThird in 2..7
        total == 5 -> playerThird in 4..7
        total == 6 -> playerThird in 6..7
        else -> false
    }
}

/** Exact next-complete-round probabilities from a remaining shoe, not an in-progress hand. */
class BaccaratEngine {
    fun calculate(valueCounts: IntArray): BaccaratChances? {
        require(valueCounts.size == 10 && valueCounts.all { it in 0..416 })
        val total = valueCounts.sum()
        require(total <= 8 * 52) { "Shoe exceeds supported capacity" }
        checkCancellation()
        // Reserve enough cards for every possible legal round, including both third cards.
        if (total < 6) return null
        val counts = valueCounts.copyOf()
        var bankerWin = 0.0
        var playerWin = 0.0
        var tie = 0.0

        fun settle(player: Int, banker: Int, weight: Double) {
            when {
                banker > player -> bankerWin += weight
                player > banker -> playerWin += weight
                else -> tie += weight
            }
        }

        fun finishBanker(player: Int, banker: Int, third: Int?, left: Int, weight: Double) {
            if (!BaccaratRules.bankerDraws(banker, third)) {
                settle(player, banker, weight)
                return
            }
            for (value in 0..9) {
                val count = counts[value]
                if (count > 0) settle(player, (banker + value) % 10, weight * count / left)
            }
        }

        fun finishRound(player: Int, banker: Int, weight: Double) {
            if (BaccaratRules.isNatural(player, banker)) {
                settle(player, banker, weight)
            } else if (!BaccaratRules.playerDraws(player)) {
                finishBanker(player, banker, null, total - 4, weight)
            } else {
                for (value in 0..9) {
                    val count = counts[value]
                    if (count == 0) continue
                    counts[value]--
                    finishBanker((player + value) % 10, banker, value, total - 5,
                        weight * count / (total - 4))
                    counts[value]++
                }
            }
        }

        fun deal(index: Int, player: Int, banker: Int, weight: Double) {
            checkCancellation()
            if (index == 4) {
                finishRound(player, banker, weight)
                return
            }
            for (value in 0..9) {
                val count = counts[value]
                if (count == 0) continue
                // Aggregate equal point values while retaining exact without-replacement weights.
                counts[value]--
                val nextWeight = weight * count / (total - index)
                if (index % 2 == 0) deal(index + 1, (player + value) % 10, banker, nextWeight)
                else deal(index + 1, player, (banker + value) % 10, nextWeight)
                counts[value]++
            }
        }

        deal(0, 0, 0, 1.0)
        return BaccaratChances(bankerWin, playerWin, tie)
    }

    private fun checkCancellation() {
        if (Thread.currentThread().isInterrupted) throw CancellationException()
    }
}
