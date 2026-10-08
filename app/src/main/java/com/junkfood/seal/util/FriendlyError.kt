package com.junkfood.seal.util

import com.junkfood.seal.R

/** Plain-language reason for a yt-dlp failure; the raw message is still shown small below it. */
enum class FriendlyError(val messageRes: Int) {
    PrivateOrLogin(R.string.error_private),
    Removed(R.string.error_removed),
    Network(R.string.error_network),
    Unsupported(R.string.error_unsupported),
    Extractor(R.string.error_extractor),
    Unknown(R.string.error_unknown);

    companion object {
        fun of(message: String?): FriendlyError {
            val m = message?.lowercase() ?: return Unknown
            return when {
                listOf("private", "login", "log in", "sign in", "cookies", "age-restricted", "age restricted")
                    .any { it in m } -> PrivateOrLogin
                listOf("video unavailable", "has been removed", "no longer available", "http error 404", "not found")
                    .any { it in m } -> Removed
                listOf("unsupported url", "is not a valid url").any { it in m } -> Unsupported
                listOf(
                        "timed out",
                        "transporterror",
                        "unable to download webpage",
                        "connection",
                        "network is unreachable",
                        "temporary failure in name resolution",
                        "failed to resolve",
                    )
                    .any { it in m } -> Network
                listOf("unable to extract", "no video formats", "unable to parse", "cannot parse data")
                    .any { it in m } ->
                    Extractor
                else -> Unknown
            }
        }
    }
}
