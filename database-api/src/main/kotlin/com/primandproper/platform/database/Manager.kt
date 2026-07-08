package com.primandproper.platform.database

/**
 * Administrative access management: creating/dropping users and databases and granting privileges.
 * Port of platform-go's `database.Manager` (`database/access_manager.go`), made coroutine-native —
 * every method suspends where Go took a `context.Context`.
 *
 * Implementations are provider-specific (the Postgres one lives in
 * `:database-exposed` under `postgres.tableaccess`); this is the abstraction they satisfy.
 */
public interface Manager {
    /** Creates a login user with the given password. */
    public suspend fun createUser(
        username: String,
        password: String,
    )

    /** Drops the user if it exists. */
    public suspend fun deleteUser(username: String)

    /** Creates a database owned by [owner]. */
    public suspend fun createDatabase(
        dbName: String,
        owner: String,
    )

    /** Drops the database if it exists. */
    public suspend fun deleteDatabase(dbName: String)

    /** Reports whether a user with [username] exists. */
    public suspend fun userExists(username: String): Boolean

    /** Reports whether a database named [dbName] exists. */
    public suspend fun databaseExists(dbName: String): Boolean

    /** Grants [privilege] on `schema.table` to [username]. */
    public suspend fun grantUserAccessToTable(
        username: String,
        schema: String,
        table: String,
        privilege: String,
    )

    /** Reports whether [username] can connect to [dbName]. */
    public suspend fun userCanAccessDatabase(
        username: String,
        dbName: String,
    ): Boolean
}
