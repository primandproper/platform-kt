package com.primandproper.platform.database

import com.primandproper.platform.errors.PlatformException

/*
 * The database sentinels, ported from platform-go's `database/database.go` and `database/errors.go`,
 * where they are `platformerrors.New(...)` values matched via `errors.Is`. Here they are singleton
 * [PlatformException]s matched via `isError` (identity through the cause chain) — the same idiom the
 * `:errors` module establishes for the rest of the tree.
 */

/** Indicates the database is not ready to serve queries yet. Port of Go's `ErrDatabaseNotReady`. */
public val ErrDatabaseNotReady: PlatformException = PlatformException("database is not ready yet")

/** Indicates a user with that username already exists. Port of Go's `ErrUserAlreadyExists`. */
public val ErrUserAlreadyExists: PlatformException = PlatformException("user already exists")
