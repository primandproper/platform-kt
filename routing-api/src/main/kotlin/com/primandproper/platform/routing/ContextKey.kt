package com.primandproper.platform.routing

/**
 * A key used to stash values on a request-scoped context. Direct port of Go's `routing.ContextKey`
 * (`type ContextKey string`), whose own doc quotes the Go context guidance: a context key "must be
 * comparable and should not be of type string or any other built-in type to avoid collisions between
 * packages." Modeled as a value class so it is a distinct type from a bare `String`, preserving that
 * anti-collision intent.
 *
 * On the JVM there is no ambient `context.Context`; the Ktor provider stores request-scoped values on
 * the call's `Attributes` (keyed by `AttributeKey`), so this type is the portable, framework-free name
 * that the provider maps onto its attribute keys.
 */
@JvmInline
public value class ContextKey(
    public val value: String,
)
