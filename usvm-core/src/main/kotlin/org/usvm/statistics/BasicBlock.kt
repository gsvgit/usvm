package org.usvm.statistics

interface BasicBlock {
    val id: Int
    val inCoverageZone: Boolean
    val basicBlockSize: Int
    var coveredByTest: Boolean
    var visitedByState: Boolean
    var touchedByState: Boolean
    val containsCall: Boolean
    val containsThrow: Boolean
}
