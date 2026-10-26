package com.bitchat.android.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * "Something is loading" signal for the beating cat in the header: messages arriving from the
 * relays, Tor starting or building a new route. Each received event keeps it on for a short while.
 */
object NetActivity {
    private const val HOLD_MS = 1_200L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var off: Job? = null

    private val _receiving = MutableStateFlow(false)
    val receiving: StateFlow<Boolean> = _receiving.asStateFlow()

    /** Called for every event received from a relay; cheap when already on. */
    fun pulse() {
        if (!_receiving.value) _receiving.value = true
        off?.cancel()
        off = scope.launch {
            delay(HOLD_MS)
            _receiving.value = false
        }
    }
}
