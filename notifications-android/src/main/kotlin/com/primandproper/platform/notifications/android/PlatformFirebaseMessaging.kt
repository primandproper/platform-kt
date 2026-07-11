package com.primandproper.platform.notifications.android

import android.content.Context
import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.NoopLogger
import com.primandproper.platform.observability.NoopTracerProvider
import com.primandproper.platform.observability.TracerProvider

/**
 * How the OS-instantiated [PlatformFirebaseMessagingService] finds the app's [PushMessageHandler].
 *
 * The service is constructed by the Android framework with a no-arg constructor, so it cannot be
 * handed a handler directly. An app can either implement this interface on its `Application` (the
 * service resolves it off `applicationContext`), or register a handler process-globally via
 * [PlatformFirebaseMessaging.handler]. Implementing this on the `Application` is the cleaner path —
 * no global mutable state, and the handler is scoped to the app object's lifetime.
 */
public fun interface PushMessageHandlerProvider {
    /** Returns the handler that should receive delivered pushes. */
    public fun pushMessageHandler(): PushMessageHandler
}

/**
 * Process-global wiring for [PlatformFirebaseMessagingService].
 *
 * Set [handler] (and, optionally, [logger] / [tracerProvider] so the dispatch spans and logs reach
 * the app's observability stack) from `Application.onCreate`, before the first push can arrive. As an
 * alternative to setting [handler], have the `Application` implement [PushMessageHandlerProvider] —
 * the service prefers an explicitly-set [handler] and otherwise falls back to that.
 *
 * All fields are `@Volatile` because they are written on the main thread (app startup) and read on
 * FCM's background delivery thread.
 */
public object PlatformFirebaseMessaging {
    /** The handler for delivered pushes; when null the service falls back to [PushMessageHandlerProvider]. */
    @Volatile
    public var handler: PushMessageHandler? = null

    /** Optional root logger threaded into the per-delivery [PushMessageDispatcher]. */
    @Volatile
    public var logger: Logger = NoopLogger

    /** Optional tracer provider threaded into the per-delivery [PushMessageDispatcher]. */
    @Volatile
    public var tracerProvider: TracerProvider = NoopTracerProvider

    /** Resolves the handler: an explicitly-set [handler] wins, else the app's provider, else null. */
    internal fun resolveHandler(context: Context): PushMessageHandler? =
        handler ?: (context.applicationContext as? PushMessageHandlerProvider)?.pushMessageHandler()
}
