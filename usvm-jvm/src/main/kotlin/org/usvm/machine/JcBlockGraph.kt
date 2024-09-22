package org.usvm.machine

import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcBlockGraph
import org.jacodb.api.jvm.cfg.JcGraph
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.cfg.JcThrowInst
import org.jacodb.api.jvm.ext.cfg.callExpr
import org.usvm.statistics.BlockGraph
import org.usvm.util.originalInst

class JcBlockGraph : BlockGraph<JcMethod, JcBlock, JcInst> {
    private val methodToBlocks = mutableMapOf<JcMethod, List<JcBlock>>()
    override val blocks: List<JcBlock>
        get() = methodToBlocks.values.flatten()
    override var newBlocks = mutableListOf<JcBlock>()
        private set

    private val methodsCache = mutableMapOf<JcMethod, JcGraph>()

    private val predecessorMap = mutableMapOf<JcBlock, MutableList<JcBlock>>()
    private val successorMap = mutableMapOf<JcBlock, MutableList<JcBlock>>()
    private val calleesMap = mutableMapOf<JcBlock, MutableList<JcBlock>>()
    private val callersMap = mutableMapOf<JcBlock, MutableList<JcBlock>>()
    private val returnMap = mutableMapOf<JcBlock, MutableList<JcBlock>>()
    private val catchersMap = mutableMapOf<JcBlock, MutableList<JcBlock>>()
    private val throwersMap = mutableMapOf<JcBlock, MutableList<JcBlock>>()

    private fun List<JcBlock>.blockOf(stmt: JcInst): JcBlock {
        // workaround stepping through instructions
        val targetStmt = stmt.originalInst()
        val targetMethod = targetStmt.location.method
        val blocksToSearch = requireNotNull(methodToBlocks[targetMethod])

        var left = 0
        var right = blocksToSearch.size - 1

        while (left <= right) {
            val mid = left + (right - left) / 2
            val block = blocksToSearch[mid]

            val instructionIndex = targetStmt.location.index
            when {
                instructionIndex < block.start.index -> right = mid - 1
                instructionIndex > block.end.index -> left = mid + 1
                else -> return block
            }
        }
        throw NoSuchElementException("$stmt does not belong to any block")
    }

    fun addNewMethod(method: JcMethod) {
        if (method !in methodsCache) {
            val flowGraph = method.flowGraph()
            methodsCache[method] = flowGraph
            cutBlockGraph(flowGraph.blockGraph())
        }
    }

    fun addNewMethodCall(callSite: JcInst, returnSite: JcInst, entryPoint: JcInst) {
        val method = entryPoint.location.method
        addNewMethod(method)
        calleesMap.getOrPut(blockOf(callSite), ::mutableListOf) += blockOf(entryPoint)
        callersMap.getOrPut(blockOf(entryPoint), ::mutableListOf) += blockOf(callSite)
        methodsCache[method]?.let {
            it.exits.forEach {
                returnMap.getOrPut(blockOf(it), ::mutableListOf) += blockOf(returnSite)
            }
        }
    }

    private fun createAndAddBlock(blockGraph: JcBlockGraph, stmts: MutableList<JcInst>, newBlocks: MutableList<JcBlock>) {
        val block = with(blockGraph.jcGraph) {
            val start = stmts.first()
            val end = stmts.last()
            JcBlock(
                start = ref(start),
                end = ref(end),
                containsCall = end.callExpr != null,
                containsThrow = end is JcThrowInst,
                method = method,
                stmts = stmts.toList()
            )
        }
        newBlocks += block
        stmts.clear()
    }

    private fun cutBlockGraph(blockGraph: JcBlockGraph) {
        val starterBlocks = blockGraph.iterator()
        newBlocks = mutableListOf()
        starterBlocks.forEach { block ->
            val stmts = blockGraph.instructions(block)
            val currentBlockStmts = mutableListOf<JcInst>()
            stmts.forEach { stmt ->
                currentBlockStmts += stmt
                stmt.callExpr?.method?.method?.let {
                    createAndAddBlock(blockGraph, currentBlockStmts, newBlocks)
                }
            }
            if (currentBlockStmts.isNotEmpty()) {
                createAndAddBlock(blockGraph, currentBlockStmts, newBlocks)
            }
        }
        val graph = blockGraph.jcGraph
        methodToBlocks[graph.method] = newBlocks

        newBlocks.forEach { block ->
            predecessorMap.getOrPut(block, ::mutableListOf) += graph.predecessors(block.start)
                .map { newBlocks.blockOf(it) }
            successorMap.getOrPut(block, ::mutableListOf) += graph.successors(block.end)
                .map { newBlocks.blockOf(it) }
            catchersMap.getOrPut(block, ::mutableListOf) += graph.catchers(block.start)
                .map { blocks.blockOf(it) }
                .also {
                    for (catcher in it) {
                        throwersMap.getOrPut(catcher, ::mutableListOf) += block
                    }
                }
        }
    }

    override fun predecessors(node: JcBlock): Sequence<JcBlock> =
        predecessorMap.getOrDefault(node, emptySet()).asSequence()

    override fun successors(node: JcBlock): Sequence<JcBlock> =
        successorMap.getOrDefault(node, emptySet()).asSequence()

    override fun callees(node: JcBlock): Sequence<JcBlock> =
        calleesMap.getOrDefault(node, emptySet()).asSequence()

    override fun callers(node: JcBlock): Sequence<JcBlock> =
        calleesMap.getOrDefault(node, emptySet()).asSequence()

    override fun returnOf(node: JcBlock): Sequence<JcBlock> =
        returnMap.getOrDefault(node, emptySet()).asSequence()

    override fun methodOf(block: JcBlock): JcMethod =
        block.method

    override fun blockOf(inst: JcInst): JcBlock =
        blocks.blockOf(inst)

    override fun statementsOf(node: JcBlock): Sequence<JcInst> =
        node.stmts.asSequence()
}