/*
 * Copyright 2020-2020 Exactpro (Exactpro Systems Limited)
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
import com.exactpro.sf.configuration.factory.JSONMessageFactory
import com.exactpro.sf.configuration.suri.SailfishURI
import com.exactpro.sf.extensions.get
import com.exactpro.sf.services.http.HTTPClientSettings
import com.exactpro.sf.services.http.HTTPMessageHelper.REQUEST_METHOD_ATTRIBUTE
import com.exactpro.sf.services.http.HTTPMessageHelper.REQUEST_RESPONSE_ATTRIBUTE
import com.exactpro.sf.services.http.HTTPMessageHelper.REQUEST_URI_ATTRIBUTE
import com.exactpro.sf.services.json.JSONDecoder
import com.exactpro.sf.services.json.JSONEncoder
import com.exactpro.th2.codec.MessageFactoryProxy
import com.exactpro.th2.codec.api.IPipelineCodec
import com.exactpro.th2.codec.api.IPipelineCodecSettings
import com.exactpro.th2.codec.util.toDebugString
import com.exactpro.th2.common.grpc.AnyMessage
import com.exactpro.th2.common.grpc.Direction.FIRST
import com.exactpro.th2.common.grpc.Direction.SECOND
import com.exactpro.th2.common.grpc.MessageGroup
import com.exactpro.th2.common.grpc.RawMessage
import com.exactpro.th2.common.message.plusAssign
import com.exactpro.th2.sailfish.utils.IMessageToProtoConverter
import com.exactpro.th2.sailfish.utils.ProtoToIMessageConverter
import com.google.protobuf.ByteString
import io.netty.channel.embedded.EmbeddedChannel

typealias MessageType = String

class JsonPipelineCodec : IPipelineCodec {
    override val protocol: String = PROTOCOL

    private lateinit var dictionary: IDictionaryStructure
    private lateinit var settings: JsonPipelineCodecSettings
    private lateinit var messageFactory: IMessageFactory
    private lateinit var protoConverter: ProtoToIMessageConverter
    private lateinit var encodeChannel: EmbeddedChannel
    private lateinit var decodeChannel: EmbeddedChannel
    private lateinit var requestInfos: Map<MessageType, MessageInfo>
    private lateinit var responseInfos: Map<MessageType, MessageInfo>

    override fun init(dictionary: IDictionaryStructure, settings: IPipelineCodecSettings?) {
        check(!this::dictionary.isInitialized) { "Codec is already initialized" }

        this.dictionary = dictionary
        this.settings = requireNotNull(settings as? JsonPipelineCodecSettings) {
            "settings is not an instance of ${JsonPipelineCodecSettings::class.java}: $settings"
        }

        SailfishURI.parse(dictionary.namespace).let { uri ->
            this.messageFactory = JSONMessageFactory().apply { init(uri, dictionary) }
            this.protoConverter = ProtoToIMessageConverter(MessageFactoryProxy(dictionary, messageFactory), dictionary, uri)
        }

        val jsonSettings = this.settings.toJsonSettings()

        this.encodeChannel = JSONEncoder(jsonSettings).run {
            init(CODEC_SETTINGS, messageFactory, dictionary, CLIENT_NAME)
            EmbeddedChannel(this)
        }

        this.decodeChannel = JSONDecoder(jsonSettings).run {
            init(CODEC_SETTINGS, messageFactory, dictionary, CLIENT_NAME)
            EmbeddedChannel(this)
        }

        val requestInfos = hashMapOf<MessageType, MessageInfo>()
        val responseInfos = hashMapOf<MessageType, MessageInfo>()

        dictionary.messages.values.asSequence()
            .filter { it.attributes.keys.containsAll(VALID_MESSAGE_ATTRIBUTES) }
            .forEach { message ->
                val responseType = message.attributes[REQUEST_RESPONSE_ATTRIBUTE]!!.value
                val method = message.attributes[REQUEST_METHOD_ATTRIBUTE]!!.value
                val uri = message.attributes[REQUEST_URI_ATTRIBUTE]!!.value.run {
                    runCatching(::UriPattern).getOrElse(fail("Failed to create URI pattern from: $this"))
                }

                check(responseType in dictionary.messages) { "Unknown response type: $responseType" }

                MessageInfo(method, uri).apply {
                    requestInfos[message.name] = this
                    responseInfos[responseType] = this
                }
            }

        this.requestInfos = requestInfos
        this.responseInfos = responseInfos
    }

    override fun encode(messageGroup: MessageGroup): MessageGroup {
        val messages = messageGroup.messagesList

        if (messages.isEmpty() || messages.none { it.message.metadata.protocol == PROTOCOL }) {
            return messageGroup
        }

        val builder = MessageGroup.newBuilder()

        for (message in messages) {
            if (message.message.metadata.protocol != PROTOCOL) {
                builder.addMessages(message)
                continue
            }

            val parsedMessage = message.message
            val metadata = parsedMessage.metadata
            val messageType = metadata.messageType

            checkNotNull(dictionary.messages[messageType]) { "Unknown message type: $messageType" }
            check(messageType in requestInfos || messageType in responseInfos) { "Message type is not a request or response: $messageType" }

            val sfMessage = protoConverter.fromProtoMessage(parsedMessage, true)
            val encodedMessage = encodeChannel.encode(sfMessage)
            val rawMessage = checkNotNull(encodedMessage.metaData.rawMessage) { "Encoded messages has no raw message in its metadata: $encodedMessage" }

            val additionalMetadataProperties = requestInfos[messageType]?.run {
                val paramMessage: IMessage? = sfMessage[REQUEST_URI_MESSAGE]
                val paramValues: Map<String, Any?> = paramMessage?.run { fieldNames.associateWith(::get) } ?: mapOf()
                mapOf(METHOD_METADATA_PROPERTY to method, URI_METADATA_PROPERTY to uri.resolve(paramValues))
            }

            builder += RawMessage.newBuilder().apply {
                body = ByteString.copyFrom(rawMessage)
                metadataBuilder.apply {
                    putAllProperties(metadata.propertiesMap)
                    additionalMetadataProperties?.run(::putAllProperties)
                    this.id = metadata.id
                    this.timestamp = metadata.timestamp
                    this.protocol = PROTOCOL
                }
            }
        }

        return builder.build()
    }

    override fun decode(messageGroup: MessageGroup): MessageGroup {
        val messages = messageGroup.messagesList

        if (messages.isEmpty() || messages.none(AnyMessage::hasRawMessage)) {
            return messageGroup
        }

        val builder = MessageGroup.newBuilder()

        for (message in messages) {
            if (!message.hasRawMessage()) {
                builder.addMessages(message)
                continue
            }

            val rawMessage = message.rawMessage
            val metadata = rawMessage.metadata
            val metadataProperties = metadata.propertiesMap
            val method = requireNotNull(metadataProperties[METHOD_METADATA_PROPERTY]) { "Message has no '$METHOD_METADATA_PROPERTY' metadata property: ${rawMessage.toDebugString()}" }
            val uri = requireNotNull(metadataProperties[URI_METADATA_PROPERTY]) { "Message has no '$URI_METADATA_PROPERTY' metadata property: ${rawMessage.toDebugString()}" }
            val messageId = metadata.id

            val messageType = when (val direction = messageId.direction) {
                FIRST -> responseInfos.entries.find { it.value.matches(method, uri) } ?: error("No response for request with '$method' method and URI matching: $uri")
                SECOND -> requestInfos.entries.find { it.value.matches(method, uri) } ?: error("No request with '$method' method and URI matching: $uri")
                else -> error("Unsupported message direction: $direction")
            }.key

            val decodedMessage = decodeChannel.decode(messageFactory.createMessage(messageType).apply {
                metaData.rawMessage = rawMessage.body.toByteArray()
            })

            builder += IMESSAGE_CONVERTER.toProtoMessage(decodedMessage).apply {
                metadataBuilder.apply {
                    putAllProperties(metadataProperties)
                    this.id = messageId
                    this.timestamp = metadata.timestamp
                    this.protocol = PROTOCOL
                }
            }
        }

        return builder.build()
    }

    override fun close() {
        encodeChannel.runCatching { close().sync() }
        decodeChannel.runCatching { close().sync() }
    }

    private data class MessageInfo(val method: String, val uri: UriPattern) {
        fun matches(method: String, uri: String): Boolean = this.method.equals(method, true) && this.uri.matches(uri)
    }

    companion object {
        private const val PROTOCOL = "json"
        private const val CLIENT_NAME = "codec"
        private const val REQUEST_URI_MESSAGE = REQUEST_URI_ATTRIBUTE

        private const val METHOD_METADATA_PROPERTY = "method"
        private const val URI_METADATA_PROPERTY = "uri"

        private val CODEC_SETTINGS = HTTPClientSettings()
        private val IMESSAGE_CONVERTER = IMessageToProtoConverter()
        private val VALID_MESSAGE_ATTRIBUTES = setOf(REQUEST_METHOD_ATTRIBUTE, REQUEST_URI_ATTRIBUTE, REQUEST_RESPONSE_ATTRIBUTE)

        private fun fail(message: String): (Throwable) -> Nothing = {
            throw IllegalStateException(message, it)
        }

        private fun EmbeddedChannel.encode(message: IMessage): IMessage = runCatching {
            check(writeOutbound(message)) { "Encoding did not produce any results" }
            check(outboundMessages().size == 1) { "Expected 1 result, but got: ${outboundMessages().size}" }
            readOutbound<IMessage>()
        }.getOrElse {
            releaseOutbound()
            throw IllegalStateException("Failed to encode message: $message", it)
        }

        private fun EmbeddedChannel.decode(message: IMessage) = runCatching {
            check(writeInbound(message)) { "Decoding did not produce any results" }
            check(inboundMessages().size == 1) { "Expected 1 result, but got: ${inboundMessages().size}" }
            readInbound<IMessage>()
        }.getOrElse {
            releaseInbound()
            throw IllegalStateException("Failed to decode message: $message", it)
        }
    }
}
