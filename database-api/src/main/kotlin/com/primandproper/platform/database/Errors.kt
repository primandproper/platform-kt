package com.primandproper.platform.database

import com.primandproper.platform.errors.PlatformException

/*
 * The database error types, ported from platform-go's `database/database.go` and `database/errors.go`,
 * where they are `platformerrors.New(...)` sentinels matched via `errors.Is`. Here they are exception
 * CLASSES thrown fresh at each site and matched by type — the same idiom the `:errors` module
 * establishes for the rest of the tree.
 */

/** Thrown when the database is not ready to serve queries yet. Port of Go's `ErrDatabaseNotReady`. */
public class DatabaseNotReadyException : PlatformException("database is not ready yet")

/**
 * Thrown when a user with that username already exists. Port of Go's `ErrUserAlreadyExists`. This
 * aliases the canonical [com.primandproper.platform.errors.UserAlreadyExistsException] so the shared
 * `:errors` HTTP/gRPC mappers match a database-layer rejection by type.
 */
public typealias UserAlreadyExistsException = com.primandproper.platform.errors.UserAlreadyExistsException
