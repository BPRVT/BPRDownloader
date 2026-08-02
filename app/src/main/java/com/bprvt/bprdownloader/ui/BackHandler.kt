package com.bprvt.bprdownloader.ui

/** Implemented by fragments that want first refusal on the remote's Back button. */
interface BackHandler {
    /** Return true if the press was consumed. */
    fun onBackPressedInFragment(): Boolean
}
