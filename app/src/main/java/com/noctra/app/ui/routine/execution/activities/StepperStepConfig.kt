package com.noctra.app.ui.routine.execution.activities

/**
 * One exercise in a Stepper-shaped activity (Progressive Muscle Relaxation,
 * Bedtime Stretching).
 *
 * Session 5 (R13): each exercise runs these phases in order:
 *   INSTRUCTION -> PREPARATION (optional, 0 = skipped) -> EXECUTION -> REST
 *   PMR:        5s instruction, no prep, 5s execution, 10s release
 *   Stretching: 5–15s instruction, 3s prep, 60s execution, 15s break
 *
 * Not part of the DB schema — held in a local per-label lookup map inside
 * GenericTimerActivityFragment.
 */
data class StepperStepConfig(
    val stepId: String,
    val name: String,                    // e.g. "Hands", "Neck Rolls"
    val instruction: String,             // shown during INSTRUCTION (and kept on screen during EXECUTION)
    val imageAssets: List<String>,       // drawable names, played in this order during EXECUTION.
    // 1 item = static image. 2+ items = loops.
    val instructionDurationSeconds: Int, // INSTRUCTION phase length
    val prepDurationSeconds: Int,        // PREPARATION phase length (0 = no prep phase)
    val actionDurationSeconds: Int,      // EXECUTION phase length
    val restDurationSeconds: Int,        // REST / release / break length
    val frameDurationsMs: List<Long> = emptyList(),
    // how long each image in imageAssets stays on screen
    // during INSTRUCTION, same order/length as imageAssets.
    // Empty = 1000ms each.
    val executionCues: List<Pair<Int, String>> = emptyList()
    // Session 5 (R15): optional on-screen text during EXECUTION,
    // as (seconds, text) in order, e.g.
    // [(25,"Hold Left"), (10,"Rest"), (25,"Hold Right")].
    // Empty = title stays the exercise name.
)