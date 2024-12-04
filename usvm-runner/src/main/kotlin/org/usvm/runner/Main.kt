package org.usvm.runner

import ch.qos.logback.classic.Level
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import org.slf4j.LoggerFactory
import org.usvm.statistics.BasicBlock
import org.usvm.utils.Mode
import org.usvm.utils.OnnxModelImpl


fun main(args: Array<String>) {
    val parser = ArgParser("usvm.Runner")
    val model by parser.option(
        ArgType.String,
        fullName = "model",
        shortName = "m",
        description = "Path to ONNX model."
    ).required()

    val path by parser.option(
        ArgType.String,
        fullName = "classpath",
        shortName = "cp",
        description = "Path to Java classpath."
    ).required()

    val mode by parser.option(
        ArgType.Choice<Mode>(),
        fullName = "mode",
        description = "Mode to run inference in. Could be either CPU or GPU, the default is former."
    ).default(Mode.CPU)

    val methodFullName by parser.option(
        ArgType.String,
        fullName = "method",
        description = "Full name of method to cover including package and class name."
    ).required()

    parser.parse(args)


    val runner = JavaMethodRunner(
        defaultOptions.copy(oracle = OnnxModelImpl<BasicBlock>(model, mode)),
        classpath = path
    )

    // setLogLevel("ROOT", Level.DEBUG)


    runner.cover(methodFullName)
}

// evil logger hack
@Suppress("unused")
private fun setLogLevel(loggerName: String, level: Level) {
    val loggerContext = LoggerFactory.getILoggerFactory() as ch.qos.logback.classic.LoggerContext
    val logger = loggerContext.getLogger(loggerName)
    logger.level = level
}

