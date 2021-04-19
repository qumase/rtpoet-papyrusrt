package ca.jahed.rtpoet.papyrusrt.utils

import ca.jahed.rtpoet.papyrusrt.PapyrusRTWriter
import ca.jahed.rtpoet.rtmodel.RTModel
import net.lingala.zip4j.io.inputstream.ZipInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

class PapyrusRTCodeGenerator(
    private var codegen: String? = null,
    private var plugins: String? = null,
) {

    init {
        if (codegen == null || plugins == null) {
            val codegenDir = File(System.getProperty("java.io.tmpdir"), javaClass.simpleName)
            if (!codegenDir.exists()) {
                codegenDir.mkdirs()

                val stream = javaClass.classLoader.getResourceAsStream("codegen.zip")
                extractWithZipInputStream(stream, codegenDir)
            }

            codegen = File(codegenDir, "codegen/bin/umlrtgen.jar").absolutePath
            plugins = File(codegenDir, "codegen/plugins").absolutePath
        }
    }

    companion object {
        @JvmStatic
        fun generate(model: RTModel, outputPath: String = "code"): Boolean {
            return PapyrusRTCodeGenerator().doGenerate(model, outputPath)
        }
    }

    fun doGenerate(model: RTModel, outputPath: String = "code"): Boolean {
        val outputDir = File(outputPath)
        val codeDir = File(outputDir, "cpp")
        codeDir.mkdirs()

        if (!codeDir.exists())
            throw RuntimeException("Cannnot create output directory ${outputDir.absolutePath}")

        val outputModel = File(outputDir, "model.uml")
        PapyrusRTWriter.write(outputModel.absolutePath, model)

        return """
            java -jar ${codegen} -p ${plugins} -o ${codeDir.absolutePath} ${outputModel.absolutePath}
        """.trim().runCommand(outputDir)
    }

    private fun String.runCommand(workingDir: File): Boolean {
        val pb = ProcessBuilder(*split(" ").toTypedArray())
            .directory(workingDir)
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()
        return pb.waitFor(120, TimeUnit.MINUTES) && pb.exitValue() == 0

    }

    private fun extractWithZipInputStream(zipFile: InputStream, destination: File) {
        var readLen: Int
        val readBuffer = ByteArray(4096)

        val zipInputStream = ZipInputStream(zipFile)
        var localFileHeader = zipInputStream.nextEntry

        while (localFileHeader != null) {
            val extractedFile = File(destination, localFileHeader.fileName)
            if (localFileHeader.isDirectory) {
                extractedFile.mkdirs()
            } else {
                FileOutputStream(extractedFile).use { outputStream ->
                    while (zipInputStream.read(readBuffer).also { readLen = it } != -1) {
                        outputStream.write(readBuffer, 0, readLen)
                    }
                }
            }

            localFileHeader = zipInputStream.nextEntry
        }
    }
}