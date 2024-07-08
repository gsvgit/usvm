@file:Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE")

package org.usvm.gameserver

import org.usvm.JavaMethodRunner
import org.usvm.OracleImpl
import org.usvm.statistics.BasicBlock

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

    val (className, methodName) = extractClassAndMethod(gameMap.nameOfObjectToCover)
    val runner = JavaMethodRunner(gameMap, OracleImpl<BasicBlock>(predict))

    val (results, percentageCoverage) = runner.cover(className, methodName)
    val errors = results.count { it.isExceptional }
    val tests = results.size - errors

    return GameOver(
        percentageCoverage.toUByte(),
        test = tests.toUInt(),
        error = errors.toUInt()
    )
}

private fun extractClassAndMethod(fullName: String): Pair<String, String> {
    val parts = fullName.split('.')
    val className = parts.dropLast(1).joinToString(".")
    val methodName = parts.last()

    return Pair(className, methodName)
}