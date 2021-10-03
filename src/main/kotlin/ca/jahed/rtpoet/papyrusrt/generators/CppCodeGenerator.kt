package ca.jahed.rtpoet.papyrusrt.generators

import ca.jahed.rtpoet.papyrusrt.PapyrusRTWriter
import ca.jahed.rtpoet.papyrusrt.utils.CmdUtils
import ca.jahed.rtpoet.rtmodel.RTModel
import net.lingala.zip4j.io.inputstream.ZipInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class CppCodeGenerator(
    private var codegen: String? = null,
    private var plugins: String? = null,
) {
    private val tempDir = File(System.getProperty("java.io.tmpdir"), "ca.jahed.rtpoet.papyrusrt")

    init {
        if (codegen == null || plugins == null) {
            val codegenDir = File(tempDir, "codegen")
            val codegenFile = File(codegenDir, "bin" + File.separator + "umlrtgen.jar")
            val pluginsDir = File(codegenDir, "plugins")

            if (!codegenFile.exists() || !pluginsDir.exists()) {
                codegenDir.delete()

                val zip = this::class.java.classLoader.getResourceAsStream("codegen.zip")
                extractWithZipInputStream(zip, tempDir)
            }

            codegen = codegenFile.absolutePath
            plugins = pluginsDir.absolutePath
        }
    }

    companion object {
        @JvmStatic
        fun generate(model: RTModel): Boolean {
            return CppCodeGenerator().doGenerate(model, "code")
        }

        @JvmStatic
        fun generate(model: RTModel, outputPath: String): Boolean {
            return CppCodeGenerator().doGenerate(model, outputPath)
        }

        @JvmStatic
        fun generate(model: RTModel, outputPath: String, timeout: Long): Boolean {
            return CppCodeGenerator().doGenerate(model, outputPath, timeout)
        }
    }

    fun doGenerate(model: RTModel, outputPath: String = "code", timeout: Long = 0): Boolean {
        val codeDir = File(outputPath)
        codeDir.mkdirs()

        if (!codeDir.exists())
            throw RuntimeException("Cannot create output directory ${codeDir.absolutePath}")

        val umlTmpDir = File(tempDir, "uml")
        umlTmpDir.mkdirs()

        if (!umlTmpDir.exists())
            throw RuntimeException("Cannot create temp directory ${umlTmpDir.absolutePath}")

        val resource = PapyrusRTWriter.writeAll(umlTmpDir.absolutePath, model)

        val result = """
            java -jar ${codegen} -p ${plugins} -o ${codeDir.absolutePath} ${resource.uri.toString().substring(5)}
        """.trim().runCommand(timeout)
        return result
    }

    private fun String.runCommand(timeout: Long): Boolean {
        return CmdUtils.exec(this, timeout) == 0
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