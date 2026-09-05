package com.jacb.inmocards

class RankStabilizer(
    private val stableFrames: Int = 3,
    private val clearFrames: Int = 2,
    private val onConfirmed: (CardRank) -> Unit,
    private val onState: (String) -> Unit
) {
    private var candidate: CardRank? = null
    private var hits = 0
    private var blanks = 0
    private var armed = true
    private var lastState: String? = null

    fun offer(rank: CardRank?) {
        if (!armed) {
            if (rank == null) blanks++ else blanks = 0
            if (blanks >= clearFrames) {
                armed = true
                blanks = 0
                emitState("待识别")
            } else {
                emitState("请移开牌面")
            }
            return
        }

        if (rank == null) {
            candidate = null
            hits = 0
            emitState("将牌角数字对准框内")
            return
        }

        if (candidate == rank) hits++ else {
            candidate = rank
            hits = 1
        }
        emitState("识别 ${rank.label}  $hits/$stableFrames")

        if (hits >= stableFrames) {
            armed = false
            candidate = null
            hits = 0
            blanks = 0
            onConfirmed(rank)
        }
    }

    fun reset() {
        candidate = null
        hits = 0
        blanks = 0
        armed = true
        emitState("待识别")
    }

    private fun emitState(message: String) {
        if (message == lastState) return
        lastState = message
        onState(message)
    }
}
