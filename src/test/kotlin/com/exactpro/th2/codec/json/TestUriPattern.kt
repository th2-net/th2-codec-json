/*
 * Copyright 2022 Exactpro (Exactpro Systems Limited)
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

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TestUriPattern {
    @Test
    fun `matches the uri with partial parameter substitution`() {
        val pattern = UriPattern("/automic/send?id=test-{a}-event-{b}")
        val uri = "http://localhost:8080/automic/send?id=test-some-data-event-another-data"
        assertTrue(pattern.matches(uri)) {
            "Pattern $pattern does not match the URI: $uri"
        }
    }
}