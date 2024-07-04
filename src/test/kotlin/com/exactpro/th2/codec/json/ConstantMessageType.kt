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

import com.exactpro.th2.codec.api.DictionaryAlias
import com.exactpro.th2.codec.api.IPipelineCodecContext
import com.exactpro.th2.codec.api.impl.ReportingContext
import com.exactpro.th2.codec.json.JsonPipelineCodecSettings.MessageTypeDetection.CONSTANT
import com.exactpro.th2.common.assertString
import com.exactpro.th2.common.grpc.AnyMessage
import com.exactpro.th2.common.grpc.MessageGroup
import com.exactpro.th2.common.grpc.RawMessage
import com.exactpro.th2.common.message.addField
import com.exactpro.th2.common.message.message
import com.exactpro.th2.common.message.messageType
import com.exactpro.th2.common.schema.dictionary.DictionaryType
import com.exactpro.th2.common.schema.grpc.router.GrpcRouter
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.google.protobuf.ByteString
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.InputStream

class ConstantMessageType {

    @Test
    fun `simple constant messageType test decode`() {
        val json = """{"SimpleOne":"SimpleOne Value", "SimpleTwo":"SimpleTwo Value", "SimpleThree":"SimpleThree Value"}"""
        val rawMessage = RawMessage.newBuilder().setBody(ByteString.copyFrom(json.toByteArray())).build()
        val group = MessageGroup.newBuilder().addMessages(AnyMessage.newBuilder().setRawMessage(rawMessage)).build()
        val decodeResult = codec.decode(group, ReportingContext())
        decodeResult.messagesList[0].message.apply {
            Assertions.assertEquals("Constant", messageType)
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
        val decodeResult = codec.encode(group, ReportingContext())
        decodeResult.messagesList[0].rawMessage.apply {
            val json = mapper.readTree(body.toStringUtf8()) as ObjectNode
            Assertions.assertEquals("SimpleOne Value", json.get("SimpleOne")?.asText())
            Assertions.assertEquals("SimpleTwo Value", json.get("SimpleTwo")?.asText())
            Assertions.assertEquals("SimpleThree Value", json.get("SimpleThree")?.asText())
        }
    }

    @Test
    fun `simple constant messageType test encode with default values`() {
        val message = message("Constant_Default")
        val group = MessageGroup.newBuilder().addMessages(AnyMessage.newBuilder().setMessage(message)).build()
        val decodeResult = codec.encode(group, ReportingContext())
        decodeResult.messagesList[0].rawMessage.apply {
            val json = mapper.readTree(body.toStringUtf8()) as ObjectNode
            Assertions.assertEquals("test1", json.get("SimpleOne")?.asText())
            Assertions.assertEquals("test2", json.get("SimpleTwo")?.asText())
            Assertions.assertEquals("test3", json.get("SimpleThree")?.asText())
        }
    }


    companion object {
        val codec = JsonPipelineCodecFactory().apply {
            init(object : IPipelineCodecContext {
                override fun get(alias: DictionaryAlias): InputStream = getResourceAsStream(alias)

                @Deprecated(
                    "Dictionary types will be removed in future releases of infra",
                    replaceWith = ReplaceWith("get(alias)")
                )
                override fun get(type: DictionaryType): InputStream = TODO("Not yet implemented")

                override fun getDictionaryAliases(): Set<String> = TODO("Not yet implemented")

                override fun getGrpcRouter(): GrpcRouter = TODO("Not yet implemented")

            })
        }.create(JsonPipelineCodecSettings(CONSTANT, constantMessageType = "Constant", dictionaryAlias = "constant_message.xml"))
        val mapper = ObjectMapper()
    }
}