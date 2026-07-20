package com.noctra.app.ui.routine.execution.activities

import kotlinx.serialization.Serializable

/**
 * One sub-step in a Stepper-shaped activity (Progressive Muscle Relaxation,
 * Bedtime Stretching). Each step has an "action" phase (tense / pose) shown
 * in green, followed by a "rest" phase (release / rest) shown in the default
 * timer color.
 *
 * Passed to StepperActivityFragment as a JSON string via Safe Args, since
 * Safe Args doesn't support List<T> args directly without Parcelable.
 */
@Serializable
data class StepperStepConfig(
    val stepId: String,
    val name: String,                 // e.g. "Hands", "Neck Rolls"
    val instruction: String,          // e.g. "Make a fist with your hands as tight as possible for 5 seconds"
    val imageAsset: String,           // drawable resource name, e.g. "ic_pmr_hands" (placeholder until real demo assets/animations land)
    val actionDurationSeconds: Int,   // e.g. 5 (PMR tense) or 3 (stretch pose)
    val restDurationSeconds: Int      // e.g. 10 (PMR release) or 15 (stretch rest)
)