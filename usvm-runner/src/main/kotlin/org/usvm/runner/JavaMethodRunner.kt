package org.usvm.runner

import org.usvm.CoverageZone
import org.usvm.PathSelectionStrategy
import org.usvm.PathSelectorCombinationStrategy
import org.usvm.UMachineOptions
import org.usvm.machine.JcMachine
import org.usvm.machine.state.JcState
import org.usvm.runner.util.JacoDBContainer
import org.usvm.runner.util.getJcMethodByName
import org.usvm.runner.util.loadClasspathFromJar
import org.usvm.runner.util.mapsKey
import kotlin.time.Duration

class JavaMethodRunner(val options: UMachineOptions, classpath: String) {
    val jacodbCpKey: String
        get() = mapsKey

    val cp by lazy {
        val classpath = loadClasspathFromJar(classpath)
        JacoDBContainer(jacodbCpKey, classpath).cp
    }

    fun cover(methodFullName: String): Pair<List<JcState>, Float> {
        val (className, methodName) = extractClassAndMethod(methodFullName)
        return cover(className, methodName)
    }

    fun cover(className: String, methodName: String): Pair<List<JcState>, Float> {
        val jcMethod = cp.getJcMethodByName(className, methodName)

        val (states, percentageCoverage) = JcMachine(cp, options, interpreterObserver = null).use { machine ->
            machine.analyze(jcMethod.method, targets = emptyList())
        }
        return states to percentageCoverage
    }

    private fun extractClassAndMethod(fullName: String): Pair<String, String> {
        val parts = fullName.split('.')
        val className = parts.dropLast(1).joinToString(".")
        val methodName = parts.last()

        return Pair(className, methodName)
    }
}

val defaultOptions = UMachineOptions(
    pathSelectionStrategies = listOf(PathSelectionStrategy.AI),
    pathSelectorCombinationStrategy = PathSelectorCombinationStrategy.SEQUENTIAL,
    coverageZone = CoverageZone.METHOD,
    exceptionsPropagation = true,
    solverTimeout = Duration.INFINITE,
    timeout = Duration.INFINITE,
    typeOperationsTimeout = Duration.INFINITE,
    useSolverForForks = false,
    stepLimit = 2000U
)