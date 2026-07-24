package com.androidscreentuner.extreme

import java.io.BufferedReader
import java.io.IOException
import java.io.OutputStream

object SurfaceFlingerManager {

    private val CODES_TO_TRY = listOf(1015, 1023, 1024, 1025, 1030, 1035)
    private const val MARKER = "---SF_DONE---"

    var transactionCode: Int = -1
        private set

    private var suProcess: Process? = null
    private var suStdin: OutputStream? = null
    private var suStdout: BufferedReader? = null

    @Synchronized
    fun openRootShell(): Boolean {
        val result = exec("id")
        if (result != null && result.contains("uid=0")) return true
        closeRootShell()
        return false
    }

    @Synchronized
    fun closeRootShell() {
        try { suStdin?.close() } catch (_: IOException) {}
        try { suProcess?.destroy() } catch (_: Exception) {}
        suProcess = null
        suStdin = null
        suStdout = null
    }

    fun probe(): Boolean {
        for (code in CODES_TO_TRY) {
            if (testCode(code)) {
                transactionCode = code
                return true
            }
        }
        return false
    }

    fun probeWithCached(cached: Int): Boolean {
        if (cached > 0 && testCode(cached)) {
            transactionCode = cached
            return true
        }
        return probe()
    }

    fun applyMatrix(matrix: FloatArray): Boolean {
        if (transactionCode == -1) return false
        val sb = StringBuilder("service call SurfaceFlinger $transactionCode i32 1")
        for (v in matrix) {
            sb.append(" f ").append(v)
        }
        return exec(sb.toString()) != null
    }

    fun applyIdentity(): Boolean {
        return applyMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0f, 0f, 0f, 1f
            )
        )
    }

    private fun testCode(code: Int): Boolean {
        val cmd = "service call SurfaceFlinger $code i32 1" +
                " f 1.0 f 0.0 f 0.0 f 0.0" +
                " f 0.0 f 1.0 f 0.0 f 0.0" +
                " f 0.0 f 0.0 f 1.0 f 0.0" +
                " f 0.0 f 0.0 f 0.0 f 1.0"
        val result = exec(cmd) ?: return false
        return !result.contains("Error")
    }

    @Synchronized
    private fun exec(cmd: String): String? {
        if (suProcess?.isAlive != true) {
            closeRootShell()
            try {
                suProcess = Runtime.getRuntime().exec("su")
                suStdin = suProcess!!.outputStream
                suStdout = suProcess!!.inputStream.bufferedReader()
            } catch (_: IOException) {
                closeRootShell()
                return null
            }
        }

        val stdin = suStdin ?: return null
        val stdout = suStdout ?: return null

        return try {
            stdin.write("$cmd 2>&1\necho $MARKER\n".toByteArray())
            stdin.flush()

            val sb = StringBuilder()
            while (true) {
                val line = stdout.readLine() ?: run {
                    closeRootShell()
                    return null
                }
                if (line == MARKER) break
                if (sb.isNotEmpty()) sb.append('\n')
                sb.append(line)
            }
            sb.toString()
        } catch (_: IOException) {
            closeRootShell()
            null
        }
    }
}
