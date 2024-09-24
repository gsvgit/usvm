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

    private var visitedStatement: Statement? = null
    lateinit var currentBlock: Block
    var position: Int = -1
    var pathConditionSize: Int = -1
    var visitedAgainVertices: Int = -1
    var visitedNotCoveredVerticesInZone: Int = -1
    var visitedNotCoveredVerticesOutOfZone: Int = -1
    var instructionsVisitedInCurrentBlock: Int = -1
    var stepWhenMovedLastTime: Int = -1

    fun update(steps: Int) {
        val previousBlock = visitedStatement?.let { currentBlock }
        visitedStatement = checkNotNull(state.pathNode.parent?.statement)
        currentBlock = blockGraph.blockOf(visitedStatement!!)
        if (previousBlock != currentBlock) {
            previousBlock?.states?.remove(this@StateWrapper.id)
            currentBlock.states.add(this@StateWrapper.id)

            position = currentBlock.id
            pathConditionSize = parentPathConditionSize + state.forkPoints.depth
            instructionsVisitedInCurrentBlock = 0
        }
        instructionsVisitedInCurrentBlock++
        stepWhenMovedLastTime = steps

        updateBlock(stepWhenMovedLastTime)
        updateVertexCounts()
    }

    private fun updateVertexCounts() {
        visitedNotCoveredVerticesInZone = 0
        visitedNotCoveredVerticesOutOfZone = 0
        visitedAgainVertices = 0
        history.entries.forEach { (vertex, historyValue) ->
            if (!vertex.coveredByTest && vertex.inCoverageZone) {
                visitedNotCoveredVerticesInZone++
            }
            if (!vertex.coveredByTest && !vertex.inCoverageZone) {
                visitedNotCoveredVerticesOutOfZone++
            }
            if (historyValue.numOfVisits > 1) {
                visitedAgainVertices++
            }
        }
    }


    private fun updateBlock(steps: Int) {
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