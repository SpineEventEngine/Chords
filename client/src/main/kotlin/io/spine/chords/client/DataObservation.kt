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
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers.IO
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
 * Maintains live server data and recovers it after a temporary connection
 * failure.
 *
 * A `DataObservation` is also a Compose [State], so its current [value] can be
 * read directly or with Kotlin property delegation. A newly created observation
 * is readable right away: it carries its initial or default value until the
 * first read from the server completes. An observation in
 * [DataObservationStatus.WaitingForConnection] automatically re-reads the
 * complete value and creates a new subscription when the server connection is
 * restored. An observation in [DataObservationStatus.Failed] is not retried
 * automatically and requires an explicit [refresh] call.
 *
 * The caller owns the observation lifecycle and should call [cancel] when the
 * observation is no longer needed. Instances are created by [Client]; their
 * server access and client lifecycle remain internal to the library.
 *
 * @param T The type of the observed value.
 */
@Suppress(
    "TooManyFunctions", /* All functions belong to the observation lifecycle. */
    "TooGenericExceptionCaught" /* All recoverable request failures become status values. */
)
public class DataObservation<out T> internal constructor(
    /**
     * The value exposed until the first successful server read completes.
     */
    initialValue: T,
    /**
     * Reads the complete current value from the server.
     */
    private val read: () -> T,
    /**
     * Creates a server subscription and registers its update and failure callbacks.
     */
    private val subscribe: (
        onUpdate: ((T) -> T) -> Unit,
        onError: (Throwable) -> Unit
    ) -> ObservationSubscription,
    /**
     * Obtains the latest client connection status.
     */
    private val connectionStatus: () -> ConnectionStatus,
    /**
     * Runs the blocking subscription and read operations.
     */
    private val requestContext: CoroutineContext,
    /**
     * Unregisters this observation after permanent cancellation.
     */
    private val onCancelled: (DataObservation<*>) -> Unit,
    /**
     * Requests a delayed retry when a stream fails on a connected channel.
     */
    private val onRecoveryNeeded: (DataObservation<*>) -> Unit = {}
) : State<T> {

    /**
     * Stores the complete value exposed as Compose state.
     */
    private val mutableValue: MutableState<T> = mutableStateOf(initialValue)

    /**
     * Stores the observation lifecycle status exposed as Compose state.
     */
    private val mutableStatus: MutableState<DataObservationStatus> =
        mutableStateOf(Refreshing)

    /**
     * Guards lifecycle fields shared by request and callback threads.
     */
    private val stateLock = Any()

    /**
     * Prevents concurrent refresh operations for this observation.
     */
    private val refreshMutex = Mutex()

    /**
     * Identifies the current lifecycle generation so stale callbacks can be ignored.
     */
    private var generation: Long = 0

    /**
     * Holds the currently installed server subscription, if any.
     */
    private var subscription: ObservationSubscription? = null

    /**
     * Tracks in-flight subscriptions that became obsolete because the connection failed.
     */
    private val abandonedSubscriptionGenerations = mutableSetOf<Long>()

    /**
     * The generation of the refresh that is currently creating a subscription,
     * or `null` when no subscription is being created.
     *
     * Accessing this field requires holding [stateLock].
     */
    private var subscribingGeneration: Long? = null

    /**
     * Permanently prevents further refreshes after cancellation.
     */
    private var cancelled: Boolean = false

    public override val value: T
        get() = mutableValue.value

    /**
     * The current observation status.
     */
    public val status: State<DataObservationStatus>
        get() = mutableStatus

    /**
     * Re-reads the complete value and replaces the current subscription.
     *
     * Request failures are exposed through [status] instead of being thrown
     * from this function. Coroutine cancellation is propagated to the caller
     * without being converted into an observation failure.
     */
    @Suppress(
        "ReturnCount" /* Each failed or stale recovery phase must stop immediately. */
    )
    public suspend fun refresh() {
        refreshMutex.withLock {
            val refreshGeneration = beginRefresh() ?: return
            val pendingUpdates = PendingUpdates<T>()
            try {
                val result = withContext(requestContext) {
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
                    finishSubscribing(refreshGeneration)
                    abandonedSubscriptionGenerations.remove(refreshGeneration)
                }
            }
        }
    }

    /**
     * Performs the blocking subscription and read operations for one refresh.
     */
    @Suppress(
        "ReturnCount" /* Each failed network phase returns its distinct result immediately. */
    )
    private fun performRefresh(
        refreshGeneration: Long,
        pendingUpdates: PendingUpdates<T>
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

    /**
     * Permanently cancels this observation.
     *
     * A cancelled observation is excluded from automatic recovery and cannot
     * be refreshed again.
     */
    public fun cancel() {
        cancel(cancelSubscription = true)
    }

    /**
     * Marks this observation as closed without cancelling its server subscription.
     *
     * The owning client terminates the connection, which ends all remaining
     * subscription streams. Avoiding an individual cancellation here prevents
     * duplicate cancellation requests during client shutdown.
     */
    internal fun close() {
        cancel(cancelSubscription = false)
    }

    /**
     * Permanently stops this observation and optionally cancels its server subscription.
     *
     * When the subscription is retained, a subscription that a refresh is still
     * creating is abandoned as well, so that it is not cancelled individually
     * once it arrives.
     */
    private fun cancel(cancelSubscription: Boolean) {
        val previousSubscription: ObservationSubscription?
        val notifyCancelled: Boolean
        synchronized(stateLock) {
            notifyCancelled = !cancelled
            if (!notifyCancelled) {
                return
            }
            cancelled = true
            if (!cancelSubscription && subscriptionInFlight) {
                abandonedSubscriptionGenerations.add(generation)
            }
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
    internal fun waitForConnection() {
        synchronized(stateLock) {
            if (cancelled || mutableStatus.value is Failed) {
                return
            }
            if (subscriptionInFlight) {
                abandonedSubscriptionGenerations.add(generation)
            }
            generation++
            subscription = null
            mutableStatus.value = WaitingForConnection
        }
    }

    /**
     * Tells whether a refresh is currently creating a subscription for the
     * present generation that has not been installed yet.
     *
     * A newly created observation is not subscribing yet, even though it starts
     * in the [Refreshing] status: its first subscription is only created once
     * its initial refresh begins.
     *
     * Reading this property requires holding [stateLock].
     */
    private val subscriptionInFlight: Boolean
        get() = subscribingGeneration == generation

    /**
     * Records that the refresh identified by [refreshGeneration] is no longer
     * creating a subscription.
     *
     * Calling this function requires holding [stateLock].
     */
    private fun finishSubscribing(refreshGeneration: Long) {
        if (subscribingGeneration == refreshGeneration) {
            subscribingGeneration = null
        }
    }

    /**
     * Tells whether this observation should be refreshed after reconnecting.
     */
    internal val needsRecovery: Boolean
        get() = synchronized(stateLock) {
            !cancelled && mutableStatus.value == WaitingForConnection
        }

    /**
     * Starts a new generation and detaches the previous subscription.
     */
    private fun beginRefresh(): Long? {
        val previousSubscription: ObservationSubscription?
        val refreshGeneration: Long
        synchronized(stateLock) {
            if (cancelled) {
                return null
            }
            generation++
            refreshGeneration = generation
            subscribingGeneration = refreshGeneration
            previousSubscription = subscription
            subscription = null
            mutableStatus.value = Refreshing
        }
        previousSubscription.cancelSafely()
        return refreshGeneration
    }

    /**
     * Replaces the complete value if [refreshGeneration] is still current.
     */
    private fun setValue(
        refreshGeneration: Long,
        newValue: T
    ): Boolean {
        synchronized(stateLock) {
            if (!isCurrent(refreshGeneration)) {
                return false
            }
            mutableValue.value = newValue
            return true
        }
    }

    /**
     * Buffers [update] during refresh or applies it to the active value afterward.
     */
    private fun bufferOrApplyUpdate(
        refreshGeneration: Long,
        pendingUpdates: PendingUpdates<T>,
        update: (T) -> T
    ) {
        synchronized(stateLock) {
            if (!isCurrent(refreshGeneration)) {
                return
            }
            if (pendingUpdates.buffering) {
                pendingUpdates.updates.add(update)
            } else {
                mutableValue.value = update(mutableValue.value)
            }
        }
    }

    /**
     * Buffers [cause] during refresh or handles it immediately afterward.
     */
    private fun bufferOrHandleFailure(
        refreshGeneration: Long,
        pendingUpdates: PendingUpdates<T>,
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

    /**
     * Installs [newSubscription] if its refresh generation is still current.
     */
    private fun installSubscription(
        refreshGeneration: Long,
        newSubscription: ObservationSubscription
    ): Boolean {
        val installed: Boolean
        val shouldCancel: Boolean
        synchronized(stateLock) {
            finishSubscribing(refreshGeneration)
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

    /**
     * Publishes the refreshed value together with updates received during its read.
     */
    private fun completeRefresh(
        refreshGeneration: Long,
        refreshedValue: T,
        pendingUpdates: PendingUpdates<T>
    ) {
        val pendingFailure: Throwable?
        synchronized(stateLock) {
            if (!isCurrent(refreshGeneration)) {
                return
            }
            var value = refreshedValue
            pendingUpdates.updates.forEach {
                value = it(value)
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

    /**
     * Attempts to preserve readable data after a non-connection subscription failure.
     */
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

    /**
     * Transitions this observation to a waiting or failed state for [cause].
     */
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

    /**
     * Tells whether [refreshGeneration] still owns this observation's callbacks.
     */
    private fun isCurrent(refreshGeneration: Long): Boolean =
        !cancelled && generation == refreshGeneration

    /**
     * Tells whether [cause] represents a temporary connection failure.
     */
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

/**
 * Creates an observation while keeping the type of an individual server update
 * out of [DataObservation]'s public type parameters.
 *
 * Each update is adapted to a transformation of the complete value. The
 * observation can therefore buffer and apply updates using only `T`, while
 * this factory retains the compile-time relationship between `T` and `U`.
 *
 * @param T The type of the complete observed value.
 * @param U The type of an individual server update.
 * @param initialValue The value exposed until the first successful read completes.
 * @param read Reads the complete current value from the server.
 * @param subscribe Creates the server subscription and installs its callbacks.
 * @param applyUpdate Applies one subscription update to the complete current value.
 * @param connectionStatus Returns the latest client connection status.
 * @param onCancelled Removes a permanently cancelled observation from its scope.
 * @param onRecoveryNeeded Requests a delayed retry after a stream failure on a
 *   connected channel.
 * @param requestContext Runs the blocking subscription and read operations.
 *   Production callers must use a dispatcher suitable for blocking work. Tests
 *   may replace it with a deterministic context.
 */
internal fun <T, U> createDataObservation(
    initialValue: T,
    read: () -> T,
    subscribe: (
        onUpdate: (U) -> Unit,
        onError: (Throwable) -> Unit
    ) -> ObservationSubscription,
    applyUpdate: (T, U) -> T,
    connectionStatus: () -> ConnectionStatus,
    onCancelled: (DataObservation<*>) -> Unit,
    onRecoveryNeeded: (DataObservation<*>) -> Unit = {},
    requestContext: CoroutineContext = IO
): DataObservation<T> = DataObservation(
    initialValue,
    read,
    { onUpdate, onError ->
        subscribe(
            { update ->
                onUpdate { value -> applyUpdate(value, update) }
            },
            onError
        )
    },
    connectionStatus,
    requestContext,
    onCancelled,
    onRecoveryNeeded
)

/**
 * Tells whether this throwable or one of its causes carries a gRPC status.
 */
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

/**
 * Collects subscription callbacks until the complete refresh value is ready.
 */
private class PendingUpdates<T> {

    /**
     * Updates received before the refreshed value is published.
     */
    val updates: MutableList<(T) -> T> = mutableListOf()

    /**
     * Tells whether callbacks must still be accumulated.
     */
    var buffering: Boolean = true

    /**
     * The first stream failure received while buffering, if any.
     */
    var failure: Throwable? = null
}

/**
 * Describes the result of the blocking portion of a refresh.
 */
private sealed class RefreshResult<out T> {

    /**
     * Contains a new subscription and the complete value read after creating it.
     */
    data class Success<T>(
        val subscription: ObservationSubscription,
        val value: T
    ) : RefreshResult<T>()

    /**
     * Contains a created subscription whose subsequent complete read failed.
     */
    data class ReadFailed(
        val subscription: ObservationSubscription,
        val cause: Exception
    ) : RefreshResult<Nothing>()

    /**
     * Contains a subscription failure and any complete value read afterward.
     */
    data class SubscriptionFailed<T>(
        val cause: Exception,
        val value: ReadValue<T>?
    ) : RefreshResult<T>()
}

/**
 * Wraps a possibly nullable value so it can be distinguished from no read result.
 */
private data class ReadValue<out T>(val value: T)

/**
 * Propagates coroutine cancellation instead of converting it to observation failure.
 */
private fun rethrowCancellation(exception: Exception) {
    if (exception is CancellationException) {
        throw exception
    }
}

/**
 * Cancels this subscription without propagating a best-effort request failure.
 */
private fun ObservationSubscription?.cancelSafely() {
    try {
        this?.cancel()
    } catch (_: Exception) {
        // Cancellation is best-effort because the stream or channel may already
        // have failed independently.
    }
}
