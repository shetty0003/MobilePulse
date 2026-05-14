package com.mobilepulse.app.data.model

data class OptimizationResult(
    val appsKilled: Int = 0,
    val cacheCleanedMb: Long = 0L,
    val errors: List<String> = emptyList()
)