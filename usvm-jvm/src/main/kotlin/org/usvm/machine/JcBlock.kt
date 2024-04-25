package org.usvm.machine

import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcInstRef

data class JcBlock(
    val start: JcInstRef,
    val end: JcInstRef,
    val containsCall: Boolean,
    val containsThrow: Boolean,
    val method: JcMethod,
    val stmts: List<JcInst>
) {
    val size: Int get() = end.index - start.index + 1

    fun contains(inst: JcInst): Boolean {
        return inst in stmts
    }

    fun contains(inst: JcInstRef): Boolean {
        return inst.index <= end.index && inst.index >= start.index
    }

}