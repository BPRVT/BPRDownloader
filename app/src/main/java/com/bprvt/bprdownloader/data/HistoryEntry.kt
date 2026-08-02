package com.bprvt.bprdownloader.data

data class HistoryEntry(
    val id: Long,
    val url: String,
    val label: String,
    val fileName: String?,
    val sizeBytes: Long,
    val lastUsed: Long,
    val useCount: Int,
    val favorite: Boolean,
    val kind: String
) {
    companion object {
        const val KIND_DOWNLOAD = "download"
        const val KIND_PAGE = "page"
    }
}
