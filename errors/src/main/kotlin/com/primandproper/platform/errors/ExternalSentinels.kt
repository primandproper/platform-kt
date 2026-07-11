package com.primandproper.platform.errors

/*
 * Error types that platform-go's errors package references from *other* modules when building its
 * HTTP/gRPC mappers. In platform-go these live in their owning packages; here the type has to be
 * declared at (or below) the layer that matches on it.
 *
 * The HTTP/gRPC mappers in this module match [CircuitBrokenException] **by type** (see http/ErrorCode,
 * http/ErrorMapper, grpc/ErrorMapper). Since :circuitbreaking depends on :errors, the type must be
 * *owned here* — the lowest shared layer — to avoid an :errors -> :circuitbreaking cycle. The
 * :circuitbreaking module re-exports this class via a typealias, so a breaker rejection thrown there
 * matches `isError<CircuitBrokenException>(e)` in this module's mappers.
 *
 * NoRowsException / UserAlreadyExistsException remain stand-ins for the not-yet-fully-ported
 * database/sql and :database modules (:database-api re-exports UserAlreadyExistsException).
 */

/** Analog of Go's `sql.ErrNoRows` — no row matched a query expecting one. */
public class NoRowsException : PlatformException("sql: no rows in result set")

/** Analog of `database.ErrUserAlreadyExists` — a user with that identity already exists. */
public class UserAlreadyExistsException : PlatformException("user already exists")

/**
 * A call was rejected because its circuit breaker is open. Owned here (not in :circuitbreaking) so
 * this module's mappers can match it by type without a dependency cycle; :circuitbreaking re-exports
 * this exact class via a typealias.
 */
public class CircuitBrokenException : PlatformException("service circuit broken")
