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

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import kotlin.text.Charsets.UTF_8

/**
 * Represents a URI pattern where parameters are substituted with `{paramName}` expressions
 */
class UriPattern(private val uriPattern: String) {
    private val pathMatcher: Regex
    private val queryParamMatchers: Map<String, Regex> // TODO: handle multiple occurrences of the same parameter

    init {
        val uri = URI(uriPattern.replace(PARAM_MATCHER, PARAM_PLACEHOLDER))

        pathMatcher = uri.path.urlDecode().replace(PARAM_PLACEHOLDER, ANY_VALUE_PATTERN).toRegex()
        queryParamMatchers = uri.query?.run {
            split(PARAM_SEPARATOR)
                .asSequence()
                .map { it.split(NAME_VALUE_SEPARATOR, limit = 2) }
                .associate { (name, value) ->
                    name to when {
                        value == PARAM_PLACEHOLDER -> ANY_VALUE_PATTERN
                        value.contains(PARAM_PLACEHOLDER) -> value.urlDecode().replace(PARAM_PLACEHOLDER, ANY_VALUE_PATTERN)
                        else -> Regex.escape(value.urlDecode())
                    }.toRegex()
                }
        } ?: emptyMap()
    }

    /**
     * Checks if the provided [uri] matches this URI pattern
     */
    fun matches(uri: String): Boolean {
        val parsedUri = URI(uri)

        if (!pathMatcher.matches(parsedUri.path.urlDecode())) {
            return false
        }

        val uriQueryParams = parsedUri.query?.run {
            split(PARAM_SEPARATOR)
                .map { it.split(NAME_VALUE_SEPARATOR, limit = 2) }
                .associate { (name, value) -> name to value.urlDecode() }
        } ?: emptyMap()

        if (!queryParamMatchers.keys.containsAll(uriQueryParams.keys)) {
            return false
        }

        queryParamMatchers.forEach { (paramName, paramMatcher) ->
            val paramValue = uriQueryParams[paramName] ?: return false

            if (!paramMatcher.matches(paramValue)) {
                return false
            }
        }

        return true
    }

    /**
     * Resolves this URI pattern by substituting parameter placeholders with with values from [paramValues]
     */
    fun resolve(paramValues: Map<String, Any?>): String = PARAM_MATCHER.replace(uriPattern) {
        val paramName = it.groups[NAME_GROUP]!!.value
        requireNotNull(paramValues[paramName]) { "Missing URI parameter: $paramName" }.toString().urlEncode()
    }

    override fun toString(): String {
        return "${pathMatcher.pattern}?${queryParamMatchers.entries.joinToString(separator = PARAM_SEPARATOR.toString()) {
            "${it.key}$NAME_VALUE_SEPARATOR${it.value.pattern}"
        }}"
    }

    companion object {
        private const val PARAM_SEPARATOR = '&'
        private const val NAME_VALUE_SEPARATOR = '='
        private const val NAME_GROUP = "name"
        private const val PARAM_PLACEHOLDER = "__PARAM__"
        private const val ANY_VALUE_PATTERN = "[^\\/&?]+"
        private val PARAM_MATCHER = """\{(?<$NAME_GROUP>\w+)\}""".toRegex()

        private fun String.urlDecode() = URLDecoder.decode(this, UTF_8)
        private fun String.urlEncode() = URLEncoder.encode(urlDecode(), UTF_8)
    }
}
