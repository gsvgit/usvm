package org.usvm.utils

import org.usvm.UState
import org.usvm.statistics.BasicBlock
import org.usvm.statistics.BlockGraph
import kotlin.properties.Delegates

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
    var position by Delegates.notNull<Int>()
    var pathConditionSize by Delegates.notNull<Int>()
    var visitedAgainVertices by Delegates.notNull<Int>()
    var visitedNotCoveredVerticesInZone by Delegates.notNull<Int>()
    var visitedNotCoveredVerticesOutOfZone by Delegates.notNull<Int>()
    var instructionsVisitedInCurrentBlock by Delegates.notNull<Int>()
    var stepWhenMovedLastTime by Delegates.notNull<Int>()

    fun update(steps: Int) {
        val previousBlock = visitedStatement?.let { currentBlock }
        // getting parent statement because pathNode is pointing to
        // next statement (to step on)
        visitedStatement = state.pathNode.parent?.statement
        currentBlock = blockGraph.blockOf(checkNotNull(visitedStatement))
        if (previousBlock != currentBlock) {
            previousBlock?.states?.remove(this@StateWrapper.id)
            currentBlock.states.add(this@StateWrapper.id)

            position = currentBlock.id
            pathConditionSize = parentPathConditionSize + state.forkPoints.depth
            instructionsVisitedInCurrentBlock = 0
        }
        instructionsVisitedInCurrentBlock++
        stepWhenMovedLastTime = steps

        updateVertexCounts()
        updateBlock(stepWhenMovedLastTime)
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