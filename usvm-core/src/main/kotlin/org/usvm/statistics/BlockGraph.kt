package org.usvm.statistics

interface BlockGraph<Block, Statement> {
    fun predecessors(node: Block): Sequence<Block>
    fun successors(node: Block): Sequence<Block>

    fun callees(node: Block): Sequence<Block>
    fun callers(node: Block): Sequence<Block>

    fun returnOf(node: Block): Sequence<Block>

    fun blockOf(inst: Statement): Block
    fun statementsOf(node: Block): Sequence<Statement>
}
