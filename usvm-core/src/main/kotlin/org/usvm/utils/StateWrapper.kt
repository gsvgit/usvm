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
    private val state: State,
    private val parentPathConditionSize: Int,
    private val parentHistory: MutableMap<Block, StateHistoryElement>,
    private val blockGraph: BlockGraph<*, Block, Statement>,
) where State : UState<*, *, Statement, *, *, State>, Block : BasicBlock {
    val children = mutableSetOf<StateWrapper<Statement, State, Block>>()
    val history = parentHistory.toMutableMap()
    val id = state.id

    val visitedNotCoveredVerticesInZone: Int
        get() = history.keys.count { !it.coveredByTest && it.inCoverageZone }

    val visitedNotCoveredVerticesOutOfZone: Int
        get() = history.keys.count { !it.coveredByTest && !it.inCoverageZone }

    var visitedStatement: Statement? = null
    lateinit var currentBlock: Block
    var position: Int = 0
    var pathConditionSize: Int = 0
    var visitedAgainVertices: Int = 0
    var instructionsVisitedInCurrentBlock: Int = 0
    var stepWhenMovedLastTime: Int = 0

    fun update(steps: Int) {
        visitedStatement = checkNotNull(state.pathNode.parent?.statement)
        currentBlock = blockGraph.blockOf(visitedStatement!!)

        position = currentBlock.id
        pathConditionSize = parentPathConditionSize + state.forkPoints.depth
        visitedAgainVertices = history.values.count { it.numOfVisits > 1 }
        instructionsVisitedInCurrentBlock = blockGraph.statementsOf(currentBlock).indexOf(visitedStatement) + 1
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

    fun addChildren(wrappers: Collection<StateWrapper<Statement, State, Block>>) {
        wrappers.forEach { it.update(stepWhenMovedLastTime) }
        children.addAll(wrappers)
    }
}