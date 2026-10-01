// Adapted from ovrport (GPLv3): small helpers to walk and rewrite ARSCLib's manifest JSON.
package app.gameport.core.patch.json

import com.reandroid.json.JSONArray
import com.reandroid.json.JSONObject

inline fun <reified T> JSONObject?.elem(name: String): T? = this?.opt(name).takeIf { it is T } as T?

inline fun <reified T> JSONArray?.elem(index: Int): T? = this?.opt(index).takeIf { it is T } as T?

inline fun <reified T> JSONObject?.take(name: String, block: T?.() -> T? = { this }): JSONObject? {
    this?.put(name, block(elem<T>(name)))
    return this
}

inline fun <reified T> JSONArray?.takeEach(
    condition: T?.() -> Boolean = { true },
    block: T?.() -> T? = { this },
): JSONArray? {
    this?.length()?.let { (it - 1).downTo(0) }?.forEach { i ->
        elem<T>(i).let {
            if (condition(it)) {
                block(it)?.let { value -> put(i, value) } ?: remove(i)
            }
        }
    }
    return this
}

inline fun <reified T> JSONArray?.elemEach(condition: T?.() -> Boolean = { true }): List<T> =
    this?.length()?.let { (it - 1).downTo(0) }?.mapNotNull { i -> elem<T>(i) }?.filter { condition(it) }?.toList()
        ?: listOf()

fun JSONObject?.named(name: String?): Boolean = elem<String>("name") == name

fun JSONObject?.takeNodes(block: JSONArray?.() -> JSONArray? = { this }): JSONObject? = take("nodes", block)

fun JSONObject?.takeAttributes(block: JSONArray?.() -> JSONArray? = { this }): JSONObject? = take("attributes", block)

fun JSONObject?.takeNodesEach(
    condition: JSONObject?.() -> Boolean = { true },
    block: JSONObject?.() -> JSONObject? = { this },
): JSONObject? = takeNodes { takeEach(condition, block) }

fun JSONObject?.nameAttribute(): String? =
    elem<JSONArray>("attributes").elemEach<JSONObject> { named("name") }.firstOrNull().elem<String>("data")

private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
private const val ATTR_NAME_ID = 16842755

/** An `<element android:name="value"/>` node, the shape of `<action>` and `<category>`. */
fun namedElement(element: String, value: String): JSONObject =
    JSONObject().put("node_type", "element").put("name", element).put(
        "attributes",
        JSONArray().put(
            JSONObject().put("name", "name").put("id", ATTR_NAME_ID)
                .put("uri", ANDROID_NS).put("prefix", "android")
                .put("value_type", "STRING").put("data", value),
        ),
    )

/** A manifest attribute: `android:<name>` when [id] is its framework resource id, else a plain one. */
class ManifestAttr(val name: String, val id: Int, val type: String, val data: Any)

/** An element node with attributes, in the shape ARSCLib's manifest JSON uses. */
fun element(name: String, vararg attributes: ManifestAttr, children: List<JSONObject> = emptyList()): JSONObject {
    val attrs = JSONArray()
    attributes.forEach { attr ->
        attrs.put(
            JSONObject().put("name", attr.name).put("id", attr.id)
                .put("uri", ANDROID_NS).put("prefix", "android")
                .put("value_type", attr.type).put("data", attr.data),
        )
    }
    val node = JSONObject().put("node_type", "element").put("name", name).put("attributes", attrs)
    if (children.isNotEmpty()) node.put("nodes", JSONArray().also { array -> children.forEach { array.put(it) } })
    return node
}

// Framework resource ids of the attributes used when adding components.
const val ATTR_NAME = 16842755
const val ATTR_EXPORTED = 16842768
const val ATTR_AUTHORITIES = 16842776
const val ATTR_INIT_ORDER = 16842778
const val ATTR_VALUE = 16842788
