/*
 * Copyright 2026, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Redistribution and use in source and/or binary forms, with or without
 * modification, must retain the above copyright notice and the following
 * disclaimer.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.spine.chords.client

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.StatusRuntimeException
import io.spine.chords.client.DataObservationStatus.Active
import io.spine.chords.client.DataObservationStatus.Cancelled
import io.spine.chords.client.DataObservationStatus.Failed
import io.spine.chords.client.DataObservationStatus.Refreshing
import io.spine.chords.client.DataObservationStatus.WaitingForConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * A cancellable handle of an underlying server subscription.
 */
internal fun interface ObservationSubscription {

    /**
     * Cancels the underlying subscription.
     */
    fun cancel()
}

/**
 * An observation managed by the client's automatic recovery coordinator.
 */
internal interface RecoverableDataObservation {

    /**
     * Tells whether this observation should be refreshed after reconnecting.
     */
    val needsRecovery: Boolean

    /**
     * Suspends this observation until the client reconnects.
     */
    fun waitForConnection()

    /**
     * Re-reads the observation after reconnecting.
     */
    suspend fun refresh()

    /**
     * Cancels this observation.
     */
    fun cancel()

    /**
     * Marks this observation as closed without cancelling its server subscription.
     *
     * The owning client terminates the connection, which ends all remaining
     * subscription streams. Avoiding an individual cancellation here prevents
     * duplicate cancellation requests during client shutdown.
     */
    fun close()
}

/**
 * A data observation maintained by [DesktopClient].
 *
 * @param T The type of the complete observed value.
 * @param U The type of an individual update.
 */
