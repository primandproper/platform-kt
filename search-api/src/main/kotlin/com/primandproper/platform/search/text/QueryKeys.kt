package com.primandproper.platform.search.text

/** Query-parameter keys used to carry search input in requests. Port of platform-go's `query_keys.go`. */
public object QueryKeys {
    /** The query-string key a search term arrives under (`?q=…`). Mirrors Go's `QueryKeySearch`. */
    public const val SEARCH: String = "q"
}
