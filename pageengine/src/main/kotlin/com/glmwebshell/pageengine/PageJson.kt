package com.glmwebshell.pageengine

import kotlinx.serialization.json.Json

/** Shared Json instance — strict enough to surface bridge / adapter bugs. */
object PageJson {
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
        explicitNulls = false
    }
}
