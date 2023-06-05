/*
 * Copyright 2020-2022 Exactpro (Exactpro Systems Limited)
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

import com.exactpro.th2.codec.json.JsonPipelineCodecSettings.MessageTypeDetection.CONSTANT
import com.exactpro.th2.common.assertString
import com.exactpro.th2.common.grpc.AnyMessage
import com.exactpro.th2.common.grpc.MessageGroup
import com.exactpro.th2.common.grpc.RawMessage
import com.exactpro.th2.common.grpc.Value
import com.exactpro.th2.common.message.addField
import com.exactpro.th2.common.message.get
import com.exactpro.th2.common.message.message
import com.exactpro.th2.common.message.messageType
import com.exactpro.th2.common.value.add
import com.exactpro.th2.common.value.listValue
import com.exactpro.th2.common.value.nullValue
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.google.protobuf.ByteString
import com.google.protobuf.NullValue
import com.google.protobuf.Value.KindCase
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class ConstantMessageType {

    @Test
    fun `simple constant messageType test decode`() {
        val json = """{"SimpleOne":"SimpleOne Value", "SimpleTwo":"SimpleTwo Value", "SimpleThree":"SimpleThree Value"}"""
        val rawMessage = RawMessage.newBuilder().setBody(ByteString.copyFrom(json.toByteArray())).build()
        val group = MessageGroup.newBuilder().addMessages(AnyMessage.newBuilder().setRawMessage(rawMessage)).build()
        val decodeResult = codec.decode(group)
        decodeResult.messagesList[0].message.apply {
            assertEquals("Constant", messageType)
            assertString("SimpleOne", "SimpleOne Value")
            assertString("SimpleTwo", "SimpleTwo Value")
            assertString("SimpleThree", "SimpleThree Value")
        }
    }

    @Test
    fun `simple constant messageType test encode`() {
        val message = message("Constant").apply {
            addField("SimpleOne", "SimpleOne Value")
            addField("SimpleTwo", "SimpleTwo Value")
            addField("SimpleThree", "SimpleThree Value")
        }
        val group = MessageGroup.newBuilder().addMessages(AnyMessage.newBuilder().setMessage(message)).build()
        val decodeResult = codec.encode(group)
        decodeResult.messagesList[0].rawMessage.apply {
            val json = mapper.readTree(body.toStringUtf8()) as ObjectNode
            assertEquals("SimpleOne Value", json.get("SimpleOne")?.asText())
            assertEquals("SimpleTwo Value", json.get("SimpleTwo")?.asText())
            assertEquals("SimpleThree Value", json.get("SimpleThree")?.asText())
        }
    }

    @Test
    fun `simple constant messageType test encode with default values`() {
        val message = message("Constant_Default")
        val group = MessageGroup.newBuilder().addMessages(AnyMessage.newBuilder().setMessage(message)).build()
        val decodeResult = codec.encode(group)
        decodeResult.messagesList[0].rawMessage.apply {
            val json = mapper.readTree(body.toStringUtf8()) as ObjectNode
            assertEquals("test1", json.get("SimpleOne")?.asText())
            assertEquals("test2", json.get("SimpleTwo")?.asText())
            assertEquals("test3", json.get("SimpleThree")?.asText())
        }
    }

    @Test
    fun `simple constant messageType test decode with null values`() {
        val json = """{"SimpleOne": null, "SimpleTwo": null, "SimpleThree": null, "SimpleCollection": ["1", null, "2"]}"""
        val rawMessage = RawMessage.newBuilder().setBody(ByteString.copyFrom(json.toByteArray())).build()
        val group = MessageGroup.newBuilder().addMessages(AnyMessage.newBuilder().setRawMessage(rawMessage)).build()
        val decodeResult = codec.decode(group)
        decodeResult.messagesList[0].message.apply {
            assertEquals("Constant", messageType)
            assertEquals(get("SimpleOne")?.kindCase, Value.KindCase.NULL_VALUE)
            assertEquals(get("SimpleTwo")?.kindCase, Value.KindCase.NULL_VALUE)
            assertEquals(get("SimpleThree")?.kindCase, Value.KindCase.NULL_VALUE)
            val simpleCollection = get("SimpleCollection")
            assertNotNull(simpleCollection)
            assertTrue(simpleCollection.hasListValue())
            simpleCollection.listValue.valuesList.apply {
                assertEquals(3, size)
                assertEquals("1", get(0).simpleValue)
                assertEquals(Value.KindCase.NULL_VALUE, get(1).kindCase)
                assertEquals("2", get(2).simpleValue)
            }
        }
    }

    @Test
    fun `simple constant messageType test encode with null values`() {
        val message = message("Constant").apply {
            addField("SimpleOne", nullValue())
            addField("SimpleTwo", nullValue())
            addField("SimpleThree", nullValue())
            addField("SimpleCollection", listValue().add(nullValue()).add(Value.newBuilder().setSimpleValue("1")).build())
        }
        val group = MessageGroup.newBuilder().addMessages(AnyMessage.newBuilder().setMessage(message)).build()
        val decodeResult = codec.encode(group)

        decodeResult.messagesList[0].rawMessage.apply {
            val json = mapper.readTree(body.toStringUtf8()) as ObjectNode
            assertTrue(json.get("SimpleOne")?.isNull ?: false)
            assertTrue(json.get("SimpleTwo")?.isNull ?: false)
            assertTrue(json.get("SimpleThree")?.isNull ?: false)
            val simpleCollection = json.get("SimpleCollection")
            assertNotNull(simpleCollection)
            assertTrue(simpleCollection.isArray)
            val simpleCollectionElements = simpleCollection.elements()
            assertTrue(simpleCollectionElements.next()?.isNull ?: false)
            assertEquals("1", simpleCollectionElements.next()?.asText())
        }
    }

    companion object {
        val codec = JsonPipelineCodecFactory().apply {
            init(getResourceAsStream("constant_message.xml"))
        }.create(JsonPipelineCodecSettings(CONSTANT, constantMessageType = "Constant"))
        val mapper = ObjectMapper()
    }
}