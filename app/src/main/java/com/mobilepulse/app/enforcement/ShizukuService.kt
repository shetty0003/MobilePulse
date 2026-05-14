package com.mobilepulse.app.enforcement

class ShizukuService : IShizukuService.Stub() {
    override fun execute(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            process.destroy()
            output
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }
}