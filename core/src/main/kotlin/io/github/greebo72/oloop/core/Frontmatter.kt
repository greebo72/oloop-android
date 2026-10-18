package io.github.greebo72.oloop.core

import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.AbstractConstruct
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.error.YAMLException
import org.yaml.snakeyaml.nodes.Node
import org.yaml.snakeyaml.nodes.ScalarNode
import org.yaml.snakeyaml.nodes.Tag

/** Splits a file into YAML frontmatter and Markdown body, and parses the YAML. */
internal object Frontmatter {
    // Same pattern as the Python CLI: "---\n", frontmatter, "\n---", optional newline, body.
    private val pattern = Regex("\\A---\\n(.*?)\\n---\\n?(.*)\\z", RegexOption.DOT_MATCHES_ALL)

    /** A fresh parser per call, SnakeYAML's Yaml is not thread safe. */
    private fun newYaml(): Yaml {
        val options = LoaderOptions().apply { isAllowDuplicateKeys = false }
        val constructor = object : SafeConstructor(options) {
            init {
                // Timestamps must stay strings. Otherwise they become Date and change on write.
                yamlConstructors[Tag.TIMESTAMP] = object : AbstractConstruct() {
                    override fun construct(node: Node): Any = (node as ScalarNode).value
                }
            }
        }
        return Yaml(constructor)
    }

    fun split(text: String): Pair<Map<String, Any?>, String> {
        val match = pattern.find(text.replace("\r\n", "\n")) ?: throw FormatException("no frontmatter")
        val data = try {
            newYaml().load<Any?>(match.groupValues[1])
        } catch (e: YAMLException) {
            throw FormatException("invalid YAML: ${e.message?.lineSequence()?.firstOrNull()}")
        }
        if (data !is Map<*, *>) throw FormatException("frontmatter is not a mapping")
        @Suppress("UNCHECKED_CAST")
        return (data as Map<String, Any?>) to match.groupValues[2].trim('\n')
    }
}
