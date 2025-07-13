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

package cb.core.tools.sss.crypto

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows

class SecureRandomGeneratorTest {
    
    private val generator = SecureRandomGenerator()
    
    @Test
    fun `nextByte should generate bytes in valid range`() {
        repeat(100) {
            val byte = generator.nextByte()
            // Byte values are in range [-128, 127]
            assertTrue(byte.toInt() >= -128)
            assertTrue(byte.toInt() <= 127)
        }
    }
    
    @Test
    fun `nextBytes should generate array of requested size`() {
        val sizes = listOf(0, 1, 10, 100, 1000)
        
        sizes.forEach { size ->
            val bytes = generator.nextBytes(size)
            assertEquals(size, bytes.size)
        }
    }
    
    @Test
    fun `nextBytes should reject negative count`() {
        val exception = assertThrows<IllegalArgumentException> {
            generator.nextBytes(-1)
        }
        
        assertEquals("Count must be non-negative", exception.message)
    }
    
    @Test
    fun `nextFieldElement should generate values in GF(256) range`() {
        repeat(1000) {
            val element = generator.nextFieldElement()
            assertTrue(element >= 0)
            assertTrue(element <= 255)
        }
    }
    
    @Test
    fun `nextFieldElements should generate array of requested size`() {
        val sizes = listOf(0, 1, 10, 100)
        
        sizes.forEach { size ->
            val elements = generator.nextFieldElements(size)
            assertEquals(size, elements.size)
            elements.forEach { element ->
                assertTrue(element >= 0)
                assertTrue(element <= 255)
            }
        }
    }
    
    @Test
    fun `nextFieldElements should reject negative count`() {
        val exception = assertThrows<IllegalArgumentException> {
            generator.nextFieldElements(-1)
        }
        
        assertEquals("Count must be non-negative", exception.message)
    }
    
    @Test
    fun `nextNonZeroFieldElement should never return zero`() {
        repeat(1000) {
            val element = generator.nextNonZeroFieldElement()
            assertTrue(element > 0)
            assertTrue(element <= 255)
        }
    }
    
    @Test
    fun `random generation should have good distribution`() {
        // Test that we get a reasonable distribution of values
        val fieldElements = IntArray(1000) { generator.nextFieldElement() }
        val uniqueValues = fieldElements.toSet().size
        
        // With 1000 samples from 256 values, we expect many unique values
        assertTrue(uniqueValues > 100)
    }
    
    @Test
    fun `successive calls should produce different values`() {
        // Test randomness by checking that successive calls differ
        val bytes1 = generator.nextBytes(10)
        val bytes2 = generator.nextBytes(10)
        
        assertFalse(bytes1.contentEquals(bytes2))
        
        val elements1 = generator.nextFieldElements(10)
        val elements2 = generator.nextFieldElements(10)
        
        assertFalse(elements1.contentEquals(elements2))
    }
    
    @Test
    fun `nextByte should produce all possible byte values eventually`() {
        // Statistical test - with enough samples, we should see various byte values
        val seenBytes = mutableSetOf<Byte>()
        repeat(10000) {
            seenBytes.add(generator.nextByte())
        }
        
        // We should see a good portion of possible byte values
        assertTrue(seenBytes.size > 200)
    }
    
    @Test
    fun `nextFieldElement should produce well-distributed values`() {
        // Test distribution across buckets
        val buckets = IntArray(4) // 0-63, 64-127, 128-191, 192-255
        repeat(4000) {
            val element = generator.nextFieldElement()
            buckets[element / 64]++
        }
        
        // Each bucket should have roughly 1000 values (±200 for randomness)
        buckets.forEach { count ->
            assertTrue(count > 800)
            assertTrue(count < 1200)
        }
    }
}