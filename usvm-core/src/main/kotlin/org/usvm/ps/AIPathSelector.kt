package org.usvm.ps
import org.usvm.UPathSelector
import org.usvm.UState
import org.usvm.statistics.BasicBlock
import org.usvm.statistics.BlockGraph
import org.usvm.statistics.StepsStatistics
import org.usvm.util.Predictor
import org.usvm.utils.Game
import org.usvm.utils.StateWrapper


class AIPathSelector<Statement, State, Block>(
    private val isInCoverageZone: (Block) -> Boolean,
    private val blockGraph: BlockGraph<*, Block, Statement>,
    private val stepsStatistics: StepsStatistics<*, State>,
    private val predictor: Predictor<Game<Block>>,
) : UPathSelector<State> where
State : UState<*, *, Statement , *, *, State>,
Block : BasicBlock {
    private val statesMap = mutableMapOf<State, StateWrapper<Statement, State, Block>>()
    private var lastPeekedState: State? = null
    private val totalSteps
        get() = stepsStatistics.totalSteps.toInt()

    private fun predict(): State {
        val wrappers = statesMap.values
        val vertices = blockGraph.blocks
        vertices.forEach{
            it.inCoverageZone = isInCoverageZone(it)
        }
        val predictedId = predictor.predictState(Game(vertices, wrappers, blockGraph))
        val predictedState = statesMap.keys.find { it.id == predictedId }

        return checkNotNull(predictedState)
    }

    override fun isEmpty() = statesMap.isEmpty()

    override fun peek(): State {
        if (statesMap.size == 1) {
            return statesMap.keys.single().also { lastPeekedState = it }
        }

        val predictedState = predict()
        lastPeekedState = predictedState

        return predictedState
    }

    override fun remove(state: State) {
        statesMap.remove(state)?.let { wrapper ->
            // remove parent edge
            val parent = statesMap.values.find { wrapper in it.children }
            parent?.children?.remove(wrapper)

            state.pathNode += state.currentStatement
            wrapper.updateBlock(totalSteps)
            wrapper.children.clear()
        }
    }

    override fun add(states: Collection<State>) {
        // is null iff we are adding initial state
        val lastPeekedStateWrapper = statesMap[lastPeekedState]
        val parentPathConditionSize = lastPeekedStateWrapper?.pathConditionSize ?: 0
        val parentHistory = lastPeekedStateWrapper?.history ?: mutableMapOf()
        val wrappers = states.map { state ->
            val wrapper = StateWrapper(
                state,
                parentPathConditionSize,
                parentHistory,
                blockGraph,
                totalSteps
            )
            //
            statesMap[state] = wrapper
            wrapper
        }
        lastPeekedStateWrapper?.addChildren(wrappers)
    }

    override fun update(state: State) {
        statesMap[state]?.update(totalSteps)
    }
}
