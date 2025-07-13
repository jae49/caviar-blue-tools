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

import cb.core.tools.sss.models.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class SecretReconstructorTest {
    
    private val splitter = SecretSplitter()
    private val reconstructor = SecretReconstructor()
    
    @Test
    fun `reconstruct should recover original secret with threshold shares`() {
        val originalSecret = "test secret".toByteArray()
        val config = SSSConfig(threshold = 3, totalShares = 5)
        
        val splitResult = splitter.split(originalSecret, config)
        val shares = splitResult.getOrNull()?.shares?.take(3) ?: emptyList()
        
        val reconstructResult = reconstructor.reconstruct(shares)
        
        assertTrue(reconstructResult is SSSResult.Success)
        assertArrayEquals(originalSecret, (reconstructResult as SSSResult.Success).value)
    }
    
    @Test
    fun `reconstruct should work with any subset of threshold shares`() {
        val originalSecret = "flexible reconstruction".toByteArray()
        val config = SSSConfig(threshold = 3, totalShares = 5)
        
        val splitResult = splitter.split(originalSecret, config)
        val allShares = splitResult.getOrNull()?.shares ?: emptyList()
        
        // Test different combinations of 3 shares
        val combinations = listOf(
            listOf(0, 1, 2),
            listOf(1, 2, 3),
            listOf(0, 2, 4),
            listOf(1, 3, 4)
        )
        
        combinations.forEach { indices ->
            val shares = indices.map { allShares[it] }
            val result = reconstructor.reconstruct(shares)
            assertTrue(result is SSSResult.Success)
            assertArrayEquals(originalSecret, (result as SSSResult.Success).value)
        }
    }
    
    @Test
    fun `reconstruct should work with more than threshold shares`() {
        val originalSecret = "extra shares".toByteArray()
        val config = SSSConfig(threshold = 2, totalShares = 5)
        
        val splitResult = splitter.split(originalSecret, config)
        val shares = splitResult.getOrNull()?.shares?.take(4) ?: emptyList()
        
        val reconstructResult = reconstructor.reconstruct(shares)
        
        assertTrue(reconstructResult is SSSResult.Success)
        assertArrayEquals(originalSecret, (reconstructResult as SSSResult.Success).value)
    }
    
    @Test
    fun `reconstruct should fail with insufficient shares`() {
        val originalSecret = "insufficient".toByteArray()
        val config = SSSConfig(threshold = 3, totalShares = 5)
        
        val splitResult = splitter.split(originalSecret, config)
        val shares = splitResult.getOrNull()?.shares?.take(2) ?: emptyList()
        val metadata = splitResult.getOrNull()?.metadata
        
        val reconstructResult = reconstructor.reconstruct(shares, metadata)
        
        assertTrue(reconstructResult is SSSResult.Failure)
        assertEquals(SSSError.INSUFFICIENT_SHARES, (reconstructResult as SSSResult.Failure).error)
    }
    
    @Test
    fun `reconstruct should fail with empty shares`() {
        val reconstructResult = reconstructor.reconstruct(emptyList())
        
        assertTrue(reconstructResult is SSSResult.Failure)
        assertEquals(SSSError.INSUFFICIENT_SHARES, (reconstructResult as SSSResult.Failure).error)
    }
    
    @Test
    fun `reconstruct should fail with duplicate share indices`() {
        // Create a dummy metadata for testing
        val metadata = ShareMetadata(
            threshold = 3,
            totalShares = 5,
            secretSize = 3,
            secretHash = ByteArray(32) { 0 }
        )
        
        val share1 = SecretShare(index = 1, data = byteArrayOf(1, 2, 3), metadata = metadata)
        val share2 = SecretShare(index = 1, data = byteArrayOf(4, 5, 6), metadata = metadata) // Same index
        val share3 = SecretShare(index = 3, data = byteArrayOf(7, 8, 9), metadata = metadata)
        
        val reconstructResult = reconstructor.reconstruct(listOf(share1, share2, share3))
        
        assertTrue(reconstructResult is SSSResult.Failure)
        assertEquals(SSSError.INVALID_SHARE, (reconstructResult as SSSResult.Failure).error)
    }
    
    @Test
    fun `reconstruct should fail with inconsistent share sizes`() {
        // Create dummy metadata for testing
        val metadata1 = ShareMetadata(
            threshold = 3,
            totalShares = 5,
            secretSize = 3,
            secretHash = ByteArray(32) { 0 }
        )
        val metadata2 = ShareMetadata(
            threshold = 3,
            totalShares = 5,
            secretSize = 2,
            secretHash = ByteArray(32) { 0 }
        )
        
        val share1 = SecretShare(index = 1, data = byteArrayOf(1, 2, 3), metadata = metadata1)
        val share2 = SecretShare(index = 2, data = byteArrayOf(4, 5), metadata = metadata2) // Different size
        val share3 = SecretShare(index = 3, data = byteArrayOf(7, 8, 9), metadata = metadata1)
        
        val reconstructResult = reconstructor.reconstruct(listOf(share1, share2, share3))
        
        assertTrue(reconstructResult is SSSResult.Failure)
        assertEquals(SSSError.INCOMPATIBLE_SHARES, (reconstructResult as SSSResult.Failure).error)
    }
    
    @Test
    fun `reconstruct should validate against metadata when provided`() {
        val originalSecret = "validated secret".toByteArray()
        val config = SSSConfig(threshold = 3, totalShares = 5)
        
        val splitResult = splitter.split(originalSecret, config)
        val shares = splitResult.getOrNull()?.shares?.take(3) ?: emptyList()
        val metadata = splitResult.getOrNull()?.metadata
        
        val reconstructResult = reconstructor.reconstruct(shares, metadata)
        
        assertTrue(reconstructResult is SSSResult.Success)
        assertArrayEquals(originalSecret, (reconstructResult as SSSResult.Success).value)
    }
    
    @Test
    fun `reconstruct should handle single-byte secret`() {
        val originalSecret = byteArrayOf(42)
        val config = SSSConfig(threshold = 2, totalShares = 3)
        
        val splitResult = splitter.split(originalSecret, config)
        val shares = splitResult.getOrNull()?.shares?.take(2) ?: emptyList()
        
        val reconstructResult = reconstructor.reconstruct(shares)
        
        assertTrue(reconstructResult is SSSResult.Success)
        assertArrayEquals(originalSecret, (reconstructResult as SSSResult.Success).value)
    }
    
    @Test
    fun `reconstruct should handle maximum size secret`() {
        val originalSecret = ByteArray(1024) { it.toByte() }
        val config = SSSConfig(threshold = 5, totalShares = 10)
        
        val splitResult = splitter.split(originalSecret, config)
        val shares = splitResult.getOrNull()?.shares?.take(5) ?: emptyList()
        
        val reconstructResult = reconstructor.reconstruct(shares)
        
        assertTrue(reconstructResult is SSSResult.Success)
        assertArrayEquals(originalSecret, (reconstructResult as SSSResult.Success).value)
    }
    
    @Test
    fun `reconstruct should work with threshold = 1`() {
        val originalSecret = "minimum threshold".toByteArray()
        val config = SSSConfig(threshold = 1, totalShares = 5)
        
        val splitResult = splitter.split(originalSecret, config)
        val shares = splitResult.getOrNull()?.shares?.take(1) ?: emptyList()
        
        val reconstructResult = reconstructor.reconstruct(shares)
        
        assertTrue(reconstructResult is SSSResult.Success)
        assertArrayEquals(originalSecret, (reconstructResult as SSSResult.Success).value)
    }
    
    @Test
    fun `reconstruct should work with all shares when threshold = totalShares`() {
        val originalSecret = "all shares needed".toByteArray()
        val config = SSSConfig(threshold = 4, totalShares = 4)
        
        val splitResult = splitter.split(originalSecret, config)
        val shares = splitResult.getOrNull()?.shares ?: emptyList()
        
        val reconstructResult = reconstructor.reconstruct(shares)
        
        assertTrue(reconstructResult is SSSResult.Success)
        assertArrayEquals(originalSecret, (reconstructResult as SSSResult.Success).value)
    }
    
    @Test
    fun `reconstruct should handle binary data correctly`() {
        val originalSecret = ByteArray(256) { it.toByte() } // All possible byte values
        val config = SSSConfig(threshold = 3, totalShares = 5)
        
        val splitResult = splitter.split(originalSecret, config)
        val shares = splitResult.getOrNull()?.shares?.take(3) ?: emptyList()
        
        val reconstructResult = reconstructor.reconstruct(shares)
        
        assertTrue(reconstructResult is SSSResult.Success)
        assertArrayEquals(originalSecret, (reconstructResult as SSSResult.Success).value)
    }
}