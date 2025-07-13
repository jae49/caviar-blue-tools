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

package cb.core.tools.sss

import cb.core.tools.sss.models.SSSConfig
import cb.core.tools.sss.models.SSSResult
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class EdgeCaseSecretTest {
    
    private val sss = ShamirSecretSharing()
    
    @Test
    fun `split and reconstruct should handle all-zero secrets`() {
        val sizes = listOf(1, 10, 100, 500, 1024)
        val configs = listOf(
            SSSConfig(3, 5),
            SSSConfig(2, 3),
            SSSConfig(5, 10)
        )
        
        for (size in sizes) {
            for (config in configs) {
                val secret = ByteArray(size) { 0 }
                val splitResult = sss.split(secret, config)
                assertTrue(splitResult is SSSResult.Success)
                
                val shares = (splitResult as SSSResult.Success).value.shares
                val reconstructResult = sss.reconstruct(shares.take(config.threshold))
                assertTrue(reconstructResult is SSSResult.Success)
                
                val reconstructed = (reconstructResult as SSSResult.Success).value
                assertArrayEquals(secret, reconstructed)
            }
        }
    }
    
    @Test
    fun `split and reconstruct should handle all-0xFF secrets`() {
        val sizes = listOf(1, 10, 100, 500, 1024)
        val configs = listOf(
            SSSConfig(3, 5),
            SSSConfig(2, 3),
            SSSConfig(5, 10)
        )
        
        for (size in sizes) {
            for (config in configs) {
                val secret = ByteArray(size) { 0xFF.toByte() }
                val splitResult = sss.split(secret, config)
                assertTrue(splitResult is SSSResult.Success)
                
                val shares = (splitResult as SSSResult.Success).value.shares
                val reconstructResult = sss.reconstruct(shares.take(config.threshold))
                assertTrue(reconstructResult is SSSResult.Success)
                
                val reconstructed = (reconstructResult as SSSResult.Success).value
                assertArrayEquals(secret, reconstructed)
            }
        }
    }
    
    @Test
    fun `split and reconstruct should handle repeating pattern secrets`() {
        val patterns = listOf(
            byteArrayOf(0x00, 0xFF.toByte()),
            byteArrayOf(0xAA.toByte(), 0x55),
            byteArrayOf(0x01, 0x02, 0x03, 0x04),
            byteArrayOf(0xFF.toByte(), 0x00, 0xFF.toByte(), 0x00)
        )
        val config = SSSConfig(3, 5)
        
        for (pattern in patterns) {
            val secret = ByteArray(256) { pattern[it % pattern.size] }
            val splitResult = sss.split(secret, config)
            assertTrue(splitResult is SSSResult.Success)
            
            val shares = (splitResult as SSSResult.Success).value.shares
            val reconstructResult = sss.reconstruct(shares.take(config.threshold))
            assertTrue(reconstructResult is SSSResult.Success)
            
            val reconstructed = (reconstructResult as SSSResult.Success).value
            assertArrayEquals(secret, reconstructed)
        }
    }
    
    @Test
    fun `split and reconstruct should handle power-of-2 sized secrets`() {
        val sizes = listOf(1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024)
        val config = SSSConfig(3, 5)
        
        for (size in sizes) {
            val secret = ByteArray(size) { (it % 256).toByte() }
            val splitResult = sss.split(secret, config)
            assertTrue(splitResult is SSSResult.Success)
            
            val shares = (splitResult as SSSResult.Success).value.shares
            val reconstructResult = sss.reconstruct(shares.take(config.threshold))
            assertTrue(reconstructResult is SSSResult.Success)
            
            val reconstructed = (reconstructResult as SSSResult.Success).value
            assertArrayEquals(secret, reconstructed)
        }
    }
    
    @Test
    fun `split and reconstruct should handle alternating bit patterns`() {
        val patterns = listOf(
            0b10101010.toByte(), // 0xAA
            0b01010101.toByte(), // 0x55
            0b11110000.toByte(), // 0xF0
            0b00001111.toByte(), // 0x0F
            0b11001100.toByte(), // 0xCC
            0b00110011.toByte()  // 0x33
        )
        val config = SSSConfig(4, 8)
        
        for (pattern in patterns) {
            val secret = ByteArray(100) { pattern }
            val splitResult = sss.split(secret, config)
            assertTrue(splitResult is SSSResult.Success)
            
            val shares = (splitResult as SSSResult.Success).value.shares
            val reconstructResult = sss.reconstruct(shares.take(config.threshold))
            assertTrue(reconstructResult is SSSResult.Success)
            
            val reconstructed = (reconstructResult as SSSResult.Success).value
            assertArrayEquals(secret, reconstructed)
        }
    }
    
    @Test
    fun `split and reconstruct should handle boundary byte values`() {
        val config = SSSConfig(2, 4)
        
        // Create secret with all possible byte values
        val secret = ByteArray(256) { it.toByte() }
        val splitResult = sss.split(secret, config)
        assertTrue(splitResult is SSSResult.Success)
        
        val shares = (splitResult as SSSResult.Success).value.shares
        val reconstructResult = sss.reconstruct(shares.take(config.threshold))
        assertTrue(reconstructResult is SSSResult.Success)
        
        val reconstructed = (reconstructResult as SSSResult.Success).value
        assertArrayEquals(secret, reconstructed)
    }
    
    @Test
    fun `split and reconstruct should handle secrets with sparse data`() {
        val config = SSSConfig(3, 6)
        
        // Create secrets with mostly zeros and a few non-zero bytes
        val sparsePositions = listOf(
            listOf(0, 99),           // First and last
            listOf(50),              // Middle only
            listOf(10, 20, 30, 40),  // Evenly spaced
            listOf(0, 1, 98, 99)     // Edges
        )
        
        for (positions in sparsePositions) {
            val secret = ByteArray(100) { 0 }
            positions.forEach { pos ->
                secret[pos] = 0xFF.toByte()
            }
            
            val splitResult = sss.split(secret, config)
            assertTrue(splitResult is SSSResult.Success)
            
            val shares = (splitResult as SSSResult.Success).value.shares
            val reconstructResult = sss.reconstruct(shares.take(config.threshold))
            assertTrue(reconstructResult is SSSResult.Success)
            
            val reconstructed = (reconstructResult as SSSResult.Success).value
            assertArrayEquals(secret, reconstructed)
        }
    }
    
    @Test
    fun `split and reconstruct should handle high entropy random secrets`() {
        val sizes = listOf(10, 100, 500, 1024)
        val config = SSSConfig(5, 10)
        
        for (size in sizes) {
            // Create a pseudo-random but deterministic secret
            val secret = ByteArray(size) { i ->
                ((i * 31 + 17) % 256).toByte()
            }
            
            val splitResult = sss.split(secret, config)
            assertTrue(splitResult is SSSResult.Success)
            
            val shares = (splitResult as SSSResult.Success).value.shares
            val reconstructResult = sss.reconstruct(shares.take(config.threshold))
            assertTrue(reconstructResult is SSSResult.Success)
            
            val reconstructed = (reconstructResult as SSSResult.Success).value
            assertArrayEquals(secret, reconstructed)
        }
    }
    
    @Test
    fun `split and reconstruct should handle secrets with ASCII text patterns`() {
        val textPatterns = listOf(
            "A".repeat(100),
            "Hello World!".repeat(10),
            "1234567890".repeat(20),
            "The quick brown fox jumps over the lazy dog.",
            "\n\r\t ".repeat(50),
            "!@#$%^&*()".repeat(10)
        )
        val config = SSSConfig(3, 5)
        
        for (text in textPatterns) {
            val secret = text.toByteArray()
            val splitResult = sss.split(secret, config)
            assertTrue(splitResult is SSSResult.Success)
            
            val shares = (splitResult as SSSResult.Success).value.shares
            val reconstructResult = sss.reconstruct(shares.take(config.threshold))
            assertTrue(reconstructResult is SSSResult.Success)
            
            val reconstructed = (reconstructResult as SSSResult.Success).value
            assertArrayEquals(secret, reconstructed)
            assertEquals(text, String(reconstructed))
        }
    }
}