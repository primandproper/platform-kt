package com.primandproper.platform.llm

/**
 * The role a [Message] plays in a chat exchange — the typed port of platform-go's free-form
 * `Message.Role` string ("user", "assistant", "system", "tool"). Modelling it as an enum keeps a
 * typo off the wire while [value] preserves the exact string a backend serializes.
 */
public enum class Role(
    public val value: String,
) {
    USER("user"),
    ASSISTANT("assistant"),
    SYSTEM("system"),
    TOOL("tool"),
    ;

    public companion object {
        /** Resolves a role from its wire [value] (trimmed, case-insensitive), or `null` if unknown. */
        public fun fromValue(value: String): Role? {
            val normalized = value.trim().lowercase()
            return entries.firstOrNull { it.value == normalized }
        }
    }
}

/**
 * A single chat message — the port of platform-go's `llm.Message`. Pairs a [role] with its [content]
 * text; backends translate a list of these into their own request shape.
 */
public data class Message(
    val role: Role,
    val content: String,
)
