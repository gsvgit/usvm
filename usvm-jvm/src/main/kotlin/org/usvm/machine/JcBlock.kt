package org.usvm.machine

import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.cfg.JcInstRef
import org.usvm.statistics.BasicBlock

data class JcBlock(
    val start: JcInstRef,
    val end: JcInstRef,
    override val containsCall: Boolean,
    override val containsThrow: Boolean,
    val method: JcMethod,
    val stmts: List<JcInst>
): BasicBlock {
    companion object {
        private var currentId = 0
    }

    override val basicBlockSize: Int get() = end.index - start.index + 1
    override val id = currentId++
    override var visitedByState = false
    override var touchedByState = false
    override var inCoverageZone = false
    override var coveredByTest = false

    fun contains(inst: JcInst): Boolean {
        return inst in stmts
    }

    fun contains(inst: JcInstRef): Boolean {
        return inst.index <= end.index && inst.index >= start.index
    }

}