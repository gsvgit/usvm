package org.usvm.utils

import org.usvm.UState
import org.usvm.statistics.BasicBlock
import org.usvm.statistics.BlockGraph

data class StateHistoryElement(
    val blockId: Int,
    var numOfVisits: Int = 0,
    var stepWhenVisitedLastTime: Int = 0
)

class StateWrapper<Statement, State, Block>(
    // TODO: too many parameters
    private val state: State,
    private val parentPathConditionSize: Int,
    private val parentHistory: MutableMap<Block, StateHistoryElement>,
    private val blockGraph: BlockGraph<*, Block, Statement>,
    steps: Int
) where State : UState<*, *, Statement, *, *, State>, Block : BasicBlock {
    val children = mutableSetOf<StateWrapper<Statement, State, Block>>()
    val history = parentHistory
    val id = state.id

    val position: Int
        get() = currentBlock.id

    val pathConditionSize: Int
        get() = parentPathConditionSize + state.forkPoints.depth

    val visitedAgainVertices: Int
        get() = history.values.count { it.numOfVisits > 1 }

    val visitedNotCoveredVerticesInZone: Int
        get() = history.keys.count { !it.coveredByTest && it.inCoverageZone }

    val visitedNotCoveredVerticesOutOfZone: Int
        get() = history.keys.count { !it.coveredByTest && !it.inCoverageZone }

    val visitedStatement: Statement
        get() = checkNotNull(state.pathNode.parent?.statement)

    var stepWhenMovedLastTime = steps

    val instructionsVisitedInCurrentBlock: Int
        get() = blockGraph.statementsOf(currentBlock).indexOf(visitedStatement) + 1

    val currentBlock: Block
        get() = blockGraph.blockOf(visitedStatement)

    fun update(steps: Int) {
        stepWhenMovedLastTime = steps
        updateBlock(stepWhenMovedLastTime)
    }

    fun updateBlock(steps: Int) {
        history.getOrPut(currentBlock) { StateHistoryElement(position) }.apply {
            val statements = blockGraph.statementsOf(currentBlock)
            if (statements.first() == visitedStatement) {
                numOfVisits++
                currentBlock.touchedByState = true
            }
            if (statements.last() == visitedStatement) {
                currentBlock.visitedByState = true
                stepWhenVisitedLastTime = steps
            }
        }
    }

    fun addChildren(wrappers: Collection<StateWrapper<Statement, State, Block>>) =
        children.addAll(wrappers)
}