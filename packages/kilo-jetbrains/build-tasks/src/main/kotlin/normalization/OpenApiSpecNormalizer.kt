package normalization

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.gradle.api.GradleException

internal object OpenApiSpecNormalizer {
    fun normalize(raw: String): String {
        val root = Json.parseToJsonElement(raw) as? JsonObject
            ?: throw GradleException("OpenAPI spec root must be a JSON object.")
        // Step 1: Remove duplicate dot-notation schemas and remap their $refs to
        //         camelCase equivalents so the spec remains self-consistent.
        // Step 2: Strip operation-level tags so all routes land in DefaultApi.
        // Step 3: Deduplicate the root-level tags array.
        val (noDotsRoot, dotMap) = remapDotSchemas(root)
        val stripped = stripTags(noDotsRoot)
        val deduped = dedupRootTags(stripped)
        return encode(deduped)
    }

    private fun encode(obj: JsonObject): String {
        val json = Json { prettyPrint = true }
            .encodeToString(JsonElement.serializer(), obj)
        return "$json\n"
    }

    /**
     * Find schemas whose names contain dots (e.g. "Event.tui.command.execute").
     * If a camelCase equivalent (e.g. "EventTuiCommandExecute") exists in the
     * same component map, remove the dot schema and rewrite every `$ref` that
     * points to it to use the camelCase name instead.
     */
    private fun remapDotSchemas(root: JsonObject): Pair<JsonObject, Map<String, String>> {
        val components = root["components"] as? JsonObject ?: return root to emptyMap()
        val schemas = components["schemas"] as? JsonObject ?: return root to emptyMap()

        // Build a map of dot-name → camelCase-name for schemas that have a
        // camelCase duplicate in the same spec.
        val dotMap = schemas.keys
            .filter { "." in it }
            .mapNotNull { dot ->
                val camel = dot.split(".").joinToString("") { w -> w.replaceFirstChar { c -> c.uppercase() } }
                if (camel in schemas) dot to camel else null
            }
            .toMap()

        if (dotMap.isEmpty()) return root to emptyMap()

        // Remove dot schemas.
        val cleaned = JsonObject(schemas.filterKeys { it !in dotMap })
        val noDotsComponents = JsonObject(components + mapOf("schemas" to cleaned))
        val noDotsRoot = JsonObject(root + mapOf("components" to noDotsComponents))

        // Rewrite $ref strings throughout the whole spec.
        val rewritten = rewriteRefs(noDotsRoot, dotMap)
        return rewritten to dotMap
    }

    /**
     * Recursively rewrite every JsonPrimitive `$ref` value that matches a
     * dot-notation schema name, replacing it with the camelCase equivalent.
     */
    private fun rewriteRefs(element: JsonElement, map: Map<String, String>): JsonObject {
        return rewriteElement(element, map) as JsonObject
    }

    private fun rewriteElement(element: JsonElement, map: Map<String, String>): JsonElement =
        when (element) {
            is JsonObject -> JsonObject(element.mapValues { (key, value) ->
                if (key == "\$ref" && value is JsonPrimitive) {
                    val ref = value.content
                    val prefix = "#/components/schemas/"
                    if (ref.startsWith(prefix)) {
                        val name = ref.removePrefix(prefix)
                        val replaced = map[name]
                        if (replaced != null) JsonPrimitive("$prefix$replaced") else value
                    } else value
                } else rewriteElement(value, map)
            })
            is JsonArray -> JsonArray(element.map { rewriteElement(it, map) })
            else -> element
        }

    /**
     * Remove the "tags" field from every operation so that openapi-generator
     * collects all operations into a single DefaultApi class.
     */
    private fun stripTags(root: JsonObject): JsonObject {
        val paths = root["paths"] as? JsonObject ?: return root
        val stripped = JsonObject(paths.mapValues { (_, item) ->
            val path = item as? JsonObject ?: return@mapValues item
            JsonObject(path.mapValues { (_, op) ->
                val obj = op as? JsonObject ?: return@mapValues op
                if ("tags" !in obj) return@mapValues op
                JsonObject(obj.filterKeys { it != "tags" })
            })
        })
        return JsonObject(root + mapOf("paths" to stripped))
    }

    /**
     * Deduplicate the root-level "tags" array by name — the spec validator
     * rejects repeated tag names even when they describe different things.
     */
    private fun dedupRootTags(root: JsonObject): JsonObject {
        val tags = root["tags"] as? JsonArray ?: return root
        val seen = mutableSetOf<String>()
        val deduped = tags.filter { tag ->
            val name = (tag as? JsonObject)?.let { (it["name"] as? JsonPrimitive)?.content }
                ?: return@filter true
            seen.add(name)
        }
        return JsonObject(root + mapOf("tags" to JsonArray(deduped)))
    }
}
