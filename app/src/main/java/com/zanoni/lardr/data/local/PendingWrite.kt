package com.zanoni.lardr.data.local

import org.json.JSONObject

data class PendingWrite(
    val id: String,
    val type: WriteType,
    val storeId: String,
    val params: Map<String, String>
) {
    fun toJson(): String = JSONObject().apply {
        put("id", id)
        put("type", type.name)
        put("storeId", storeId)
        put("params", JSONObject(params))
    }.toString()

    companion object {
        fun fromJson(json: String): PendingWrite? = try {
            val obj = JSONObject(json)
            val paramsObj = obj.getJSONObject("params")
            val params = paramsObj.keys().asSequence().associateWith { paramsObj.getString(it) }
            PendingWrite(
                id = obj.getString("id"),
                type = WriteType.valueOf(obj.getString("type")),
                storeId = obj.getString("storeId"),
                params = params
            )
        } catch (e: Exception) {
            null
        }
    }
}

enum class WriteType {
    STORE_NAME,
    INGREDIENT_BOUGHT,
    INGREDIENT_DELETE
}