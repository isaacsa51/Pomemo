package com.serranoie.app.pomemo.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * View-based haptics, trimmed from Essentials' HapticUtil (MIT, sameerasw.com): same names and
 * constants, without its in-app toggle and "stronger" custom-vibration mode. performHapticFeedback
 * already honours the system "touch feedback" setting, so there is nothing else to gate on.
 */
object HapticUtil {
    /** Standard tap: buttons, chips, opening things. */
    fun performUIHaptic(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    /** Light tick: steppers, day toggles, page settling. */
    fun performLightHaptic(view: View) {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) HapticFeedbackConstants.SEGMENT_TICK
            else HapticFeedbackConstants.CLOCK_TICK
        )
    }

    /** Navigation / primary keys: tabs, FAB. */
    fun performVirtualKeyHaptic(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    /** Something committed: save, start, switch on. */
    fun performConfirmHaptic(view: View) {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
            else HapticFeedbackConstants.CONTEXT_CLICK
        )
    }

    /** Something removed or stopped: delete, reset, dismiss. */
    fun performRejectHaptic(view: View) {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT
            else HapticFeedbackConstants.KEYBOARD_TAP
        )
    }
}
