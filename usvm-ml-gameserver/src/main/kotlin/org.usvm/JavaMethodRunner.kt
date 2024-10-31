package org.usvm

import org.usvm.gameserver.GameMap
import org.usvm.gameserver.Searcher.BFS
import org.usvm.gameserver.Searcher.DFS
import org.usvm.machine.JcMachine
import org.usvm.machine.state.JcState
import org.usvm.util.JacoDBContainer
import org.usvm.util.Predictor
import org.usvm.util.getJcMethodByName
import org.usvm.util.loadClasspathFromJar
import org.usvm.util.mapsKey
import java.io.File
import kotlin.time.Duration

class JavaMethodRunner(gameMap: GameMap, oracle: Predictor<*>? = null) {
    val jacodbCpKey: String
        get() = mapsKey

    val classpath: List<File> =
        loadClasspathFromJar(gameMap.assemblyFullName)

    val cp by lazy {
        JacoDBContainer(jacodbCpKey, classpath).cp
    }

    val stepLimit = gameMap.stepsToPlay + gameMap.stepsToStart

    val defaultSearcher = when (gameMap.defaultSearcher) {
        BFS -> PathSelectionStrategy.BFS
        DFS -> PathSelectionStrategy.DFS
    }

    val options: UMachineOptions = UMachineOptions(
        pathSelectionStrategies = listOf(defaultSearcher, PathSelectionStrategy.AI),
        pathSelectorCombinationStrategy = PathSelectorCombinationStrategy.SEQUENTIAL,
        coverageZone = CoverageZone.METHOD,
        exceptionsPropagation = true,
        solverTimeout = Duration.INFINITE,
        timeout = Duration.INFINITE,
        typeOperationsTimeout = Duration.INFINITE,
        useSolverForForks = false,

        stepLimit = stepLimit.toULong(),
        stepsToStart = gameMap.stepsToStart,
        oracle = oracle
    )

    fun cover(className: String, methodName: String): Pair<List<JcState>, Float> {
        val jcMethod = cp.getJcMethodByName(className, methodName)

        val (states, percentageCoverage) = JcMachine(cp, options, interpreterObserver = null).use { machine ->
            machine.analyze(jcMethod.method, targets = emptyList())
        }
        return states to percentageCoverage
    }
}
