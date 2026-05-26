package com.noctra.app.ui.routine.execution.activities

/**
 * ActivityCompletionListener
 *
 * Shared interface implemented by RoutineExecutionFragment.
 * All activity fragments (Breathing, Audioscapes, Journaling, Generic)
 * call onActivityComplete() when their timer ends so the parent can
 * advance to the next step or show the completion overlay.
 *
 * Kept in its own file so no activity fragment depends on another.
 */
interface ActivityCompletionListener {
    fun onActivityComplete()
}