package com.primandproper.platform.search.vector

import com.primandproper.platform.errors.PlatformException

// Vector-search sentinels. In platform-go these are `var Err… = platformerrors.New(…)` values matched
// via errors.Is; here they are singleton PlatformExceptions matched via `errors.isError` (identity
// through the cause chain), exactly as the cache/vector ports do for their sentinels.

/** A query or upsert was attempted with a zero-length vector. Mirrors `vectorsearch.ErrEmptyEmbedding`. */
public val ErrEmptyEmbedding: PlatformException = PlatformException("empty embedding vector provided")

/** A vector with the given id does not exist in the index. Mirrors `vectorsearch.ErrNotFound`. */
public val ErrNotFound: PlatformException = PlatformException("vector not found")

/** A `null` provider config was passed to a constructor. Mirrors `vectorsearch.ErrNilConfig`. */
public val ErrNilConfig: PlatformException = PlatformException("nil vector search config")

/** An embedding's dimension does not match the index dimension. Mirrors `vectorsearch.ErrDimensionMismatch`. */
public val ErrDimensionMismatch: PlatformException = PlatformException("embedding dimension does not match index dimension")

/** A `null` database client was passed to a postgres-backed provider. Mirrors `vectorsearch.ErrNilDatabaseClient`. */
public val ErrNilDatabaseClient: PlatformException = PlatformException("nil database client")

/** An unsupported [DistanceMetric] was specified. Mirrors `vectorsearch.ErrInvalidMetric`. */
public val ErrInvalidMetric: PlatformException = PlatformException("invalid distance metric")

/** A non-positive dimension was specified. Mirrors `vectorsearch.ErrInvalidDimension`. */
public val ErrInvalidDimension: PlatformException = PlatformException("invalid index dimension")
