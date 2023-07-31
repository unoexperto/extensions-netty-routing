//package reactor.netty.http.server
package com.walkmind.extensions.netty

import com.squareup.moshi.Moshi
import java.lang.reflect.Type

interface JsonMapper {
    fun serialize(type: Type, value: Any): String
    fun <T> deserialize(type: Type, value: String): T?
}

class MoshiMapper(private val moshi: Moshi) : JsonMapper {

    override fun serialize(type: Type, value: Any): String {
        return moshi.adapter<Any>(type).toJson(value)
    }

    override fun <T> deserialize(type: Type, value: String): T? {
        return moshi.adapter<T>(type).fromJson(value)
    }
}

interface StringParser {
    fun <T> parse(type: Type, value: String): T
}
