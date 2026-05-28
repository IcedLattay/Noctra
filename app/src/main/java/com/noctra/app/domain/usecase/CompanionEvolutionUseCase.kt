package com.noctra.app.domain.usecase

import kotlin.math.min

class CompanionEvolutionUseCase {

    data class EvolutionState(
        val stageName: String,
        val stageLevel: Int,
        val currentXp: Int,
        val nextMilestoneXp: Int,
        val progressPercent: Float,
        val totalXp: Int
    )

    fun execute(totalXp: Int): EvolutionState {
        return when {
            totalXp < 1500 -> {
                val progress = totalXp.toFloat() / 1500f
                EvolutionState("The Depleted", 1, totalXp, 1500, progress, totalXp)
            }
            totalXp < 5000 -> {
                val stageXp = totalXp - 1500
                val progress = stageXp.toFloat() / (5000f - 1500f)
                EvolutionState("The Awakening", 2, stageXp, 3500, progress, totalXp)
            }
            totalXp < 15000 -> {
                val stageXp = totalXp - 5000
                val progress = stageXp.toFloat() / (15000f - 5000f)
                EvolutionState("The Charged", 3, stageXp, 10000, progress, totalXp)
            }
            totalXp < 50000 -> {
                val stageXp = totalXp - 15000
                val progress = stageXp.toFloat() / (50000f - 15000f)
                EvolutionState("The Peak Overdrive", 4, stageXp, 35000, progress, totalXp)
            }
            else -> {
                EvolutionState("The Zen Master", 5, totalXp - 50000, 0, 1.0f, totalXp)
            }
        }
    }
}
