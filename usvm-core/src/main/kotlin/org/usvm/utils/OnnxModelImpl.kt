package org.usvm.utils

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.usvm.StateId
import org.usvm.logger
import org.usvm.statistics.BasicBlock
import org.usvm.statistics.BlockGraph
import org.usvm.util.OnnxModel
import java.nio.FloatBuffer
import java.nio.LongBuffer

enum class Mode {
    CPU,
    GPU
}

class OnnxModelImpl<Block: BasicBlock>(
    pathToONNX: String,
    mode: Mode
): OnnxModel<Game<Block>> {
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val sessionOptions: OrtSession.SessionOptions = OrtSession.SessionOptions().apply {
        if (mode == Mode.GPU) {
            addCUDA()
        } else {
            logger.info("Using CPU execution provider.")
        }
    }
    private val session: OrtSession = env.createSession(pathToONNX, sessionOptions)

    override fun predictState(game: Game<Block>): UInt {
        val stateIds = mutableMapOf<StateId, Int>()
        val input = generateInput(game, stateIds)
        val output = session.run(input)

        val predictedStatesRanks =
            (output["out"].get().value as Array<*>).map { (it as FloatArray).toList() }

        return checkNotNull(getPredictedState(predictedStatesRanks, stateIds))
    }

    private fun generateInput(
        game: Game<Block>,
        stateIds: MutableMap<StateId, Int>
    ): Map<String, OnnxTensor> {
        val (vertices, stateWrappers, blockGraph) = game
        val vertexIds = mutableMapOf<Int, Int>()
        val gameVertices = tensorFromBasicBlocks(vertices, vertexIds)
        val states = tensorFromStates(stateWrappers, stateIds)
        val (vertexToVertexEdgesIndex, vertexToVertexEdgesAttributes) = tensorFromVertexEdges(
            vertices,
            blockGraph,
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

    private fun tensorFromStates(states: Collection<StateWrapper<*, *, *>>, stateIds: MutableMap<StateId, Int>): OnnxTensor {
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
        blockGraph: BlockGraph<*, Block, *>,
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
        states: Collection<StateWrapper<*, *, *>>,
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
        states: Collection<StateWrapper<*, *, *>>,
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
