/*
 * Copyright 2020-2021 Exactpro (Exactpro Systems Limited)
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

import com.exactpro.sf.common.messages.structures.IDictionaryStructure
import com.exactpro.sf.common.messages.structures.loaders.JsonYamlDictionaryStructureLoader
import com.exactpro.sf.common.messages.structures.loaders.XmlDictionaryStructureLoader
import com.exactpro.th2.codec.api.IPipelineCodec
import com.exactpro.th2.codec.api.IPipelineCodecFactory
import com.exactpro.th2.codec.api.IPipelineCodecSettings
import com.google.auto.service.AutoService
import mu.KotlinLogging
import java.io.InputStream

@AutoService(IPipelineCodecFactory::class)
@Suppress("unused")
class JsonPipelineCodecFactory : IPipelineCodecFactory {
    private lateinit var dictionary: IDictionaryStructure

    override val settingsClass: Class<out IPipelineCodecSettings> = JsonPipelineCodecSettings::class.java
    @Deprecated("Please migrate to the protocols property")
    override val protocol: String = PROTOCOL

    override fun init(dictionary: InputStream) {
        check(!this::dictionary.isInitialized) { "factory already initialized" }
        this.dictionary = dictionary.readAllBytes().run {
            runCatching {
                LOGGER.debug { "Loading dictionary as XML" }
                XmlDictionaryStructureLoader().load(inputStream())
            }.recoverCatching {
                LOGGER.warn(it) { "Cannot load dictionary as XML. Loading as JSON/YAML" }
                JsonYamlDictionaryStructureLoader().load(inputStream())
            }
        }.getOrThrow()
    }

    override fun create(settings: IPipelineCodecSettings?): IPipelineCodec {
        check(::dictionary.isInitialized) { "'dictionary' was not loaded" }
        return JsonPipelineCodec(dictionary, requireNotNull(settings as? JsonPipelineCodecSettings) {
            "settings is not an instance of ${JsonPipelineCodecSettings::class.java}: $settings"
        })
    }

    companion object {
        const val PROTOCOL = "json"
        private val LOGGER = KotlinLogging.logger { }
    }
}