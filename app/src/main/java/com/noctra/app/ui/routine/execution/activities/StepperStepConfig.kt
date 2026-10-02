package com.noctra.app.ui.routine.execution.activities

/**
 * One sub-step in a Stepper-shaped activity (Progressive Muscle Relaxation,
 * Bedtime Stretching). Each step has an "action" phase (tense / pose) shown
 * in green, followed by a "rest" phase (release / rest) shown in the default
 * timer color. During rest, a pause icon is shown instead of the images.
 *
 * Not part of the DB schema — held in a local per-label lookup map inside
 * GenericTimerActivityFragment.
 */
data class StepperStepConfig(
    val stepId: String,
    val name: String,                    // e.g. "Hands", "Neck Rolls"
    val instruction: String,             // e.g. "Make a fist with your hands as tight as possible for 5 seconds"
    val imageAssets: List<String>,       // drawable names, played in this order during the action phase.
    // 1 item = static image. 2+ items = loops.
    val actionDurationSeconds: Int,      // pose / tense length
    val restDurationSeconds: Int,        // rest / release length
    val frameDurationsMs: List<Long> = emptyList()
    // Session 5 (R8): how long each image in imageAssets stays on
    // screen, same order/length as imageAssets. Empty = 1000ms each.
)