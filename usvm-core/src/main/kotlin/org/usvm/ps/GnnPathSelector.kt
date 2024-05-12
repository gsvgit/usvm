package org.usvm.ps

import org.usvm.UPathSelector
import org.usvm.UState
import org.usvm.statistics.BasicBlock
import org.usvm.statistics.BlockGraph

private data class StateHistoryElement(
    val blockId: Int,
    var numOfVisits: Int = 0,
    var stepWhenVisitedLastTime: Int = 0
)

class GnnPathSelector<Statement, Method, State, Block>(
    val blockGraph: BlockGraph<Block, Statement>
) : UPathSelector<State> where
      State : UState<*, Method, Statement, *, *, State>, Block : BasicBlock {

    private val statesMap = mutableMapOf<State, StateWrapper>()
    private var lastPeekedState: State? = null
    private var totalSteps = 0

    override fun isEmpty() = statesMap.isEmpty()

    override fun peek(): State {
        totalSteps++
        return statesMap.keys.first().also { lastPeekedState = it }
    }

    override fun remove(state: State) {
        statesMap.remove(state)?.let { wrapper ->
            state.pathNode += state.currentStatement
            wrapper.updateBlock()
            wrapper.children.clear()
        }
    }

    override fun add(states: Collection<State>) {
        // is null iff we are adding initial state
        val wrapper = statesMap[lastPeekedState]
        val parentPathConditionSize = wrapper?.pathConditionSize ?: 0
        states.forEach {
            statesMap[it] = StateWrapper(it, parentPathConditionSize)
        }
        wrapper?.addChildren(states)
    }

    override fun update(state: State) {
        statesMap[state]?.update()
    }

    private inner class StateWrapper(
        val state: State,
        val parentPathConditionSize: Int
    ) {
        val children = mutableSetOf<StateWrapper>()
        val history = mutableMapOf<Block, StateHistoryElement>()
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

        var stepWhenMovedLastTime = totalSteps

        val instructionsVisitedInCurrentBlock: Int
            get() = blockGraph.statementsOf(currentBlock).indexOf(visitedStatement) + 1

        val currentBlock: Block
            get() = blockGraph.blockOf(visitedStatement)

        fun update() {
            stepWhenMovedLastTime = totalSteps
            updateBlock()
        }

        fun updateBlock() {
            history.getOrPut(currentBlock) { StateHistoryElement(position) }.apply {
                val statements = blockGraph.statementsOf(currentBlock)
                if (statements.first() == visitedStatement) {
                    numOfVisits++
                    currentBlock.touchedByState = true
                }
                if (statements.last() == visitedStatement) {
                    currentBlock.visitedByState = true
                    stepWhenVisitedLastTime = totalSteps
                }
            }
        }

        fun addChildren(states: Collection<State>) {
            val wrappers = states.mapNotNull { statesMap[it] }
            children.addAll(wrappers)
        }
    }
}
