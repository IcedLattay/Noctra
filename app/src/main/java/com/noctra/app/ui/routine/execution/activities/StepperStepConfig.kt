package com.noctra.app.ui.routine.execution.activities

/**
 * One sub-step in a Stepper-shaped activity (Progressive Muscle Relaxation,
 * Bedtime Stretching). Each step has an "action" phase (tense / pose) shown
 * in green, followed by a "rest" phase (release / rest) shown in the default
 * timer color.
 *
 * Not part of the DB schema — held in a local per-label lookup map inside
 * GenericTimerActivityFragment, same pattern as its existing
 * completionMessageFor()-style lookups.
 */
data class StepperStepConfig(
    val stepId: String,
    val name: String,                    // e.g. "Hands", "Neck Rolls"
    val instruction: String,             // e.g. "Make a fist with your hands as tight as possible for 5 seconds"
    val imageAssets: List<String>,       // drawable resource names, one per reference frame. Length varies per
    // sub-activity (1, 2, 3, or 4 confirmed so far) — NOT a fixed count.
    // A single-item list displays statically; 2+ items cycle on a 1s
    // interval during the action phase only (see
    // GenericTimerActivityFragment.startFrameLoop()).
    val actionDurationSeconds: Int,      // e.g. 5 (PMR tense) or 3 (stretch pose)
    val restDurationSeconds: Int         // e.g. 10 (PMR release) or 15 (stretch rest)
)