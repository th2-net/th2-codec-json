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

import com.exactpro.sf.services.json.JsonSettings
import com.exactpro.th2.codec.api.IPipelineCodecSettings
import com.exactpro.th2.codec.json.JsonPipelineCodecSettings.MessageTypeDetection.BY_HTTP_METHOD_AND_URI
import com.exactpro.th2.codec.json.JsonPipelineCodecSettings.MessageTypeDetection.BY_INNER_FIELD

data class JsonPipelineCodecSettings(
    val messageTypeDetection: MessageTypeDetection = BY_HTTP_METHOD_AND_URI,
    val messageTypeField: String = "",
    val rejectUnexpectedFields: Boolean = true,
    val treatSimpleValuesAsStrings: Boolean = false
) : IPipelineCodecSettings {
    init {
        if (messageTypeDetection == BY_INNER_FIELD) {
            check(messageTypeField.isNotBlank()) { "${::messageTypeDetection.name} is $BY_INNER_FIELD but ${::messageTypeField.name} is blank" }
        }
    }

    fun toJsonSettings(): JsonSettings = JsonSettings().apply {
        isRejectUnexpectedFields = rejectUnexpectedFields
        isTreatSimpleValuesAsStrings = treatSimpleValuesAsStrings
    }

    enum class MessageTypeDetection {
        BY_HTTP_METHOD_AND_URI,
        BY_INNER_FIELD
    }
}
