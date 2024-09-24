package org.usvm.ps
import org.usvm.UPathSelector
import org.usvm.UState
import org.usvm.statistics.BasicBlock
import org.usvm.statistics.BlockGraph
import org.usvm.statistics.StepsStatistics
import org.usvm.util.OnnxModel
import org.usvm.util.Oracle
import org.usvm.util.Predictor
import org.usvm.utils.Game
import org.usvm.utils.StateWrapper
import org.usvm.utils.isSat


class AIPathSelector<Statement, State, Block>(
    private val blockGraph: BlockGraph<*, Block, Statement>,
    private val stepsStatistics: StepsStatistics<*, State>,
    private val predictor: Predictor<Game<Block>>,
) : UPathSelector<State> where
State : UState<*, *, Statement, *, *, State>,
Block : BasicBlock {
    private val statesMap = mutableMapOf<State, StateWrapper<Statement, State, Block>>()
    private var lastPeekedState: State? = null
    private val totalSteps
        get() = stepsStatistics.totalSteps.toInt()

    // set is convenient since we don't have to worry about duplicates
    private var touchedStates = mutableSetOf<StateWrapper<Statement, State, Block>>()
    private var touchedBlocks = mutableSetOf<Block>()
    private var newBlocks = listOf<Block>()

    private fun predict(): State {
        val wrappers = statesMap.values
        val vertices = blockGraph.blocks
        val game = buildGame(vertices, wrappers)
        val predictedId = predictor.predictState(game)
        val predictedState = statesMap.keys.find { it.id == predictedId }

        return checkNotNull(predictedState)
    }

    private fun buildGame(
        vertices: List<Block>,
        wrappers: MutableCollection<StateWrapper<Statement, State, Block>>
    ): Game<Block> {
        val game = when (predictor) {
            is OnnxModel<Game<Block>> -> Game(vertices, wrappers, blockGraph)
            is Oracle<Game<Block>> -> {
                // if we played with default searcher before
                // client has no information about the game
                if (lastPeekedState == null) {
                    Game(vertices, wrappers, blockGraph)
                } else {
                    if (blockGraph.newBlocks != newBlocks) {
                        touchedBlocks.addAll(blockGraph.newBlocks)
                        newBlocks = blockGraph.newBlocks.toList()
                    }
                    if (touchedStates.isEmpty()) touchedStates.addAll(wrappers)
                    val delta = Game(touchedBlocks.toList(), touchedStates.toList(), blockGraph)
                    delta
                }
            }
        }
        touchedStates.clear()
        touchedBlocks.clear()

        return game
    }

    override fun isEmpty() = statesMap.isEmpty()

    override fun peek(): State {
        if (totalSteps == 0) {
            return statesMap.keys.single().also { lastPeekedState = it }
        }

        val predictedState = predict()
        lastPeekedState = predictedState
        val wrapper = checkNotNull(statesMap[lastPeekedState])
        touchedBlocks.add(wrapper.currentBlock)
        touchedStates.add(wrapper)

        return predictedState
    }

    override fun remove(state: State) {
        statesMap.remove(state)?.let { wrapper ->
            // remove parent edge
            val parent = statesMap.values.find { wrapper in it.children }
            if (parent != null) {
                parent.children.remove(wrapper)
                // removing parent edge on client side
                touchedStates.add(parent)
            }

            state.pathNode += state.currentStatement

            wrapper.update(totalSteps)
            wrapper.children.clear()
            wrapper.currentBlock.states.remove(wrapper.id)
            if (state.isSat())
                touchedBlocks.addAll(wrapper.history.keys)

            touchedStates.remove(wrapper)
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
                blockGraph
            )
            //
            statesMap[state] = wrapper
            wrapper
        }
        touchedStates.addAll(wrappers)
        lastPeekedStateWrapper?.addChildren(wrappers)
    }

    override fun update(state: State) {
        val wrapper = statesMap[state]
        requireNotNull(wrapper)
        wrapper.update(totalSteps)
        touchedBlocks.add(wrapper.currentBlock)
    }
}
