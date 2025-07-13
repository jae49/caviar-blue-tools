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

class ExtremeConfigurationTest {
    
    private val sss = ShamirSecretSharing()
    
    @Test
    fun `split and reconstruct with k=1 n=1 configuration`() {
        val config = SSSConfig(1, 1)
        val secret = "Single share secret".toByteArray()
        
        val splitResult = sss.split(secret, config)
        assertTrue(splitResult is SSSResult.Success)
        
        val shares = (splitResult as SSSResult.Success).value.shares
        assertEquals(1, shares.size)
        
        val reconstructResult = sss.reconstruct(shares)
        assertTrue(reconstructResult is SSSResult.Success)
        
        val reconstructed = (reconstructResult as SSSResult.Success).value
        assertArrayEquals(secret, reconstructed)
    }
    
    @Test
    fun `split and reconstruct with k=1 n=2 configuration`() {
        val config = SSSConfig(1, 2)
        val secret = "Minimal threshold".toByteArray()
        
        val splitResult = sss.split(secret, config)
        assertTrue(splitResult is SSSResult.Success)
        
        val shares = (splitResult as SSSResult.Success).value.shares
        assertEquals(2, shares.size)
        
        // Should reconstruct from any single share
        for (share in shares) {
            val reconstructResult = sss.reconstruct(listOf(share))
            assertTrue(reconstructResult is SSSResult.Success)
            
            val reconstructed = (reconstructResult as SSSResult.Success).value
            assertArrayEquals(secret, reconstructed)
        }
    }
    
    @Test
    fun `split and reconstruct with k=2 n=2 configuration`() {
        val config = SSSConfig(2, 2)
        val secret = "All shares required".toByteArray()
        
        val splitResult = sss.split(secret, config)
        assertTrue(splitResult is SSSResult.Success)
        
        val shares = (splitResult as SSSResult.Success).value.shares
        assertEquals(2, shares.size)
        
        // Should only reconstruct with both shares
        val reconstructResult = sss.reconstruct(shares)
        assertTrue(reconstructResult is SSSResult.Success)
        
        val reconstructed = (reconstructResult as SSSResult.Success).value
        assertArrayEquals(secret, reconstructed)
    }
    
    @Test
    fun `split and reconstruct with k=1 n=128 configuration`() {
        val config = SSSConfig(1, 128)
        val secret = "Maximum shares minimal threshold".toByteArray()
        
        val splitResult = sss.split(secret, config)
        assertTrue(splitResult is SSSResult.Success)
        
        val shares = (splitResult as SSSResult.Success).value.shares
        assertEquals(128, shares.size)
        
        // Should reconstruct from any single share
        val testIndices = listOf(0, 1, 63, 64, 126, 127)
        for (index in testIndices) {
            val reconstructResult = sss.reconstruct(listOf(shares[index]))
            assertTrue(reconstructResult is SSSResult.Success)
            
            val reconstructed = (reconstructResult as SSSResult.Success).value
            assertArrayEquals(secret, reconstructed)
        }
    }
    
    @Test
    fun `split and reconstruct with k=127 n=128 configuration`() {
        val config = SSSConfig(127, 128)
        val secret = "Almost all shares required".toByteArray()
        
        val splitResult = sss.split(secret, config)
        assertTrue(splitResult is SSSResult.Success)
        
        val shares = (splitResult as SSSResult.Success).value.shares
        assertEquals(128, shares.size)
        
        // Should reconstruct with any 127 shares
        val shareSubsets = listOf(
            shares.drop(1),      // Missing first share
            shares.dropLast(1),  // Missing last share
            shares.filterIndexed { index, _ -> index != 64 } // Missing middle share
        )
        
        for (subset in shareSubsets) {
            assertEquals(127, subset.size)
            val reconstructResult = sss.reconstruct(subset)
            assertTrue(reconstructResult is SSSResult.Success)
            
            val reconstructed = (reconstructResult as SSSResult.Success).value
            assertArrayEquals(secret, reconstructed)
        }
    }
    
