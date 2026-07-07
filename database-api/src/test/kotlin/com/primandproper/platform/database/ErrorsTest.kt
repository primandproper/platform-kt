package com.primandproper.platform.database

import kotlin.test.Test
import kotlin.test.assertTrue

/** Port of platform-go's `database/errors_test.go`. */
class ErrorsTest {
    @Test
    fun `ErrUserAlreadyExists carries the expected message`() {
        assertTrue(ErrUserAlreadyExists.message!!.contains("user already exists"))
    }

    @Test
    fun `ErrDatabaseNotReady carries the expected message`() {
        assertTrue(ErrDatabaseNotReady.message!!.contains("database is not ready yet"))
    }
}
