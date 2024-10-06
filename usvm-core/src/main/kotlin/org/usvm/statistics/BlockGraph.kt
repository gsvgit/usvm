package org.usvm.statistics

interface BlockGraph<Method, Block, Statement> {
    val blocks: List<Block>
    val newBlocks: List<Block>

    fun predecessors(block: Block): Sequence<Block>
    fun successors(block: Block): Sequence<Block>

    fun callees(block: Block): Sequence<Block>
    fun callers(block: Block): Sequence<Block>

    fun returnOf(block: Block): Sequence<Block>


    fun methodOf(block: Block): Method
    fun blockOf(inst: Statement): Block
    fun statementsOf(block: Block): Sequence<Statement>
}
