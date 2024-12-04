package org.usvm.runner.util

import io.github.rchowell.dotlin.DotNodeShape
import io.github.rchowell.dotlin.digraph
import org.usvm.statistics.BasicBlock
import org.usvm.utils.Game


@Suppress("UNUSED")
fun<Block: BasicBlock> Game<Block>.dotGraph(
    historyEdges: Boolean = false,
    parentEdges: Boolean = true,
    colorNonCoverageZone: Boolean = true,
    blockProperties: Boolean = true,
    stateProperties: Boolean = true
): String {
    val groupedByMethod = vertices.groupBy { blockGraph.methodOf(it) }
    return digraph(name = "game_map", strict = true) {
        node {
            style = "rounded,filled"
            shape = DotNodeShape.RECTANGLE
        }

        // Subgraph for each method
        groupedByMethod.entries.forEachIndexed { i, (method, blocks) ->
            +subgraph("cluster_$i") {
                label = method.toString()
                color = "#${Integer.toHexString(method.hashCode()).substring(0, 6)}"
                blocks.forEach { block ->
                    val blockId = block.id.toString()
                    +blockId + {
                        if (blockProperties) {
                            val blockLabel = "Block ID: ${block.id}\\n" +
                                    "Size: ${block.basicBlockSize}\\n" +
                                    "Coverage Zone: ${block.inCoverageZone}\\n" +
                                    "Covered By Test: ${block.coveredByTest}\\n" +
                                    "Visited By State: ${block.visitedByState}\\n" +
                                    "Touched By State: ${block.touchedByState}\\n" +
                                    "Contains Call: ${block.containsCall}\\n" +
                                    "Contains Throw: ${block.containsThrow}"
                            label = blockLabel
                        }

                        color = if (colorNonCoverageZone || block.inCoverageZone) {
                            when {
                                block.coveredByTest -> "forestgreen"
                                block.visitedByState -> "yellow2"
                                block.touchedByState -> "darkorange3"
                                else -> "orangered4"
                            }
                        } else {
                            "gray"
                        }
                    }
                    blockGraph.successors(block).forEach { successor ->
                        val successorId = successor.id.toString()
                        blockId - successorId + {
                            color = "blueviolet"
                        }
                    }
                }
            }
        }

        // Call and return edges
        vertices.forEach { block ->
            val blockId = block.id.toString()
            blockGraph.callees(block).forEach { callee ->
                val calleeId = callee.id.toString()
                blockId - calleeId + {
                    label = "call"
                    color = "cyan3"
                }
            }
            blockGraph.returnOf(block).forEach { returnSite ->
                val returnId = returnSite.id.toString()
                blockId - returnId + {
                    label = "return"
                    color = "aquamarine4"
                }
            }
        }

        // States and their edges
        stateWrappers.forEach { state ->
            val stateId = "\"State ${state.id}\""
            +stateId + {
                if (stateProperties) {
                    val stateLabel = "State ID: ${state.id}\\n" +
                            "Visited Not Covered In Zone: ${state.visitedNotCoveredVerticesInZone}\\n" +
                            "Visited Not Covered Out Of Zone: ${state.visitedNotCoveredVerticesOutOfZone}\\n" +
                            "Position: ${state.position}\\n" +
                            "Path Condition Size: ${state.pathConditionSize}\\n" +
                            "Visited Again Vertices: ${state.visitedAgainVertices}\\n" +
                            "Instructions Visited In Current Block: ${state.instructionsVisitedInCurrentBlock}\\n" +
                            "Step When Moved Last Time: ${state.stepWhenMovedLastTime}"
                    label = stateLabel
                }
                shape = DotNodeShape.DIAMOND
                color = "coral3"
            }
            if (historyEdges) {
                state.history.values.forEach { stateHistoryElement ->
                    val blockId = stateHistoryElement.blockId.toString()
                    stateId - blockId + {
                        label = "history(${stateHistoryElement.numOfVisits})"
                    }
                }
            }
            if (parentEdges) {
                state.children.forEach { child ->
                    val childId = "\"State ${child.id}\""
                    stateId - childId + {
                        label = "parent of"
                    }
                }
            }
            stateId - state.position.toString() + { label = "position" }
        }
    }.dot()
}