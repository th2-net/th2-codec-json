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

import com.exactpro.th2.common.grpc.Message
import com.exactpro.th2.common.message.getString
import com.exactpro.th2.common.message.messageType
import java.io.InputStream
import kotlin.test.fail
import org.junit.jupiter.api.Assertions

fun getResourceAsStream(path: String): InputStream {
    return String.Companion::class.java.classLoader.getResourceAsStream(path) ?: fail("Resource [$path] is required")
}

fun Message.assertString(name: String, expected: String? = null): String {
    this.assertContains(name)
    val actual = this.getString(name)!!
    expected?.let {
        Assertions.assertEquals(expected, actual) {"Unexpected $name field value"}
    }
    return actual
}

fun Message.assertContains(vararg name: String) {
    name.forEach { fieldName ->
        if (!this.containsFields(fieldName)) {
            org.junit.jupiter.api.fail { "$messageType:$fieldName expected: not <null>" }
        }
    }
}