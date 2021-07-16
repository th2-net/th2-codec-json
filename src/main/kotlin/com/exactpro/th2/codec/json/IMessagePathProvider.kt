/*
 * Copyright 2021-2021 Exactpro (Exactpro Systems Limited)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.exactpro.th2.codec.json

import com.exactpro.sf.common.messages.IMessage
import com.exactpro.sf.common.messages.IMessageFactory
import com.exactpro.sf.common.messages.structures.IFieldStructure
import com.exactpro.sf.common.messages.structures.IMessageStructure
import com.exactpro.sf.extensions.get
import com.exactpro.sf.extensions.set
import com.exactpro.sf.services.json.JSONVisitorUtility
import com.fasterxml.jackson.core.JsonPointer
import kotlin.reflect.KClass
import kotlin.reflect.cast

class IMessagePathProvider(
    private val factory: IMessageFactory
) {
    inline fun <reified T : Any> get(
        message: IMessage,
        path: JsonPointer,
        structure: IMessageStructure,
    ): T? {
        return get(message, path, structure, T::class)
    }

    fun <T : Any> get(
        message: IMessage,
        path: JsonPointer,
        structure: IMessageStructure,
        clazz: KClass<T>,
    ): T? {
        var current: Any? = message
        var currentStruct: IFieldStructure = structure
        var currentPath: JsonPointer = path
        do {
            val (struct, value ) = current.getPath(currentPath, currentStruct)
            current = value ?: return null // some structures do not exist
            currentStruct = struct
            currentPath = currentPath.tail()
        } while (!currentPath.matches())
        return current?.let(clazz::cast)
    }

    @Suppress("UNCHECKED_CAST")
    fun set(
        message: IMessage,
        path: JsonPointer,
        structure: IMessageStructure,
        value: Any,
        replaceIfExist: Boolean = true
    ) {
        var current: Any? = message
        var currentStruct: IFieldStructure = structure
        var currentPath: JsonPointer = path
        do {
            val next = currentPath.tail()
            val finalNode = next.matches()
            val (struct, existingValue) = current.getPath(currentPath, currentStruct, createMissing = true)
            if (finalNode) { // check if it was the end of the path
                if (existingValue != null && !replaceIfExist) return // value exists. Do not replace
                when (current) {
                    is IMessage -> current[struct.name] = value
                    is MutableList<*> -> (current as MutableList<Any?>)[currentPath.matchingIndex] = value
                    else -> error("unsupported match for type ${current?.let { it::class.java }}")
                }
                return
            }
            current = existingValue
            currentStruct = struct
            currentPath = next
        } while (!currentPath.matches())
    }

    private fun Any?.getPath(
        currentPath: JsonPointer,
        currentStruct: IFieldStructure,
        createMissing: Boolean = false
    ): Pair<IFieldStructure, Any?> = when (this) {
        is IMessage -> getPath(currentPath, currentStruct, createMissing)
        is MutableList<*> -> getPath(currentPath, currentStruct, createMissing)
        else -> error("cannot extract path from ${this?.let { it::class.java }} in field ${currentStruct.name}")
    }

    @Suppress("IMPLICIT_CAST_TO_ANY") // for when statement
    private fun IMessage.getPath(
        path: JsonPointer,
        structure: IFieldStructure,
        createMissing: Boolean = false
    ): Pair<IFieldStructure, Any?> {
        check(!path.mayMatchElement()) { "path must match the field but matches the element at index ${path.matchingIndex}" }
        val matchedFieldStruct = structure.fields.values.firstOrNull { field ->
            path.matchingProperty == JSONVisitorUtility.getJsonFieldName(field)
        }
        checkNotNull(matchedFieldStruct) { "cannot find a field ${path.matchingProperty} in the message ${structure.name}" }
        val result: Any? = with(matchedFieldStruct) {
            get(name) ?: when {
                !createMissing -> null
                isCollection -> arrayListOf<Any?>()
                isComplex -> factory.createMessage(referenceName)
                else -> null
            }?.also { set(name, it) }
        }
        return matchedFieldStruct to result
    }

    private fun MutableList<*>.getPath(
        path: JsonPointer,
        structure: IFieldStructure,
        createMissing: Boolean = false
    ): Pair<IFieldStructure, Any?> {
        check(path.mayMatchElement()) { "path must match the element in collection but matches ${path.matchingProperty} field" }
        @Suppress("UNCHECKED_CAST")
        this as MutableList<Any?>
        val matchingIndex = path.matchingIndex
        if (createMissing) {
            while (matchingIndex >= size) {
                add(if (structure.isComplex) factory.createMessage(structure.referenceName) else null)
            }
        }
        return structure to getOrElse(matchingIndex) {
            error("cannot get element at index $matchingIndex in collection ${structure.name} with size $size")
        }
    }
}
