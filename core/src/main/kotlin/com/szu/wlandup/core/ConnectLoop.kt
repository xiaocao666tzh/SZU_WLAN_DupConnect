package com.szu.wlandup.core

/**
 * Retry loop used by the app UI. Reloads credentials every iteration so a
 * mid-loop delete cannot keep using a closed-over snapshot. Honors an external
 * stop latch without clearing it.
 */
class ConnectLoop(
    private val session: ConnectSession,
    private val loadCredentials: () -> Credentials?,
    private val shouldStop: () -> Boolean,
) {
    fun run(onResult: (ConnectResult) -> Unit = {}): ConnectLoopExit {
        while (!shouldStop()) {
            val creds = loadCredentials() ?: return ConnectLoopExit.CredentialsGone
            val result = session.runOnce(creds)
            onResult(result)
            when (result) {
                is ConnectResult.Success -> return ConnectLoopExit.Succeeded(result)
                is ConnectResult.Failure -> {
                    if (shouldStop()) return ConnectLoopExit.Stopped
                }
            }
        }
        return ConnectLoopExit.Stopped
    }
}

sealed class ConnectLoopExit {
    data class Succeeded(val result: ConnectResult.Success) : ConnectLoopExit()
    data object Stopped : ConnectLoopExit()
    data object CredentialsGone : ConnectLoopExit()
}
