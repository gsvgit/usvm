package org.usvm.machine

import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcBlockGraph
import org.jacodb.api.cfg.JcGraph
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcThrowInst
import org.jacodb.api.ext.cfg.callExpr
import org.usvm.statistics.BlockGraph
import org.usvm.util.originalInst

class JcBlockGraph : BlockGraph<JcMethod, JcBlock, JcInst> {
    private val basicBlocks = mutableSetOf<JcBlock>()
    override val blocks: Set<JcBlock>
        get() = basicBlocks.toSet()

    private val methodsCache = mutableMapOf<JcMethod, JcGraph>()

    private val predecessorMap = mutableMapOf<JcBlock, MutableSet<JcBlock>>()
    private val successorMap = mutableMapOf<JcBlock, MutableSet<JcBlock>>()
    private val calleesMap = mutableMapOf<JcBlock, MutableSet<JcBlock>>()
    private val callersMap = mutableMapOf<JcBlock, MutableSet<JcBlock>>()
    private val returnMap = mutableMapOf<JcBlock, MutableSet<JcBlock>>()
    private val catchersMap = mutableMapOf<JcBlock, MutableSet<JcBlock>>()
    private val throwersMap = mutableMapOf<JcBlock, MutableSet<JcBlock>>()

    private fun Set<JcBlock>.blockOf(stmt: JcInst): JcBlock {
        // workaround stepping through instructions
        val targetStmt = when (stmt) {
            is JcMethodEntrypointInst, is JcConcreteMethodCallInst -> stmt.originalInst()
            else -> stmt
        }

        return find { it.contains(targetStmt) }
            ?: throw IllegalArgumentException("$stmt does not belong to any block")
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
        calleesMap.getOrPut(blockOf(callSite), ::mutableSetOf) += blockOf(entryPoint)
        callersMap.getOrPut(blockOf(entryPoint), ::mutableSetOf) += blockOf(callSite)
        methodsCache[method]?.let {
            it.exits.forEach {
                returnMap.getOrPut(blockOf(it), ::mutableSetOf) += blockOf(returnSite)
            }
        }
    }

    private fun createBlock(blockGraph: JcBlockGraph, stmts: MutableList<JcInst>): JcBlock {
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
        stmts.clear()
        return block
    }

    private fun cutBlockGraph(blockGraph: JcBlockGraph) {
        val blocks = blockGraph.iterator()
        val newBlocks = mutableSetOf<JcBlock>()
        blocks.forEach { block ->
            val stmts = blockGraph.instructions(block)
            val currentBlockStmts = mutableListOf<JcInst>()
            stmts.forEach { stmt ->
                currentBlockStmts += stmt
                stmt.callExpr?.method?.method?.let {
                    newBlocks += createBlock(blockGraph, currentBlockStmts)
                }
            }
            if (currentBlockStmts.isNotEmpty()) {
                newBlocks += createBlock(blockGraph, currentBlockStmts)
            }
        }
        basicBlocks += newBlocks
        newBlocks.forEach { block ->
            predecessorMap.getOrPut(block, ::mutableSetOf) += blockGraph.jcGraph.predecessors(block.start)
                .map { newBlocks.blockOf(it) }
            successorMap.getOrPut(block, ::mutableSetOf) += blockGraph.jcGraph.successors(block.end)
                .map { newBlocks.blockOf(it) }
            catchersMap.getOrPut(block, ::mutableSetOf) += blockGraph.jcGraph.catchers(block.start)
                .map { basicBlocks.blockOf(it) }
                .also {
                    for (catcher in it) {
                        throwersMap.getOrPut(catcher, ::mutableSetOf) += block
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
        basicBlocks.blockOf(inst)

    override fun statementsOf(node: JcBlock): Sequence<JcInst> =
        node.stmts.asSequence()
}