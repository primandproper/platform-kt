package com.primandproper.platform.database

import kotlin.test.Test
import kotlin.test.assertTrue

/** Port of platform-go's `database/errors_test.go`. */
class ErrorsTest {
    @Test
    fun `UserAlreadyExistsException carries the expected message`() {
        assertTrue(UserAlreadyExistsException().message!!.contains("user already exists"))
    }

    @Test
    fun `DatabaseNotReadyException carries the expected message`() {
        assertTrue(DatabaseNotReadyException().message!!.contains("database is not ready yet"))
    }
}
