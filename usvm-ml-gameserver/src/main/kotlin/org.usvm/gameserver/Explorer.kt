package org.usvm.gameserver

import org.usvm.runner.JavaMethodRunner
import org.usvm.OracleImpl
import org.usvm.PathSelectionStrategy
import org.usvm.UMachineOptions
import org.usvm.runner.defaultOptions
import org.usvm.statistics.BasicBlock
import kotlin.math.floor

fun randomExplorer(
    inputBody: Start,
    getNextStep: () -> Step,
    sendOutputMessageBody: (outputMessageBody: OutputMessageBody) -> Unit,
): GameOver {
    val gameMap = inputBody.gameMap

    val predict = { gameState: GameState ->
        val msg = ReadyForNextStep(gameState)
        sendOutputMessageBody(msg)
        val step = getNextStep()
        sendOutputMessageBody(MoveReward(Reward(MoveRewardData(4u, 4u), 5u)))
        step.gameStep.stateId
    }

    val options = cloneDefaultOptions(gameMap, predict)
    val runner = JavaMethodRunner(options, gameMap.assemblyFullName)

    val (results, percentageCoverage) = runner.cover(gameMap.nameOfObjectToCover)
    val errors = results.count { it.isExceptional }
    val tests = results.size - errors

    return GameOver(
        floor(percentageCoverage).toUInt(),
        test = tests.toUInt(),
        error = errors.toUInt()
    )
}

private fun cloneDefaultOptions(gameMap: GameMap, predict: (GameState) -> UInt): UMachineOptions {
    val defaultSearcher = when (gameMap.defaultSearcher) {
        Searcher.BFS -> PathSelectionStrategy.BFS
        Searcher.DFS -> PathSelectionStrategy.DFS
    }
    val stepLimit = (gameMap.stepsToStart + gameMap.stepsToStart).toULong()
    return defaultOptions.copy(
        pathSelectionStrategies = listOf(defaultSearcher, PathSelectionStrategy.AI),
        stepLimit = stepLimit,
        stepsToStart = gameMap.stepsToStart,
        oracle = OracleImpl<BasicBlock>(predict)
    )
}