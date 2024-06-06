package org.usvm

import org.usvm.gameserver.GameState
import org.usvm.statistics.BasicBlock
import org.usvm.util.Oracle
import org.usvm.utils.Game
import org.usvm.util.createGameState

class OracleImpl<Block: BasicBlock>(
    val predict: (GameState) -> UInt
): Oracle<Game<Block>> {
    override fun predictState(game: Game<Block>): UInt {
        val prediction = predict(createGameState(game))
        return prediction
    }
}
