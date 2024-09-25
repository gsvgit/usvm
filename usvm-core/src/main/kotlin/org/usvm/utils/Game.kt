package org.usvm.utils

import org.usvm.statistics.BasicBlock
import org.usvm.statistics.BlockGraph


data class Game<Block : BasicBlock>(
    val vertices: Collection<Block>,
    val stateWrappers: Collection<StateWrapper<*, *, *>>,
    val blockGraph: BlockGraph<*, Block, *>
)