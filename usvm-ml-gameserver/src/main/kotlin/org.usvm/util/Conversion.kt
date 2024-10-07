package org.usvm.util

import org.usvm.gameserver.GameEdgeLabel
import org.usvm.gameserver.GameMapEdge
import org.usvm.gameserver.GameMapVertex
import org.usvm.gameserver.GameState
import org.usvm.gameserver.State
import org.usvm.gameserver.StateHistoryElem
import org.usvm.statistics.BasicBlock
import org.usvm.statistics.BlockGraph
import org.usvm.utils.Game
import org.usvm.utils.StateHistoryElement
import org.usvm.utils.StateWrapper


private fun StateHistoryElement.toStateHistoryElem(): StateHistoryElem {
    return StateHistoryElem(
        blockId.toUInt(),
        numOfVisits.toUInt(),
        stepWhenVisitedLastTime.toUInt()
    )
}

private fun <Block : BasicBlock> StateWrapper<*, *, Block>.toState(): State {
    return State(
        id,
        position.toUInt(),
        pathConditionSize.toUInt(),
        visitedAgainVertices.toUInt(),
        visitedNotCoveredVerticesInZone.toUInt(),
        visitedNotCoveredVerticesOutOfZone.toUInt(),
        stepWhenMovedLastTime.toUInt(),
        instructionsVisitedInCurrentBlock.toUInt(),
        history.values.map { it.toStateHistoryElem() },
        children.map { it.id }
    )
}

private fun <Block : BasicBlock> Block.toGameMapVertex(): GameMapVertex {
    return GameMapVertex(
        id.toUInt(),
        inCoverageZone,
        basicBlockSize.toUInt(),
        coveredByTest,
        visitedByState,
        touchedByState,
        containsCall,
        containsThrow,
        states
    )
}

private fun <Block : BasicBlock> BlockGraph<*, Block, *>.toGameMapEdge(blocks: Collection<Block>): List<GameMapEdge> {
    return blocks.flatMap { block ->
        val successorsEdges = successors(block).map { successor ->
            GameMapEdge(
                vertexFrom = block.id.toUInt(),
                vertexTo = successor.id.toUInt(),
                label = GameEdgeLabel(0)
            )
        }

        val calleesEdges = callees(block).map { callee ->
            GameMapEdge(
                vertexFrom = block.id.toUInt(),
                vertexTo = callee.id.toUInt(),
                label = GameEdgeLabel(1)
            )
        }

        val returnOfEdges = returnOf(block).map { returnSite ->
            GameMapEdge(
                vertexFrom = block.id.toUInt(),
                vertexTo = returnSite.id.toUInt(),
                label = GameEdgeLabel(2)
            )
        }

        successorsEdges + calleesEdges + returnOfEdges
    }
}

fun <Block : BasicBlock> createGameState(
    game: Game<Block>
): GameState {
    val (vertices, stateWrappers, blockGraph) = game
    return GameState(
        graphVertices = vertices.map { it.toGameMapVertex() },
        states = stateWrappers.map { it.toState() },
        map = blockGraph.toGameMapEdge(vertices)
    )
}