    @Test
    fun `split and reconstruct with k=128 n=128 configuration`() {
        val config = SSSConfig(128, 128)
        val secret = "All 128 shares required".toByteArray()
        
        val splitResult = sss.split(secret, config)
        assertTrue(splitResult is SSSResult.Success)
        
        val shares = (splitResult as SSSResult.Success).value.shares
        assertEquals(128, shares.size)
        
        // Should only reconstruct with all 128 shares
        val reconstructResult = sss.reconstruct(shares)
        assertTrue(reconstructResult is SSSResult.Success)
        
        val reconstructed = (reconstructResult as SSSResult.Success).value
        assertArrayEquals(secret, reconstructed)
    }
    
    @Test
    fun `split and reconstruct with various extreme k n combinations`() {
        val extremeConfigs = listOf(
            SSSConfig(1, 10),    // Very low threshold
            SSSConfig(10, 10),   // All shares required
            SSSConfig(50, 100),  // Half shares required
            SSSConfig(99, 100),  // Almost all shares required
            SSSConfig(64, 128),  // Half of maximum
            SSSConfig(100, 128), // High threshold, max shares
            SSSConfig(2, 128),   // Low threshold, max shares
            SSSConfig(3, 3),     // Small all-required
            SSSConfig(5, 7),     // Small majority required
            SSSConfig(1, 64)     // Minimum threshold, medium shares
        )
        
        val secret = "Testing extreme configurations".toByteArray()
        
        for (config in extremeConfigs) {
            val splitResult = sss.split(secret, config)
            assertTrue(splitResult is SSSResult.Success, "Failed to split with config k=${config.threshold}, n=${config.totalShares}")
            
            val shares = (splitResult as SSSResult.Success).value.shares
            assertEquals(config.totalShares, shares.size)
            
            val reconstructResult = sss.reconstruct(shares.take(config.threshold))
            assertTrue(reconstructResult is SSSResult.Success, "Failed to reconstruct with config k=${config.threshold}, n=${config.totalShares}")
            
            val reconstructed = (reconstructResult as SSSResult.Success).value
            assertArrayEquals(secret, reconstructed)
        }
    }
    
    @Test
    fun `split and reconstruct with maximum size secret and extreme configurations`() {
        val secret = ByteArray(1024) { (it % 256).toByte() }
        val configs = listOf(
            SSSConfig(1, 128),
            SSSConfig(128, 128),
            SSSConfig(64, 128),
            SSSConfig(100, 128)
        )
        
        for (config in configs) {
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
    fun `reconstruct with exactly k shares in different orders`() {
        val config = SSSConfig(5, 10)
        val secret = "Order independence test".toByteArray()
        
        val splitResult = sss.split(secret, config)
        assertTrue(splitResult is SSSResult.Success)
        
        val shares = (splitResult as SSSResult.Success).value.shares
        
        // Test different orderings of the first 5 shares
        val orderings = listOf(
            listOf(0, 1, 2, 3, 4),
            listOf(4, 3, 2, 1, 0),
            listOf(0, 2, 4, 1, 3),
            listOf(3, 1, 4, 0, 2)
        )
        
        for (ordering in orderings) {
            val orderedShares = ordering.map { shares[it] }
            val reconstructResult = sss.reconstruct(orderedShares)
            assertTrue(reconstructResult is SSSResult.Success)
            
            val reconstructed = (reconstructResult as SSSResult.Success).value
            assertArrayEquals(secret, reconstructed)
        }
    }
    
    @Test
    fun `reconstruct with different subsets of shares for low threshold`() {
        val config = SSSConfig(3, 10)
        val secret = "Multiple valid subsets".toByteArray()
        
        val splitResult = sss.split(secret, config)
        assertTrue(splitResult is SSSResult.Success)
        
        val shares = (splitResult as SSSResult.Success).value.shares
        
        // Test various 3-share combinations
        val combinations = listOf(
            listOf(0, 1, 2),
            listOf(3, 4, 5),
            listOf(6, 7, 8),
            listOf(0, 5, 9),
            listOf(1, 4, 7),
            listOf(7, 8, 9)
        )
        
        for (indices in combinations) {
            val subset = indices.map { shares[it] }
            val reconstructResult = sss.reconstruct(subset)
            assertTrue(reconstructResult is SSSResult.Success)
            
            val reconstructed = (reconstructResult as SSSResult.Success).value
            assertArrayEquals(secret, reconstructed)
        }
    }
}