@Suppress(
    "TooManyFunctions", /* All functions belong to the observation lifecycle. */
    "TooGenericExceptionCaught" /* All recoverable request failures become status values. */
)
internal class DataObservationImpl<T, U>(
    initialValue: T,
    private val read: () -> T,
    private val subscribe: (
        onUpdate: (U) -> Unit,
        onError: (Throwable) -> Unit
    ) -> ObservationSubscription,
    private val applyUpdate: (T, U) -> T,
    private val connectionStatus: () -> ConnectionStatus,
    private val onCancelled: (RecoverableDataObservation) -> Unit,
    private val onRecoveryNeeded: (RecoverableDataObservation) -> Unit = {}
) : DataObservation<T>, RecoverableDataObservation {

    private val mutableValue: MutableState<T> = mutableStateOf(initialValue)
    private val mutableStatus: MutableState<DataObservationStatus> =
        mutableStateOf(Refreshing)
    private val stateLock = Any()
    private val refreshMutex = Mutex()

    private var generation: Long = 0
    private var subscription: ObservationSubscription? = null
    private val abandonedSubscriptionGenerations = mutableSetOf<Long>()
    private var cancelled: Boolean = false

    override val value: T
        get() = mutableValue.value

    override val status: State<DataObservationStatus>
        get() = mutableStatus

    /**
     * Performs the initial read and subscription synchronously.
     */
    internal fun initialize() {
        runBlocking {
            refresh()
        }
    }

    @Suppress(
        "ReturnCount" /* Each failed or stale recovery phase must stop immediately. */
    )
    override suspend fun refresh() {
        refreshMutex.withLock {
            val refreshGeneration = beginRefresh() ?: return
            val pendingUpdates = PendingUpdates<U>()
            try {
                val result = withContext(IO) {
                    performRefresh(refreshGeneration, pendingUpdates)
                }
                when (result) {
                    is RefreshResult.Success -> {
                        if (!installSubscription(
                                refreshGeneration,
                                result.subscription
                            )
                        ) {
                            return
                        }
                        completeRefresh(
                            refreshGeneration,
                            result.value,
                            pendingUpdates
                        )
                    }
                    is RefreshResult.ReadFailed -> {
                        if (!installSubscription(
                                refreshGeneration,
                                result.subscription
                            )
                        ) {
                            return
                        }
                        handleFailure(
                            refreshGeneration,
                            result.cause,
                            cancelConnectedSubscription = true
                        )
                    }
                    is RefreshResult.SubscriptionFailed -> {
                        result.value?.let {
                            setValue(refreshGeneration, it.value)
                        }
                        handleFailure(refreshGeneration, result.cause)
                    }
                }
            } finally {
                synchronized(stateLock) {
                    abandonedSubscriptionGenerations.remove(refreshGeneration)
                }
            }
        }
    }

    @Suppress(
        "ReturnCount" /* Each failed network phase returns its distinct result immediately. */
    )
    private fun performRefresh(
        refreshGeneration: Long,
        pendingUpdates: PendingUpdates<U>
    ): RefreshResult<T> {
        val newSubscription = try {
            subscribe(
                { update ->
                    bufferOrApplyUpdate(
                        refreshGeneration,
                        pendingUpdates,
                        update
                    )
                },
                { error ->
                    bufferOrHandleFailure(
                        refreshGeneration,
                        pendingUpdates,
                        error
                    )
                }
            )
        } catch (e: Exception) {
            rethrowCancellation(e)
            return RefreshResult.SubscriptionFailed(
                e,
                readAfterSubscriptionFailure(e)
            )
        }
        val refreshedValue = try {
            read()
        } catch (e: Exception) {
            rethrowCancellation(e)
            return RefreshResult.ReadFailed(newSubscription, e)
        }
        return RefreshResult.Success(newSubscription, refreshedValue)
    }

    override fun cancel() {
        cancel(cancelSubscription = true)
    }

    override fun close() {
        cancel(cancelSubscription = false)
    }

    private fun cancel(cancelSubscription: Boolean) {
        val previousSubscription: ObservationSubscription?
        val notifyCancelled: Boolean
        synchronized(stateLock) {
            notifyCancelled = !cancelled
            if (!notifyCancelled) {
                return
            }
            cancelled = true
            generation++
            previousSubscription = subscription
            subscription = null
            mutableStatus.value = Cancelled
        }
        if (cancelSubscription) {
            previousSubscription.cancelSafely()
        }
        onCancelled(this)
    }

    /**
     * Suspends this observation until the client reconnects.
     */
    override fun waitForConnection() {
        synchronized(stateLock) {
            if (cancelled || mutableStatus.value is Failed) {
                return
            }
            if (subscription == null && mutableStatus.value == Refreshing) {
                abandonedSubscriptionGenerations.add(generation)
            }
            generation++
            subscription = null
            mutableStatus.value = WaitingForConnection
        }
    }

    /**
     * Tells whether this observation should be refreshed after reconnecting.
     */
    override val needsRecovery: Boolean
        get() = synchronized(stateLock) {
            !cancelled && mutableStatus.value == WaitingForConnection
        }

    private fun beginRefresh(): Long? {
        val previousSubscription: ObservationSubscription?
        val refreshGeneration: Long
        synchronized(stateLock) {
            if (cancelled) {
                return null
            }
            generation++
            refreshGeneration = generation
            previousSubscription = subscription
            subscription = null
            mutableStatus.value = Refreshing
        }
        previousSubscription.cancelSafely()
        return refreshGeneration
    }

    private fun setValue(refreshGeneration: Long, newValue: T): Boolean {
        synchronized(stateLock) {
            if (!isCurrent(refreshGeneration)) {
                return false
            }
            mutableValue.value = newValue
            return true
        }
    }

    private fun bufferOrApplyUpdate(
        refreshGeneration: Long,
        pendingUpdates: PendingUpdates<U>,
        update: U
    ) {
        synchronized(stateLock) {
            if (!isCurrent(refreshGeneration)) {
                return
            }
            if (pendingUpdates.buffering) {
                pendingUpdates.updates.add(update)
            } else {
                mutableValue.value = applyUpdate(mutableValue.value, update)
            }
        }
    }

    private fun bufferOrHandleFailure(
        refreshGeneration: Long,
        pendingUpdates: PendingUpdates<U>,
        cause: Throwable
    ) {
        val handleImmediately = synchronized(stateLock) {
            if (!isCurrent(refreshGeneration)) {
                return
            }
            if (pendingUpdates.buffering) {
                if (pendingUpdates.failure == null) {
                    pendingUpdates.failure = cause
                }
                false
            } else {
                true
            }
        }
        if (handleImmediately) {
            handleFailure(refreshGeneration, cause)
        }
    }

    private fun installSubscription(
        refreshGeneration: Long,
        newSubscription: ObservationSubscription
    ): Boolean {
        val installed: Boolean
        val shouldCancel: Boolean
        synchronized(stateLock) {
            installed = isCurrent(refreshGeneration)
            shouldCancel = !installed &&
                    !abandonedSubscriptionGenerations.remove(refreshGeneration)
            if (installed) {
                subscription = newSubscription
            }
        }
        if (shouldCancel) {
            newSubscription.cancelSafely()
        }
        return installed
    }

    private fun completeRefresh(
        refreshGeneration: Long,
        refreshedValue: T,
        pendingUpdates: PendingUpdates<U>
    ) {
        val pendingFailure: Throwable?
        synchronized(stateLock) {
            if (!isCurrent(refreshGeneration)) {
                return
            }
            var value = refreshedValue
            pendingUpdates.updates.forEach {
                value = applyUpdate(value, it)
            }
            pendingUpdates.updates.clear()
            pendingUpdates.buffering = false
            mutableValue.value = value
            pendingFailure = pendingUpdates.failure
            if (pendingFailure == null) {
                mutableStatus.value = Active
            }
        }
        if (pendingFailure != null) {
            handleFailure(refreshGeneration, pendingFailure)
        }
    }

    private fun readAfterSubscriptionFailure(
        subscriptionFailure: Exception
    ): ReadValue<T>? {
        if (isConnectionFailure(subscriptionFailure)) {
            return null
        }
        return try {
            ReadValue(read())
        } catch (e: Exception) {
            rethrowCancellation(e)
            null
        }
    }

    private fun handleFailure(
        refreshGeneration: Long,
        cause: Throwable,
        cancelConnectedSubscription: Boolean = false
    ) {
        val connectionFailure = isConnectionFailure(cause)
        val connected = connectionStatus() == ConnectionStatus.CONNECTED
        val previousSubscription: ObservationSubscription?
        synchronized(stateLock) {
            if (!isCurrent(refreshGeneration)) {
                return
            }
            generation++
            previousSubscription = subscription
            subscription = null
            mutableStatus.value = if (connectionFailure) {
                WaitingForConnection
            } else {
                Failed(cause)
            }
        }
        if (!connectionFailure ||
            (cancelConnectedSubscription && connected)
        ) {
            previousSubscription.cancelSafely()
        }
        if (connectionFailure && connected) {
            onRecoveryNeeded(this)
        }
    }

    private fun isCurrent(refreshGeneration: Long): Boolean =
        !cancelled && generation == refreshGeneration

    private fun isConnectionFailure(cause: Throwable): Boolean {
        return when (Status.fromThrowable(cause).code) {
            Status.Code.CANCELLED,
            Status.Code.DEADLINE_EXCEEDED,
            Status.Code.UNAVAILABLE -> true
            Status.Code.UNKNOWN -> cause.hasGrpcStatus() &&
                    connectionStatus() != ConnectionStatus.CONNECTED
            else -> false
        }
    }
}

private fun Throwable.hasGrpcStatus(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current is StatusException || current is StatusRuntimeException) {
            return true
        }
        current = current.cause
    }
    return false
}

private class PendingUpdates<U> {
    val updates: MutableList<U> = mutableListOf()
    var buffering: Boolean = true
    var failure: Throwable? = null
}

private sealed class RefreshResult<out T> {

    data class Success<T>(
        val subscription: ObservationSubscription,
        val value: T
    ) : RefreshResult<T>()

    data class ReadFailed(
        val subscription: ObservationSubscription,
        val cause: Exception
    ) : RefreshResult<Nothing>()

    data class SubscriptionFailed<T>(
        val cause: Exception,
        val value: ReadValue<T>?
    ) : RefreshResult<T>()
}

private data class ReadValue<out T>(val value: T)

private fun rethrowCancellation(exception: Exception) {
    if (exception is CancellationException) {
        throw exception
    }
}

private fun ObservationSubscription?.cancelSafely() {
    try {
        this?.cancel()
    } catch (_: Exception) {
        // Cancellation is best-effort because the stream or channel may already
        // have failed independently.
    }
}
