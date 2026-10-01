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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot.Companion.global
import androidx.compose.runtime.snapshots.Snapshot.Companion.sendApplyNotifications
import io.spine.base.EntityState
import io.spine.chords.client.ConnectionStatus.CLOSED
import io.spine.chords.client.ConnectionStatus.CONNECTED
import io.spine.chords.client.ConnectionStatus.UNAVAILABLE
import io.spine.chords.client.DataObservationStatus.Active
import io.spine.chords.client.DataObservationStatus.Cancelled
import io.spine.chords.client.DataObservationStatus.Failed
import io.spine.chords.client.DataObservationStatus.Refreshing
import io.spine.chords.client.DataObservationStatus.WaitingForConnection
import io.spine.chords.client.DataPageCursor.After
import io.spine.chords.client.DataPageCursor.At
import io.spine.chords.client.DataPageCursor.Before
import io.spine.chords.client.DataPageCursor.End
import io.spine.chords.client.DataPageCursor.Start
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.LAZY
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Navigation and loading state for browsing a [PagedDataQuery] one page at a time.
 *
 * `PagedDataNavigator(query, client, scope)` starts loading immediately;
 * [rememberPagedDataNavigator] starts after the composition commits. The first page follows
 * live changes; other pages stay fixed until navigation or retry. Only the displayed page
 * is retained. [status] reports loading, active data, automatic recovery, terminal failure,
 * or closure.
 *
 * Methods and result publication are synchronized, so callers may use a multithreaded dispatcher.
 * Closing the navigator or client, or cancelling the caller's scope, ends loading and pending
 * waits. Closing a navigator does not cancel its caller's scope or close the shared client.
 *
 * @param T The displayed item type.
 */
