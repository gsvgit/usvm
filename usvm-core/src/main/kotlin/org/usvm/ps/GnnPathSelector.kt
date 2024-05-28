package org.usvm.ps

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.usvm.StateId
import org.usvm.UPathSelector
import org.usvm.UState
import org.usvm.statistics.BasicBlock
import org.usvm.statistics.BlockGraph
import java.nio.FloatBuffer
import java.nio.LongBuffer

private data class StateHistoryElement(
    val blockId: Int,
    var numOfVisits: Int = 0,
    var stepWhenVisitedLastTime: Int = 0
)

class GnnPathSelector<Statement, Method, State, Block>(
    private val blockGraph: BlockGraph<Method, Block, Statement>,
    private val isInCoverageZone: (Block) -> Boolean
    ) : UPathSelector<State> where
      State : UState<*, Method, Statement, *, *, State>, Block : BasicBlock {

    private val statesMap = mutableMapOf<State, StateWrapper>()
    private var lastPeekedState: State? = null
    private var totalSteps = 0

    private val path = "model.onnx"
    private val oracle = Oracle(path)

    override fun isEmpty() = statesMap.isEmpty()

    override fun peek(): State {
        totalSteps++
        if (statesMap.size == 1) {
            return statesMap.keys.single().also { lastPeekedState = it }
        }
        val wrappers = statesMap.values
        val vertices = blockGraph.blocks
        val predictedId = oracle.predictState(vertices, wrappers)

        return checkNotNull(statesMap.keys.find { it.id == predictedId }).also { lastPeekedState = it }
    }

    override fun remove(state: State) {
        statesMap.remove(state)?.let { wrapper ->
            // remove parent edge
            val parent = statesMap.values.find { wrapper in it.children }
            parent?.children?.remove(wrapper)

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

    private inner class Oracle(
        pathToONNX: String,
    ) {
        private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
        private val session: OrtSession = env.createSession(pathToONNX)

        fun predictState(vertices: Collection<Block>, stateWrappers: Collection<StateWrapper>): StateId {
            val stateIds = mutableMapOf<StateId, Int>()
            val input = generateInput(vertices, stateWrappers, stateIds)
            val output = session.run(input)

            val predictedStatesRanks =
                (output["out"].get().value as Array<*>).map { (it as FloatArray).toList() }

            return checkNotNull(getPredictedState(predictedStatesRanks, stateIds))
        }

        private fun generateInput(
            vertices: Collection<Block>,
            stateWrappers: Collection<StateWrapper>,
            stateIds: MutableMap<StateId, Int>
        ): Map<String, OnnxTensor> {
            val vertexIds = mutableMapOf<Int, Int>()
            val gameVertices = tensorFromBasicBlocks(vertices, vertexIds)
            val states = tensorFromStates(stateWrappers, stateIds)
            val (vertexToVertexEdgesIndex, vertexToVertexEdgesAttributes) = tensorFromVertexEdges(
                vertices,
                vertexIds
            )
            val (parentOfEdges, historyEdgesIndexVertexToState, historyEdgesAttributes) = tensorFromStateEdges(
                stateWrappers,
                stateIds,
                vertexIds
            )
            val vertexToState = tensorFromStatePositions(stateWrappers, stateIds, vertexIds)

            return mapOf(
                "game_vertex" to gameVertices,
                "state_vertex" to states,
                "gamevertex_to_gamevertex_index" to vertexToVertexEdgesIndex,
                "gamevertex_to_gamevertex_type" to vertexToVertexEdgesAttributes,
                "gamevertex_history_statevertex_index" to historyEdgesIndexVertexToState,
                "gamevertex_history_statevertex_attrs" to historyEdgesAttributes,
                "gamevertex_in_statevertex" to vertexToState,
                "statevertex_parentof_statevertex" to parentOfEdges
            )
        }

        private fun getPredictedState(stateRank: List<List<Float>>, stateIds: Map<StateId, Int>): StateId? {
            return stateRank
                .mapIndexed { index, ranks -> stateIds.entries.find { it.value == index }?.key to ranks.sum() }
                .maxBy { it.second }.first
        }

        private fun tensorFromBasicBlocks(vertices: Collection<Block>, vertexIds: MutableMap<Int, Int>): OnnxTensor {
            val numOfVertexAttributes = 7
            val verticesArray = vertices.flatMapIndexed { i, vertex ->
                vertexIds[vertex.id] = i
                vertex.inCoverageZone = isInCoverageZone(vertex)
                listOf(
                    vertex.inCoverageZone.toFloat(),
                    vertex.basicBlockSize.toFloat(),
                    vertex.coveredByTest.toFloat(),
                    vertex.visitedByState.toFloat(),
                    vertex.touchedByState.toFloat(),
                    vertex.containsCall.toFloat(),
                    vertex.containsThrow.toFloat()
                )
            }.toFloatArray()

            return createFloatTensor(verticesArray, vertices.size, numOfVertexAttributes)
        }

        private fun tensorFromStates(states: Collection<StateWrapper>, stateIds: MutableMap<StateId, Int>): OnnxTensor {
            val numOfStateAttributes = 7
            val statesArray = states.flatMapIndexed { i, state ->
                stateIds[state.id] = i
                listOf(
                    state.position,
                    state.pathConditionSize,
                    state.visitedAgainVertices,
                    state.visitedNotCoveredVerticesInZone,
                    state.visitedNotCoveredVerticesOutOfZone,
                    state.stepWhenMovedLastTime,
                    state.instructionsVisitedInCurrentBlock
                ).map { it.toFloat() }
            }.toFloatArray()

            return createFloatTensor(statesArray, states.size, numOfStateAttributes)
        }

        private fun tensorFromVertexEdges(
            blocks: Collection<Block>,
            vertexIds: MutableMap<Int, Int>
        ): Pair<OnnxTensor, OnnxTensor> {
            val vertexFrom = mutableListOf<Long>()
            val vertexTo = mutableListOf<Long>()
            val attributes = mutableListOf<Long>()
            var edgeId = 0L

            blocks.forEach { block ->
                blockGraph.successors(block).forEach { successor ->
                    vertexFrom.add(vertexIds[block.id]?.toLong() ?: -1)
                    vertexTo.add(vertexIds[successor.id]?.toLong() ?: -1)
                    attributes.add(edgeId++)
                }
            }

            val indexList = (vertexFrom + vertexTo).toLongArray()

            return createLongTensor(indexList, 2, vertexFrom.size) to createLongTensor(
                attributes.toLongArray(),
                attributes.size
            )
        }

        private fun tensorFromStateEdges(
            states: Collection<StateWrapper>,
            stateIds: MutableMap<StateId, Int>,
            vertexIds: MutableMap<Int, Int>
        ): Triple<OnnxTensor, OnnxTensor, OnnxTensor> {
            val numOfParentOfEdges = states.sumOf { it.children.size }
            val numOfHistoryEdges = states.sumOf { it.history.size }
            val numOfHistoryEdgeAttributes = 2

            val parentOf = LongArray(2 * numOfParentOfEdges)
            val historyIndexVertexToState = LongArray(2 * numOfHistoryEdges)
            val historyAttributes = LongArray(numOfHistoryEdges * numOfHistoryEdgeAttributes)

            states.forEach { state ->
                state.children.forEachIndexed { i, child ->
                    parentOf[i] = stateIds[state.id]?.toLong() ?: -1
                    parentOf[numOfParentOfEdges + i] = stateIds[child.id]?.toLong() ?: -1
                }

                var historyIndex = 0
                var historyAttrsIndex = 0
                state.history.forEach { (_, historyElem) ->
                    historyIndexVertexToState[historyIndex] = vertexIds[historyElem.blockId]?.toLong() ?: -1
                    historyIndexVertexToState[numOfHistoryEdges + historyIndex] = stateIds[state.id]?.toLong() ?: -1
                    historyAttributes[historyAttrsIndex] = historyElem.numOfVisits.toLong()
                    historyAttributes[historyAttrsIndex + 1] = historyElem.stepWhenVisitedLastTime.toLong()
                    historyAttrsIndex += numOfHistoryEdgeAttributes
                    historyIndex++
                }
            }

            val parentTensor = createLongTensor(parentOf, 2, numOfParentOfEdges)
            val historyIndexTensor =
                createLongTensor(historyIndexVertexToState, 2, numOfHistoryEdges)
            val historyAttributesTensor = createLongTensor(
                historyAttributes,
                numOfHistoryEdges,
                numOfHistoryEdgeAttributes
            )
            return Triple(parentTensor, historyIndexTensor, historyAttributesTensor)
        }

        private fun tensorFromStatePositions(
            states: Collection<StateWrapper>,
            stateIds: MutableMap<StateId, Int>,
            vertexIds: MutableMap<Int, Int>
        ): OnnxTensor {
            val totalStates = states.size
            val vertexToState = LongArray(2 * totalStates)
            states.forEachIndexed { i, state ->
                val stateIndex = stateIds[state.id]
                val vertexIndex = vertexIds[state.position]?.toLong() ?: -1

                vertexToState[i] = vertexIndex
                if (stateIndex != null) {
                    vertexToState[stateIndex + i] = stateIndex.toLong()
                }
            }
            return createLongTensor(vertexToState, 2, totalStates)
        }

        private fun createFloatTensor(data: FloatArray, vararg numbers: Int): OnnxTensor {
            val shape = createShape(*numbers)
            val buffer = FloatBuffer.wrap(data)
            return OnnxTensor.createTensor(env, buffer, shape)
        }

        private fun createLongTensor(data: LongArray, vararg numbers: Int): OnnxTensor {
            val shape = createShape(*numbers)
            val buffer = LongBuffer.wrap(data)
            return OnnxTensor.createTensor(env, buffer, shape)
        }

        private fun createShape(vararg numbers: Int) = numbers.map { it.toLong() }.toLongArray()

        private fun Boolean.toFloat() = if (this) 1f else 0f
    }
}
