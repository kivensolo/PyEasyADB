package com.easyadb.platform

expect object ProcessKiller {
    fun kill(pid: Long)
    fun kill(process: Process)
}
