package org.usvm.statistics

import org.usvm.StateId

interface BasicBlock {
    val id: Int
    var inCoverageZone: Boolean
    val basicBlockSize: Int
    var coveredByTest: Boolean
    var visitedByState: Boolean
    var touchedByState: Boolean
    val containsCall: Boolean
    val containsThrow: Boolean

    val states: MutableList<StateId>
}
