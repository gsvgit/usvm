package org.usvm.util

sealed interface Predictor<G> {
    fun predictState(game: G): UInt
}

interface OnnxModel<G>: Predictor<G>
interface Oracle<G>: Predictor<G>
