package com.primandproper.platform.notifications.fcm

/** Thrown when an empty FCM project id is supplied. */
public class EmptyFcmProjectIdException : IllegalArgumentException("empty FCM project id")

/** Thrown when an empty FCM access token is supplied to the static-token factory. */
public class EmptyFcmAccessTokenException : IllegalArgumentException("empty FCM access token")

/**
 * Configures the FCM backend. Port of the connection parts of platform-go's `fcm.Config`, adapted for
 * the REST transport this module speaks instead of the Firebase SDK.
 *
 * platform-go's `Config` carries a `CredentialsPath` (a service-account JSON the SDK reads to mint
 * OAuth2 tokens). Speaking HTTP v1 directly, this port needs the two values that end up on the wire:
 * the [projectId] (in the request URL) and the [accessToken] (the `Bearer` credential). Deriving that
 * token from a service-account JSON — sign a JWT, exchange it at Google's token endpoint, refresh on
 * expiry — is the `TODO(fcm-oauth)` seam noted on the module; here the already-minted token is
 * supplied directly (and the sender also accepts a dynamic token provider for the refreshing case).
 *
 * [baseUrl] is not part of Go's `Config` — the SDK hardcodes the host — but it is surfaced here so
 * tests can point the sender at a fake host; it defaults to FCM's real API base.
 */
public data class FcmConfig(
    val projectId: String,
    val accessToken: String,
    val baseUrl: String = DEFAULT_BASE_URL,
) {
    /** Throws when [projectId] or [accessToken] is empty. */
    public fun validate() {
        if (projectId.isEmpty()) throw EmptyFcmProjectIdException()
        if (accessToken.isEmpty()) throw EmptyFcmAccessTokenException()
    }

    public companion object {
        /** FCM's production API base. Requests target `<baseUrl>/v1/projects/<projectId>/messages:send`. */
        public const val DEFAULT_BASE_URL: String = "https://fcm.googleapis.com"
    }
}
