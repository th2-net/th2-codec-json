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
import com.exactpro.sf.common.messages.structures.IDictionaryStructure
import com.exactpro.sf.common.messages.structures.IMessageStructure
import com.exactpro.sf.common.messages.structures.loaders.XmlDictionaryStructureLoader
import com.exactpro.sf.configuration.factory.JSONMessageFactory
import com.exactpro.sf.configuration.suri.SailfishURI
import com.exactpro.sf.extensions.get
import com.exactpro.sf.extensions.set
import com.fasterxml.jackson.core.JsonPointer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TestIMessagePathProvider {
    private val dictionary: IDictionaryStructure =
        TestIMessagePathProvider::class.java.classLoader.getResourceAsStream("test_dictionary.xml").use(XmlDictionaryStructureLoader()::load)
    private val factory: IMessageFactory = JSONMessageFactory().apply { init(SailfishURI.parse(dictionary.namespace), dictionary) }
    private val provider = IMessagePathProvider(factory)

    @Nested
    inner class Positive {

        @Test
        fun `returns null is no field in message`() {
            val source = factory.createMessage("Top").apply {
                set("Simple1", "42")
            }

            val jsonPointer = JsonPointer.compile("/Simple")

            val result = provider.get<String>(source, jsonPointer, structure(source))
            assertNull(result)
        }

        @Test
        fun `gets value from root`() {
            val source = factory.createMessage("Top").apply {
                set("Simple", "42")
            }

            val jsonPointer = JsonPointer.compile("/Simple")

            val result = provider.get<String>(source, jsonPointer, structure(source))
            assertEquals("42", result)
        }

        @Test
        fun `gets value from root collection`() {
            val source = factory.createMessage("Top").apply {
                set("SimpleCollection", listOf("42"))
            }

            val jsonPointer = JsonPointer.compile("/SimpleCollection/0")

            val result = provider.get<String>(source, jsonPointer, structure(source))
            assertEquals("42", result)
        }

        @Test
        fun `gets value from inner msg`() {
            val source = factory.createMessage("Top").apply {
                set("Complex", factory.createMessage("Complex1").apply {
                    set("Simple", "42")
                })
            }

            val jsonPointer = JsonPointer.compile("/Complex/Simple")

            val result = provider.get<String>(source, jsonPointer, structure(source))
            assertEquals("42", result)
        }

        @Test
        fun `gets value from collection in inner msg`() {
            val source = factory.createMessage("Top").apply {
                set("Complex", factory.createMessage("Complex1").apply {
                    set("SimpleCollection", listOf("42"))
                })
            }

            val jsonPointer = JsonPointer.compile("/Complex/SimpleCollection/0")

            val result = provider.get<String>(source, jsonPointer, structure(source))
            assertEquals("42", result)
        }

        @Test
        fun `gets value from inner msg collection`() {
            val source = factory.createMessage("Top").apply {
                set("ComplexCollection", listOf(
                    factory.createMessage("Complex1").apply {
                        set("Simple", "42")
                    }
                ))
            }

            val jsonPointer = JsonPointer.compile("/ComplexCollection/0/Simple")

            val result = provider.get<String>(source, jsonPointer, structure(source))
            assertEquals("42", result)
        }

        @Test
        fun `gets value from collection in inner msg collection`() {
            val source = factory.createMessage("Top").apply {
                set("ComplexCollection", listOf(
                    factory.createMessage("Complex1").apply {
                        set("SimpleCollection", listOf("42"))
                    }
                ))
            }

            val jsonPointer = JsonPointer.compile("/ComplexCollection/0/SimpleCollection/0")

            val result = provider.get<String>(source, jsonPointer, structure(source))
            assertEquals("42", result)
        }

        @Test
        fun `sets value to root`() {
            val source = factory.createMessage("Top")

            val jsonPointer = JsonPointer.compile("/Simple")

            provider.set(source, jsonPointer, structure(source), "42")
            assertEquals("42", source["Simple"])
        }

        @Test
        fun `does not replace existing value`() {
            val source = factory.createMessage("Top").apply {
                set("Simple", "43")
            }

            val jsonPointer = JsonPointer.compile("/Simple")

            provider.set(source, jsonPointer, structure(source), "42", replaceIfExist = false)
            assertEquals("43", source["Simple"])
        }

        @Test
        fun `sets value to root collection`() {
            val source = factory.createMessage("Top")

            val jsonPointer = JsonPointer.compile("/SimpleCollection/0")

            provider.set(source, jsonPointer, structure(source), "42")
            val collection: List<Any>? = source["SimpleCollection"]
            assertNotNull(collection)
            assertEquals(1, collection.size) { "Unexpected collection size: $collection" }
            assertEquals("42", collection[0])
        }

        @Test
        fun `sets value in inner message`() {
            val source = factory.createMessage("Top")

            val jsonPointer = JsonPointer.compile("/Complex/Simple")

            provider.set(source, jsonPointer, structure(source), "42")
            val inner: IMessage? = source["Complex"]
            assertNotNull(inner)
            assertEquals("42", inner["Simple"])
        }

        @Test
        fun `sets value in inner message collection`() {
            val source = factory.createMessage("Top")

            val jsonPointer = JsonPointer.compile("/Complex/SimpleCollection/0")

            provider.set(source, jsonPointer, structure(source), "42")
            val inner: IMessage? = source["Complex"]
            assertNotNull(inner)
            val collection: List<Any>? = inner["SimpleCollection"]
            assertNotNull(collection)
            assertEquals(1, collection.size) { "Unexpected collection size: $collection" }
            assertEquals("42", collection[0])
        }

        @Test
        fun `sets value in collection in inner message collection`() {
            val source = factory.createMessage("Top")

            val jsonPointer = JsonPointer.compile("/ComplexCollection/0/SimpleCollection/0")

            provider.set(source, jsonPointer, structure(source), "42")
            val innerCollection: List<IMessage>? = source["ComplexCollection"]
            assertNotNull(innerCollection)
            val inner = innerCollection[0]
            val collection: List<Any>? = inner["SimpleCollection"]
            assertNotNull(collection)
            assertEquals(1, collection.size) { "Unexpected collection size: $collection" }
            assertEquals("42", collection[0])
        }

        @Test
        fun `correctly choose the name`() {
            val source = factory.createMessage("AnotherTop").apply {
                set("Simple", "42")
            }

            val jsonPointer = JsonPointer.compile("/simple")

            val result = provider.get<String>(source, jsonPointer, structure(source))
            assertEquals("42", result)
        }
    }

    @Nested
    inner class Negative {
        @Test
        fun `reports unknown field in path`() {
            val source = factory.createMessage("Top").apply {
                set("Simple", "42")
            }

            val jsonPointer = JsonPointer.compile("/Simple1")

            val ex = assertThrows<IllegalStateException> { provider.get<String>(source, jsonPointer, structure(source)) }
            assertEquals("cannot find a field Simple1 in the message Top", ex.message)
        }

        @Test
        fun `reports unknown index in collection`() {
            val source = factory.createMessage("Top").apply {
                set("SimpleCollection", listOf("42"))
            }

            val jsonPointer = JsonPointer.compile("/SimpleCollection/1")

            val ex = assertThrows<IllegalStateException> { provider.get<String>(source, jsonPointer, structure(source)) }
            assertEquals("cannot get element at index 1 in collection SimpleCollection with size 1", ex.message)
        }
    }

    private fun structure(source: IMessage): IMessageStructure =
        requireNotNull(dictionary.messages[source.name]) { "structure required" }
}