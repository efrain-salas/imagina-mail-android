package net.thunderbird.android.imagina

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * The files of the repository that the tests of Imagina's resources read (colours, links, changelog, version).
 * Gradle runs the tests in the folder of the module; the IDE may run them in the root of the repository.
 */
internal object ImaginaProjectFiles {
    private val module: File = File("src/main/res").let {
        if (it.exists()) File(".") else File("app-imagina")
    }.absoluteFile.normalize()

    val root: File = module.parentFile

    fun module(path: String): File = File(module, path).also { check(it.exists()) { "${it.path} does not exist" } }

    fun root(path: String): File = File(root, path).also { check(it.exists()) { "${it.path} does not exist" } }

    /** The `<color name="..">value</color>` entries of a resource file, in order. */
    fun colors(file: File): Map<String, String> = resourceEntries(file, "color")

    /** The `<string name="..">value</string>` entries of a resource file, in order. */
    fun strings(file: File): Map<String, String> = resourceEntries(file, "string")

    private fun resourceEntries(file: File, tag: String): Map<String, String> {
        val elements = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName(tag)
        return buildMap {
            for (index in 0 until elements.length) {
                val element = elements.item(index) as Element
                put(element.getAttribute("name"), element.textContent.trim())
            }
        }
    }
}
