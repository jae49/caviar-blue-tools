/*
 * Copyright 2025 John Engelman
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

package cb.core.tools.sss.models

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Assertions

class SSSConfigTest {

    @Test
    fun `should create valid configuration with default values`() {
        val config = SSSConfig(threshold = 3, totalShares = 5)
        
        Assertions.assertEquals(3, config.threshold)
        Assertions.assertEquals(5, config.totalShares)
        Assertions.assertEquals(SSSConfig.DEFAULT_SECRET_MAX_SIZE, config.secretMaxSize)
        Assertions.assertEquals(SSSConfig.DEFAULT_FIELD_SIZE, config.fieldSize)
        Assertions.assertTrue(config.useSecureRandom)
        Assertions.assertEquals(2, config.redundancy)
        Assertions.assertFalse(config.isTrivial)
    }

    @Test
    fun `should create valid configuration with custom values`() {
        val config = SSSConfig(
            threshold = 10,
            totalShares = 20,
            secretMaxSize = 512,
            fieldSize = 256,
            useSecureRandom = false
        )
        
        Assertions.assertEquals(10, config.threshold)
        Assertions.assertEquals(20, config.totalShares)
        Assertions.assertEquals(512, config.secretMaxSize)
        Assertions.assertEquals(256, config.fieldSize)
        Assertions.assertFalse(config.useSecureRandom)
        Assertions.assertEquals(10, config.redundancy)
    }

    @Test
    fun `should reject invalid threshold values`() {
        assertThrows<IllegalArgumentException> {
            SSSConfig(threshold = 0, totalShares = 5)
        }.also {
            Assertions.assertTrue(it.message!!.contains("Threshold must be positive"))
        }

        assertThrows<IllegalArgumentException> {
            SSSConfig(threshold = -1, totalShares = 5)
        }.also {
            Assertions.assertTrue(it.message!!.contains("Threshold must be positive"))
        }
    }

    @Test
    fun `should reject invalid total shares values`() {
        assertThrows<IllegalArgumentException> {
            SSSConfig(threshold = 1, totalShares = 0)
        }.also {
            Assertions.assertTrue(it.message!!.contains("Total shares must be positive"))
        }

        assertThrows<IllegalArgumentException> {
            SSSConfig(threshold = 1, totalShares = -1)
        }.also {
            Assertions.assertTrue(it.message!!.contains("Total shares must be positive"))
        }
    }

    @Test
    fun `should reject threshold greater than total shares`() {
        assertThrows<IllegalArgumentException> {
            SSSConfig(threshold = 6, totalShares = 5)
        }.also {
            Assertions.assertTrue(it.message!!.contains("Threshold (6) cannot exceed total shares (5)"))
        }
    }

    @Test
    fun `should reject total shares exceeding maximum`() {
        assertThrows<IllegalArgumentException> {
            SSSConfig(threshold = 100, totalShares = 129)
        }.also {
            Assertions.assertTrue(it.message!!.contains("Total shares (129) cannot exceed maximum allowed (128)"))
        }
    }

    @Test
    fun `should reject invalid secret max size`() {
        assertThrows<IllegalArgumentException> {
            SSSConfig(threshold = 2, totalShares = 3, secretMaxSize = 0)
        }.also {
            Assertions.assertTrue(it.message!!.contains("Secret max size must be positive"))
        }

        assertThrows<IllegalArgumentException> {
            SSSConfig(threshold = 2, totalShares = 3, secretMaxSize = 1025)
        }.also {
            Assertions.assertTrue(it.message!!.contains("Secret max size (1025) cannot exceed maximum allowed (1024)"))
        }
    }

    @Test
    fun `should reject unsupported field sizes`() {
        assertThrows<IllegalArgumentException> {
            SSSConfig(threshold = 2, totalShares = 3, fieldSize = 512)
        }.also {
            Assertions.assertTrue(it.message!!.contains("Currently only GF(256) is supported"))
        }
    }

    @Test
    fun `should validate secret size correctly`() {
        val config = SSSConfig(threshold = 2, totalShares = 3, secretMaxSize = 100)
        
        Assertions.assertTrue(config.validateSecretSize(1))
        Assertions.assertTrue(config.validateSecretSize(50))
        Assertions.assertTrue(config.validateSecretSize(100))
        
        Assertions.assertFalse(config.validateSecretSize(0))
        Assertions.assertFalse(config.validateSecretSize(101))
        Assertions.assertFalse(config.validateSecretSize(-1))
    }

    @Test
    fun `should identify trivial configurations`() {
        // k = 1 is trivial (any single share reveals secret)
        val config1 = SSSConfig(threshold = 1, totalShares = 5)
        Assertions.assertTrue(config1.isTrivial)
        
        // k = n is trivial (all shares required)
        val config2 = SSSConfig(threshold = 5, totalShares = 5)
        Assertions.assertTrue(config2.isTrivial)
        
        // k = 1, n = 1 is double trivial
        val config3 = SSSConfig(threshold = 1, totalShares = 1)
        Assertions.assertTrue(config3.isTrivial)
        
        // Normal configuration
        val config4 = SSSConfig(threshold = 3, totalShares = 5)
        Assertions.assertFalse(config4.isTrivial)
    }

    @Test
    fun `should create configuration using factory methods`() {
        val config1 = SSSConfig.create(3, 5)
        Assertions.assertEquals(3, config1.threshold)
        Assertions.assertEquals(5, config1.totalShares)
        Assertions.assertEquals(SSSConfig.DEFAULT_SECRET_MAX_SIZE, config1.secretMaxSize)
        Assertions.assertTrue(config1.useSecureRandom)
        
        val config2 = SSSConfig.createAllRequired(7)
        Assertions.assertEquals(7, config2.threshold)
        Assertions.assertEquals(7, config2.totalShares)
        Assertions.assertTrue(config2.isTrivial)
    }

    @Test
    fun `should handle edge case configurations`() {
        // Minimum configuration
        val minConfig = SSSConfig(threshold = 1, totalShares = 1)
        Assertions.assertEquals(0, minConfig.redundancy)
        Assertions.assertTrue(minConfig.isTrivial)
        
        // Maximum configuration
        val maxConfig = SSSConfig(threshold = 128, totalShares = 128)
        Assertions.assertEquals(0, maxConfig.redundancy)
        Assertions.assertTrue(maxConfig.isTrivial)
        
        // Maximum redundancy
        val maxRedundancy = SSSConfig(threshold = 1, totalShares = 128)
        Assertions.assertEquals(127, maxRedundancy.redundancy)
        Assertions.assertTrue(maxRedundancy.isTrivial)
    }
}