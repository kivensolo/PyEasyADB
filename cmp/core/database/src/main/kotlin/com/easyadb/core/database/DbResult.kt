package com.easyadb.core.database

data class DbResult<T>(
    val success: Boolean,
    val data: T? = null,
    val error: String? = null
)
