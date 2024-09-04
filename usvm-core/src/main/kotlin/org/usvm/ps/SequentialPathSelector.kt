package org.usvm.ps

import org.usvm.UPathSelector
import org.usvm.statistics.StepsStatistics

class SequentialPathSelector<State>(
    private val selectors: List<UPathSelector<State>>,
    private val stepsToSwitch: UInt,
    private val stepsStatistics: StepsStatistics<*, *>
): UPathSelector<State> {
    init {
        require(selectors.size >= 2) { "Cannot create sequential path selector from less than 2 selectors" }
    }

    private var ptr = 0

    private var currentSelector = selectors[ptr]
    private val totalSteps
        get() = stepsStatistics.totalSteps.toUInt()

    override fun isEmpty() = currentSelector.isEmpty() && selectors.all { it.isEmpty() }

    override fun peek(): State {
        // currently only either 1 or 2 selectors are supported
        if (totalSteps == stepsToSwitch) {
            ptr = (ptr + 1) % selectors.size
            currentSelector = selectors[ptr]
        }
        return currentSelector.peek()
    }

    override fun update(state: State) {
        selectors.forEach { it.update(state) }
    }

    override fun add(states: Collection<State>) {
        selectors.forEach { it.add(states) }
    }

    override fun remove(state: State) {
        selectors.forEach { it.remove(state) }
    }
}
