package com.primandproper.platform.version

/*
Port of platform-go's `version` package: a holder for build/VCS metadata with an "unknown" fallback
for any unset field, plus indented-JSON rendering for CLI use.

Injection seam: platform-go sets package-level vars (`Version`, `CommitHash`, …) via `-ldflags -X`
at link time. The JVM has no linker seam, so this module ships only the holder ([BuildInfo]) and the
unknown-fallback accessor ([get]). Filling the holder is left as a documented build-time seam — the
consumer wires a Gradle task to write a generated resource (or stamp the JAR manifest) and populates
[BuildInfo] at startup, e.g. `BuildInfo.version = readManifestAttribute("Implementation-Version")`.
Left unset, every field falls back to "unknown", which is a valid shipping state.
*/

private const val UNKNOWN = "unknown"

/**
 * Mutable holder for the build-injected VCS/build metadata, the analog of platform-go's package-level
 * `Version`/`CommitHash`/`CommitTime`/`BuildTime` vars. A build-time seam (see the file header)
 * assigns these at startup; each defaults to `null`, which [get] renders as "unknown".
 */
public object BuildInfo {
    /** Release version, e.g. a semver tag like `v1.2.3`. */
    public var version: String? = null

    /** Full VCS commit hash the build was produced from. */
    public var commitHash: String? = null

    /** Timestamp of the commit the build was produced from. */
    public var commitTime: String? = null

    /** Timestamp at which the build itself was produced. */
    public var buildTime: String? = null
}

/**
 * Immutable snapshot of the version metadata. Field names carry the same `snake_case` JSON keys as
 * platform-go's `Info` struct so the rendered output is wire-compatible.
 */
public data class Info(
    val version: String,
    val commitHash: String,
    val commitTime: String,
    val buildTime: String,
) {
    /**
     * Renders this info as two-space-indented JSON with a trailing newline, matching Go's
     * `json.Encoder` with `SetIndent("", "  ")`. Keys are emitted in struct-declaration order.
     */
    public fun toJson(): String =
        buildString {
            append("{\n")
            append("  \"version\": ").append(quote(version)).append(",\n")
            append("  \"commit_hash\": ").append(quote(commitHash)).append(",\n")
            append("  \"commit_time\": ").append(quote(commitTime)).append(",\n")
            append("  \"build_time\": ").append(quote(buildTime)).append("\n")
            append("}\n")
        }
}

/**
 * Returns the current version info, substituting "unknown" for any field left unset in [BuildInfo].
 * The port of Go's `version.Get()`.
 */
public fun get(): Info =
    Info(
        version = BuildInfo.version.orUnknown(),
        commitHash = BuildInfo.commitHash.orUnknown(),
        commitTime = BuildInfo.commitTime.orUnknown(),
        buildTime = BuildInfo.buildTime.orUnknown(),
    )

/**
 * Writes the current version info as indented JSON to [out]. The port of Go's `version.WriteJSON`;
 * pass `System.out` for the `WriteJSONToStdout` behaviour.
 */
public fun writeJson(out: Appendable) {
    out.append(get().toJson())
}

private fun String?.orUnknown(): String = if (this.isNullOrEmpty()) UNKNOWN else this

private fun quote(value: String): String =
    buildString {
        append('"')
        for (c in value) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
        }
        append('"')
    }
