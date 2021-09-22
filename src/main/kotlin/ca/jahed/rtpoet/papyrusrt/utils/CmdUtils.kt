package ca.jahed.rtpoet.papyrusrt.utils

import java.io.IOException
import java.io.InputStream
import java.io.PrintStream
import java.util.concurrent.TimeUnit


object CmdUtils {
    internal class StreamGobbler(private val inStream: InputStream, private val outStream: PrintStream? = null) :
        Runnable {
        override fun run() {
            try {
                var c: Int
                while (inStream.read().also { c = it } != -1)
                    outStream?.print(c.toChar())
            } catch (x: IOException) {
            }
        }
    }

    fun exec(cmd: String, timeout: Long = 0): Int {
        val rt = Runtime.getRuntime()
        val proc = rt.exec(cmd)

        val errorGobbler = Thread(StreamGobbler(proc.errorStream))
        val outputGobbler = Thread(StreamGobbler(proc.inputStream))

        errorGobbler.start()
        outputGobbler.start()

        if (timeout == 0.toLong()) proc.waitFor()
        else proc.waitFor(timeout, TimeUnit.SECONDS)

        errorGobbler.join()
        outputGobbler.join()
        return proc.exitValue()
    }
}