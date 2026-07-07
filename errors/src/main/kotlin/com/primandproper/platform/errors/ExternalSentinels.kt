package com.primandproper.platform.errors

/*
 * Sentinels that platform-go's errors package references from *other* modules when building its
 * HTTP/gRPC mappers. In platform-go these live in their owning packages; here the identity has to
 * sit at (or below) the layer that matches on it.
 *
 * The HTTP/gRPC mappers in this module match `ErrCircuitBroken` **by identity** (see http/ErrorCode,
 * http/ErrorMapper, grpc/ErrorMapper). Since :circuitbreaking depends on :errors, the sentinel must
 * be *owned here* — the lowest shared layer — to avoid an :errors -> :circuitbreaking cycle. The
 * :circuitbreaking module re-exports this exact instance, so `isError(e, ErrCircuitBroken)` resolves
 * to one identity from either package.
 *
 * ErrNoRows / ErrUserAlreadyExists remain true stand-ins: they belong to the not-yet-ported
 * database/sql and :database modules.
 * TODO: relocate ErrNoRows/ErrUserAlreadyExists to their owning modules once :database lands.
 */

/** Analog of Go's `sql.ErrNoRows` — no row matched a query expecting one. */
public val ErrNoRows: PlatformException = PlatformException("sql: no rows in result set")

/** Analog of `database.ErrUserAlreadyExists`. */
public val ErrUserAlreadyExists: PlatformException = PlatformException("user already exists")

/**
 * Canonical `circuitbreaking.ErrCircuitBroken` — a call was rejected because its breaker is open.
 * Owned here (not in :circuitbreaking) so this module's mappers can match it by identity without a
 * dependency cycle; :circuitbreaking re-exports this same instance.
 */
public val ErrCircuitBroken: PlatformException = PlatformException("service circuit broken")