@Suppress("TooManyFunctions" /* Navigation and cancellation share one page lifecycle. */)
public class PagedDataNavigator<T> internal constructor(
    /**
     * The caller's lifetime and dispatcher for page requests and connection monitoring.
     */
    callerScope: CoroutineScope,
    /**
     * Connection state used to coordinate recovery and client closure.
     */
    private val connectionStatus: StateFlow<ConnectionStatus>,
    /**
     * The source of fixed pages beyond the live first page.
     */
    private val read: suspend (DataPageCursor) -> DataPage<T>,
    /**
     * The source of live first-page data and its loading or recovery status.
     */
    private val observeFirstPage: () -> Flow<Pair<DataObservationStatus, DataPage<T>>>,
    /**
     * The item's cursor value used to locate an adjacent page.
     */
    private val keyOf: (T) -> Any
) : AutoCloseable {

    /**
     * Mutual exclusion for navigation, closure, and publication from concurrent requests.
     */
    private val lock = Any()

    /**
     * The navigator's child job, allowing closure without cancellation of the caller.
     */
    private val lifetime = Job(callerScope.coroutineContext[Job])

    /**
     * The coroutine scope shared by this navigator's requests and connection monitor.
     */
    private val scope = CoroutineScope(callerScope.coroutineContext + lifetime)

    /**
     * A consistent page, cursor, and loading status for Compose readers.
     */
    private var state by mutableStateOf(PageState<T>())

    /**
     * The request number used to reject results from cancelled work that finishes late.
     */
    private var generation = 0L

    /**
     * The latest requested cursor, so retry can repeat a failed navigation.
     */
    private var requestedCursor: DataPageCursor = Start

    /**
     * The current request job, retained for cancellation and completion before its replacement.
     */
    private var requestJob: Job? = null

    /**
     * The next state-change signal for waiters, which survive replacement of a page request.
     */
    private var nextChange = CompletableDeferred<Unit>(lifetime)

    /**
     * Client-closure monitoring, active from the first request until the navigator closes.
     */
    private var connectionMonitor: Job? = null

    init {
        lifetime.invokeOnCompletion { close() }
    }

    /**
     * The displayed items, initially empty and retained while another request is pending.
     */
    public val items: List<T>
        get() = synchronized(lock) { state.page.items }

    /**
     * Loading, active data, automatic connection recovery, terminal failure, or cancellation.
     * [Failed] exposes its cause and requires [retry].
     * [WaitingForConnection] recovers automatically.
     */
    public val status: DataObservationStatus
        get() = synchronized(lock) { if (lifetime.isActive) state.status else Cancelled }

    /**
     * Whether an adjacent page can be requested in the forward direction.
     */
    public val canGoNext: Boolean
        get() = synchronized(lock) { status == Active && state.page.hasNext }

    /**
     * Whether an adjacent page can be requested in the backward direction.
     */
    public val canGoPrevious: Boolean
        get() = synchronized(lock) { status == Active && state.page.hasPrevious }

    /**
     * Whether the displayed items are from the live first page.
     */
    public val isFirstPage: Boolean
        get() = synchronized(lock) { state.cursor == Start }

    /**
     * An opaque identity for resetting scroll position when the displayed page changes.
     * It stays stable during live updates and while navigation is pending or has failed.
     */
    public val pageKey: Any
        get() = synchronized(lock) { state.cursor }

    /**
     * Waits for the latest requested page, following any request that replaces it while waiting.
     *
     * Returns the current items immediately when [status] is [Active]. Terminal failures throw
     * their cause. Closing this navigator or cancelling its scope throws [CancellationException].
     * Automatic recovery keeps waiting; callers can impose a timeout or cancel their own wait.
     */
    public suspend fun awaitItems(): List<T> {
        while (true) {
            lifetime.ensureActive()
            val (current, signal) = global { synchronized(lock) { state to nextChange } }
            when (val value = current.status) {
                Active -> return current.page.items
                is Failed -> throw value.error
                Cancelled -> throw CancellationException("Page navigation is closed.")
                else -> signal.await()
            }
        }
    }

    /**
     * Requests the following page when [canGoNext] is true.
     */
    public fun next() {
        change {
            if (canGoNext && state.page.items.isNotEmpty()) {
                load(After(keyOf(state.page.items.last())))
            }
        }
    }

    /**
     * Requests the preceding page, resuming live updates on reaching the first page.
     * An empty page returns directly to the beginning.
     */
    public fun previous() {
        change {
            if (canGoPrevious) {
                val first = state.page.items.firstOrNull()
                load(if (first == null) Start else Before(keyOf(first)))
            }
        }
    }

    /**
     * Requests the first page and resumes live updates, replacing any pending request.
     */
    public fun first(): Unit = change { load(Start) }

    /**
     * Requests the final page without reading intervening pages, replacing any pending request.
     *
     * The page contains up to `pageSize` items, ending with the final matching item.
     * When the entire selection fits on one page, this becomes the live first page.
     * Otherwise it stays fixed until navigation or retry.
     */
    public fun last(): Unit = change { load(End) }

    /**
     * Requests the page starting at [key], replacing any pending request, without reading
     * intervening pages.
     *
     * [key] is a value of the query's `orderBy` column, as `keyOf` returns it. The matching item
     * with that value is included; otherwise the page starts at the next matching item in display
     * order, or is empty. A separate single-item read checks for matching items before [key].
     * If there are none, this is the live first page; otherwise it stays fixed until navigation
     * or retry.
     */
    public fun seek(key: Any): Unit = change { load(At(key)) }

    /**
     * Repeats the latest requested page, replacing any pending request.
     */
    public fun retry() {
        change { load(requestedCursor) }
    }

    /**
     * Cancels requests, live updates, and pending waits. Repeated calls have no further effect.
     * An unused navigator can close before its initial composition commits.
     */
    public override fun close() {
        change {
            if ((requestJob != null || connectionMonitor != null) && state.status != Cancelled) {
                generation++
                publish(Cancelled)
            }
            add { scope.cancel() }
        }
    }

    /**
     * The transition to a requested page, protected from late results of cancelled requests.
     * A loaded first page remains active while its new observation starts.
     */
    @Suppress("TooGenericExceptionCaught" /* Request failures must become observable statuses. */)
    private fun MutableList<() -> Unit>.load(target: DataPageCursor) {
        if (!lifetime.isActive || state.status == Cancelled) return
        if (connectionStatus.value == CLOSED) {
            publish(Cancelled)
            add { scope.cancel() }
            return
        }
        if (connectionMonitor == null) {
            val monitor = scope.launch(start = LAZY) {
                connectionStatus.collect { if (it == CLOSED) close() }
            }
            connectionMonitor = monitor
            add { monitor.start() }
        }
        val previous = requestJob
        val current = ++generation
        requestedCursor = target
        publish(Refreshing)
        val request = scope.launch(start = LAZY) {
            try {
                previous?.join()
                if (target != Start) {
                    val result = readFixedPage(target, current)
                    acceptIfCurrent(
                        expected = current, status = Active, result = result, target = target
                    )
                    if (result.hasPrevious) return@launch
                }
                var retainFirstPage = target != Start
                observeFirstPage()
                    .collect { (status, result) ->
                        if (!retainFirstPage || status != Refreshing) {
                            acceptIfCurrent(
                                expected = current, status = status, result = result
                            )
                        }
                        retainFirstPage = false
                    }
                acceptIfCurrent(current, Cancelled)
            } catch (cancelled: CancellationException) {
                acceptIfCurrent(current, Cancelled)
                throw cancelled
            } catch (error: Exception) {
                acceptIfCurrent(current, Failed(error))
            }
        }
        requestJob = request
        add {
            previous?.cancel()
            request.start()
        }
    }

    /**
     * Applies a transition atomically without dispatching observers or waiters under [lock].
     * Global snapshot writes defer apply notifications until the transition is complete.
     */
    private fun change(block: MutableList<() -> Unit>.() -> Unit) {
        val actions = mutableListOf<() -> Unit>()
        global {
            synchronized(lock) {
                actions.block()
            }
        }
        try {
            sendApplyNotifications()
        } finally {
            actions.forEach { it() }
        }
    }

    /**
     * Fixed-page loading with the same connection recovery policy as live observations.
     * Before the first attempt, it waits only while the client is `UNAVAILABLE`; idle and
     * connecting clients can read.
     * After a transient failure, it uses the observation retry delay and waits until connected.
     */
    @Suppress("TooGenericExceptionCaught" /* Classifies failures from arbitrary client reads. */)
    private suspend fun readFixedPage(target: DataPageCursor, current: Long): DataPage<T> {
        while (true) {
            if (connectionStatus.value == UNAVAILABLE) {
                acceptIfCurrent(current, WaitingForConnection)
                awaitConnection()
                acceptIfCurrent(current, Refreshing)
            }
            try {
                return read(target)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                currentCoroutineContext()
                    .ensureActive()
                if (!isConnectionFailure(error, connectionStatus.value)) throw error
                acceptIfCurrent(current, WaitingForConnection)
                delay(ConnectionRetryDelayMillis)
                awaitConnection()
                acceptIfCurrent(current, Refreshing)
            }
        }
    }

    /**
     * Suspends until connected or permanently closed; the navigator's lifetime cancels the wait.
     */
    private suspend fun awaitConnection() {
        connectionStatus
            .takeWhile { it != CONNECTED && it != CLOSED }
            .collect { }
        if (connectionStatus.value == CLOSED) throw CancellationException("The client is closed.")
    }

    /**
     * Rejects late results and publishes an accepted page and status together.
     * An accepted [Cancelled] status stops the navigator's work.
     */
    private fun acceptIfCurrent(
        expected: Long,
        status: DataObservationStatus,
        result: DataPage<T>? = null,
        target: DataPageCursor = Start
    ) {
        change {
            if (expected != generation || !lifetime.isActive) return@change
            publish(status = status, result = result, target = target)
            if (status == Cancelled) add { scope.cancel() }
        }
    }

    /**
     * A consistent page and status for snapshot observers and suspended callers.
     * Both become visible before waiters resume. Calling this function requires holding [lock].
     */
    private fun MutableList<() -> Unit>.publish(
        status: DataObservationStatus,
        result: DataPage<T>? = null,
        target: DataPageCursor = Start
    ) {
        state = if (status == Active && result != null) PageState(
            page = result,
            cursor = if (result.hasPrevious) target else Start,
            status = status
        ) else state.copy(status = status)
        val previous = nextChange
        nextChange = CompletableDeferred(lifetime)
        add { previous.complete(Unit) }
    }

    /**
     * One published page with its navigation identity and loading state.
     *
     * @property page The last successfully loaded page, retained while loading another.
     * @property cursor The displayed page's identity, independent of pending navigation.
     * @property status Loading, recovery, failure, or cancellation of the latest request.
     */
    private data class PageState<T>(
        val page: DataPage<T> = DataPage(),
        val cursor: DataPageCursor = Start,
        val status: DataObservationStatus = Refreshing
    )
}

