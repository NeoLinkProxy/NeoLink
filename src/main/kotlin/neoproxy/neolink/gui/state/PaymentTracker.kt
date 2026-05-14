package neoproxy.neolink.gui.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Coordinates payment countdown and polling as one cancellable unit.
 *
 * Keeping both jobs here prevents stale order polling from mutating the current dialog after
 * the user starts a new order, closes a terminal dialog, or exits the desktop app.
 */
internal class PaymentTracker(
    private val scope: CoroutineScope,
    private val totalSeconds: Int,
    private val isStillCurrent: (String) -> Boolean,
    private val isSuccessful: (String) -> Boolean,
    private val updateCountdown: suspend (String, Int) -> Unit,
    private val pollStatus: suspend (String, Any?) -> Boolean,
    private val onSuccess: suspend (String, Int) -> Unit
) {
    private var countdownJob: Job? = null
    private var pollJob: Job? = null

    fun start(orderId: String, requestContext: Any? = null) {
        cancel()
        countdownJob = scope.launch(Dispatchers.Default) {
            var secondsLeft = totalSeconds
            while (secondsLeft >= 0) {
                if (!isStillCurrent(orderId) || isSuccessful(orderId)) {
                    return@launch
                }
                updateCountdown(orderId, secondsLeft)
                delay(1_000)
                secondsLeft--
            }
        }
        pollJob = scope.launch(Dispatchers.IO) {
            var secondsLeft = totalSeconds
            while (secondsLeft >= -30) {
                if (!isStillCurrent(orderId)) {
                    return@launch
                }
                if (pollStatus(orderId, requestContext)) {
                    countdownJob?.cancel()
                    countdownJob = null
                    onSuccess(orderId, secondsLeft.coerceAtLeast(0))
                    return@launch
                }
                delay(2_000)
                secondsLeft -= 2
            }
        }
    }

    fun cancel() {
        countdownJob?.cancel()
        pollJob?.cancel()
        countdownJob = null
        pollJob = null
    }
}
