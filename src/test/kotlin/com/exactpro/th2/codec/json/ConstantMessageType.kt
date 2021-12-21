package com.exactpro.th2.codec.json

import com.exactpro.th2.codec.json.JsonPipelineCodecSettings.MessageTypeDetection.CONSTANT
import com.exactpro.th2.common.assertString
import com.exactpro.th2.common.grpc.AnyMessage
import com.exactpro.th2.common.grpc.MessageGroup
import com.exactpro.th2.common.grpc.RawMessage
import com.exactpro.th2.common.message.addField
import com.exactpro.th2.common.message.message
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.google.protobuf.ByteString
import getResourceAsStream
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
            Assertions.assertEquals("SimpleOne Value", json.get("SimpleOne")?.asText())
            Assertions.assertEquals("SimpleTwo Value", json.get("SimpleTwo")?.asText())
            Assertions.assertEquals("SimpleThree Value", json.get("SimpleThree")?.asText())
        }
    }

    @Test
    fun `simple constant messageType test encode with default values`() {
        val message = message("Constant_Default")
        val group = MessageGroup.newBuilder().addMessages(AnyMessage.newBuilder().setMessage(message)).build()
        val decodeResult = codec.encode(group)
        decodeResult.messagesList[0].rawMessage.apply {
            val json = mapper.readTree(body.toStringUtf8()) as ObjectNode
            Assertions.assertEquals("test1", json.get("SimpleOne")?.asText())
            Assertions.assertEquals("test2", json.get("SimpleTwo")?.asText())
            Assertions.assertEquals("test3", json.get("SimpleThree")?.asText())
        }
    }


    companion object {
        val codec = JsonPipelineCodecFactory().apply {
            init(getResourceAsStream("constant_message.xml"))
        }.create(JsonPipelineCodecSettings(CONSTANT, constantMessageType = "Constant"))
        val mapper = ObjectMapper()
    }
}