/**
 * Page navigation for callers that manage their lifetime through a coroutine [scope].
 *
 * Loading starts immediately. A closed client or cancelled scope produces a cancelled navigator
 * without issuing requests.
 * Close the navigator when finished, for example with `use`. Its requests and connection monitor
 * are children of [scope], so that scope cannot complete until the navigator is closed.
 *
 * @param T The entity type displayed by the navigator.
 * @param query The ordered selection to navigate.
 * @param client The server connection, which remains open when the navigator closes.
 * @param scope The caller's lifetime and dispatcher; cancellation closes the navigator.
 */
@Suppress("FunctionNaming" /* The factory uses the constructor name of its return type. */)
public fun <T : EntityState> PagedDataNavigator(
    query: PagedDataQuery<T>,
    client: Client,
    scope: CoroutineScope
): PagedDataNavigator<T> = createPagedDataNavigator(query, client, scope)
    .also { it.first() }

/**
 * A navigator whose first request is deferred to its lifecycle owner.
 * The public factory begins loading immediately; [rememberPagedDataNavigator] waits for a committed
 * composition.
 */
internal fun <T : EntityState> createPagedDataNavigator(
    query: PagedDataQuery<T>,
    client: Client,
    scope: CoroutineScope
): PagedDataNavigator<T> = PagedDataNavigator(
    callerScope = scope,
    connectionStatus = client.connectionStatus,
    read = { query.read(client, it) },
    observeFirstPage = { query.observeFirstPage(client) },
    keyOf = query.keyOf
)
