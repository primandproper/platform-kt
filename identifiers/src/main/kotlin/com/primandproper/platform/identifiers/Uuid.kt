package com.primandproper.platform.identifiers

import java.util.UUID

/**
 * Produces a new random (v4) UUID string, e.g. `"3fa85f64-5717-4562-b3fc-2c963f66afa6"`. Offered
 * alongside [newUlid] for callers that need the universally-recognized UUID shape rather than a
 * compact, sortable one — this is the one part of the JDK standard library that already covers what
 * platform-go leans on xid for.
 */
public fun newUuid(): String = UUID.randomUUID().toString()

/** Reports whether [value] parses as a UUID, hyphens and all. */
public fun isValidUuid(value: String): Boolean =
    try {
        UUID.fromString(value)
        true
    } catch (_: IllegalArgumentException) {
        false
    }
