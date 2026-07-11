package com.primandproper.platform.search.vector

import com.primandproper.platform.errors.PlatformException

// Vector-search error types. In platform-go these are `var Err… = platformerrors.New(…)` sentinels
// matched via errors.Is; here they are exception CLASSES thrown fresh at each site and matched by
// type. The nil-config / nil-database-client sentinels are dropped — Kotlin's non-null types already
// forbid what they modelled.

/** Thrown when a query or upsert was attempted with a zero-length vector. Mirrors `vectorsearch.ErrEmptyEmbedding`. */
public class EmptyEmbeddingException : PlatformException("empty embedding vector provided")

/** Thrown when a vector with the given id does not exist in the index. Mirrors `vectorsearch.ErrNotFound`. */
public class VectorNotFoundException : PlatformException("vector not found")

/** Thrown when an embedding's dimension does not match the index dimension. Mirrors `vectorsearch.ErrDimensionMismatch`. */
public class DimensionMismatchException : PlatformException("embedding dimension does not match index dimension")

/** Thrown when an unsupported [DistanceMetric] was specified. Mirrors `vectorsearch.ErrInvalidMetric`. */
public class InvalidMetricException : PlatformException("invalid distance metric")

/** Thrown when a non-positive dimension was specified. Mirrors `vectorsearch.ErrInvalidDimension`. */
public class InvalidDimensionException : PlatformException("invalid index dimension")
