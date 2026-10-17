package com.easyadb.core.util

data class ExecResult(
    val success: Boolean,
    val output: String,
    val exitCode: Int = -1
)
