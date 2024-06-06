package org.usvm.ps

import org.usvm.UPathSelector

class SequentialPathSelector<State>(
    private val selectors: List<UPathSelector<State>>,
    private val stepsToSwitch: UInt
): UPathSelector<State> {
    init {
        require(selectors.size >= 2) { "Cannot create sequential path selector from less than 2 selectors" }
    }

    private var currentSelector = selectors.first()
    private var totalSteps = 0u

    override fun isEmpty() = currentSelector.isEmpty() && selectors.all { it.isEmpty() }

    override fun peek(): State {
        if (totalSteps == stepsToSwitch) {
            selectors.removeFirst()
            currentSelector = selectors.first()
        }
        totalSteps++
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
