package cb.core.tools.sss

import cb.core.tools.sss.models.SSSConfig
import cb.core.tools.sss.models.SecretShare
import cb.core.tools.sss.models.SSSResult
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ErrorRecoveryTest {
    
    private val sss = ShamirSecretSharing()
    
    @Test
    fun `reconstruct with exactly k shares vs more shares`() {
        val config = SSSConfig(3, 10)
        val secret = "Testing share count variations".toByteArray()
        
        val splitResult = sss.split(secret, config)
        assertTrue(splitResult is SSSResult.Success)
        
        val allShares = (splitResult as SSSResult.Success).value.shares
        
        // Test with exactly k shares
        val minimalShares = allShares.take(3)
        val minimalResult = sss.reconstruct(minimalShares)
        assertTrue(minimalResult is SSSResult.Success)
        val minimalReconstruction = (minimalResult as SSSResult.Success).value
        
        // Test with k+1, k+2, ..., n shares
        for (shareCount in 4..10) {
            val extraShares = allShares.take(shareCount)
            val extraResult = sss.reconstruct(extraShares)
            assertTrue(extraResult is SSSResult.Success)
            
            val extraReconstruction = (extraResult as SSSResult.Success).value
            assertArrayEquals(minimalReconstruction, extraReconstruction,
                "Reconstruction with $shareCount shares doesn't match minimal reconstruction")
        }
    }
    
    @Test
    fun `reconstruct with shares in different orders`() {
        val config = SSSConfig(4, 8)
        val secret = "Order independence test".toByteArray()
        
        val splitResult = sss.split(secret, config)
        assertTrue(splitResult is SSSResult.Success)
        
        val shares = (splitResult as SSSResult.Success).value.shares
        
        // Different orderings to test
        val orderings = listOf(
            listOf(0, 1, 2, 3),      // Sequential
            listOf(3, 2, 1, 0),      // Reverse
            listOf(0, 2, 4, 6),      // Even indices
            listOf(1, 3, 5, 7),      // Odd indices
            listOf(7, 0, 3, 4),      // Random
            listOf(5, 6, 7, 0)       // Wrap around
        )
        
        val referenceResult = sss.reconstruct(shares.take(4))
        assertTrue(referenceResult is SSSResult.Success)
        val reference = (referenceResult as SSSResult.Success).value
        
        for (ordering in orderings) {
            val orderedShares = ordering.map { shares[it] }
            val result = sss.reconstruct(orderedShares)
            assertTrue(result is SSSResult.Success)
            
            val reconstructed = (result as SSSResult.Success).value
            assertArrayEquals(reference, reconstructed,
                "Order ${ordering.joinToString(",")} produced different result")
        }
    }
    
    @Test
    fun `reconstruct with insufficient shares should fail gracefully`() {
        val configs = listOf(
            SSSConfig(3, 5),
            SSSConfig(5, 10),
            SSSConfig(10, 20)
        )
        
        for (config in configs) {
            val secret = "Insufficient shares test".toByteArray()
            val splitResult = sss.split(secret, config)
            assertTrue(splitResult is SSSResult.Success)
            
            val shares = (splitResult as SSSResult.Success).value.shares
            
            // Test with k-1, k-2, ..., 1, 0 shares
            for (shareCount in (config.threshold - 1) downTo 0) {
                val insufficientShares = shares.take(shareCount)
                val result = sss.reconstruct(insufficientShares)
                assertFalse(result is SSSResult.Success,
                    "Reconstruction should fail with $shareCount shares (need ${config.threshold})")
                
                assertTrue(result is SSSResult.Failure)
                val error = result as SSSResult.Failure
                assertTrue(error.message.contains("insufficient", ignoreCase = true) ||
                          error.message.contains("require", ignoreCase = true))
            }
        }
    }
    
    @Test
    fun `recover from partial share corruption with redundant shares`() {
        val config = SSSConfig(3, 7) // Need 3, have 7
        val secret = "Corruption recovery test".toByteArray()
        
        val splitResult = sss.split(secret, config)
        assertTrue(splitResult is SSSResult.Success)
        
        val shares = (splitResult as SSSResult.Success).value.shares.toMutableList()
        
        // Corrupt 2 shares (still have 5 good ones, need only 3)
        shares[1] = shares[1].copy(
            data = shares[1].data.copyOf().apply {
                for (i in 0 until minOf(10, size)) {
                    this[i] = (this[i] + 1).toByte()
                }
            }
        )
        shares[3] = shares[3].copy(
            data = ByteArray(shares[3].data.size) { 0xFF.toByte() }
        )
        
        // Try different combinations that avoid corrupted shares
        val goodCombinations = listOf(
            listOf(0, 2, 4),
            listOf(0, 5, 6),
            listOf(2, 4, 5),
            listOf(4, 5, 6)
        )
        
        for (indices in goodCombinations) {
            val selectedShares = indices.map { shares[it] }
            val result = sss.reconstruct(selectedShares)
            assertTrue(result is SSSResult.Success)
            
            val reconstructed = (result as SSSResult.Success).value
            assertArrayEquals(secret, reconstructed)
        }
    }
    
    @Test
    fun `handle empty share list gracefully`() {
        val emptyShares = emptyList<SecretShare>()
        val result = sss.reconstruct(emptyShares)
        assertFalse(result is SSSResult.Success)
        
        assertTrue(result is SSSResult.Failure)
        val error = result as SSSResult.Failure
        assertTrue(error.message.contains("Insufficient", ignoreCase = true))
    }
    
    @Test
    fun `handle null or invalid share data gracefully`() {
        val config = SSSConfig(3, 5)
        val secret = "Invalid share handling".toByteArray()
        
        val splitResult = sss.split(secret, config)
        assertTrue(splitResult is SSSResult.Success)
        
        val shares = (splitResult as SSSResult.Success).value.shares.toMutableList()
        
        // Test with shares that have invalid configurations
        // We can't create shares with invalid indices due to constructor validation,
        // so test with shares that have corrupted data (which should fail integrity check)
        val corruptedShares = listOf(
            SecretShare(
                index = shares[0].index,
                data = shares[0].data.copyOf().apply { this[0] = (this[0] + 1).toByte() },
                metadata = shares[0].metadata,
                dataHash = shares[0].dataHash // Keep original hash to trigger integrity failure
            )
        )
        
        for (corruptedShare in corruptedShares) {
            val shareSet = listOf(corruptedShare) + shares.drop(1).take(2)
            val result = sss.reconstruct(shareSet)
            assertFalse(result is SSSResult.Success, "Should fail with corrupted share")
        }
    }
    
    @Test
    fun `reconstruct with all possible k-subsets from n shares`() {
        val config = SSSConfig(3, 6)
        val secret = "Exhaustive subset test".toByteArray()
        
        val splitResult = sss.split(secret, config)
        assertTrue(splitResult is SSSResult.Success)
        
        val shares = (splitResult as SSSResult.Success).value.shares
        
        // Generate all possible 3-combinations from 6 shares
        val combinations = generateCombinations(6, 3)
        var successCount = 0
        
        for (indices in combinations) {
            val subset = indices.map { shares[it] }
            val result = sss.reconstruct(subset)
            assertTrue(result is SSSResult.Success)
            
            val reconstructed = (result as SSSResult.Success).value
            assertArrayEquals(secret, reconstructed)
            successCount++
        }
        
        // C(6,3) = 20 combinations
        assertEquals(20, successCount)
    }
    
    @Test
    fun `handle reconstruction with mixed valid and invalid shares`() {
        val config = SSSConfig(3, 6)
        val secret = "Mixed share validation".toByteArray()
        
        val splitResult = sss.split(secret, config)
        assertTrue(splitResult is SSSResult.Success)
        
        val shares = (splitResult as SSSResult.Success).value.shares
        
        // Create a mix of valid and invalid shares
        val mixedShares = listOf(
            shares[0], // Valid
            shares[1], // Valid
            shares[2].copy(data = shares[2].data.copyOf().apply { this[0] = (this[0] + 1).toByte() }), // Invalid
            shares[3], // Valid
            shares[4]  // Valid
        )
        
        // Should succeed if we pick the right 3 shares
        val validSubset = listOf(mixedShares[0], mixedShares[1], mixedShares[3])
        val validResult = sss.reconstruct(validSubset)
        assertTrue(validResult is SSSResult.Success)
        
        val reconstructed = (validResult as SSSResult.Success).value
        assertArrayEquals(secret, reconstructed)
        
        // Should fail if we include the corrupted share
        val invalidSubset = listOf(mixedShares[0], mixedShares[1], mixedShares[2])
        val invalidResult = sss.reconstruct(invalidSubset)
        assertFalse(invalidResult is SSSResult.Success)
    }
    
    @Test
    fun `progressive share addition for reconstruction`() {
        val config = SSSConfig(5, 10)
        val secret = "Progressive reconstruction".toByteArray()
        
        val splitResult = sss.split(secret, config)
        assertTrue(splitResult is SSSResult.Success)
        
        val shares = (splitResult as SSSResult.Success).value.shares
        val progressiveShares = mutableListOf<SecretShare>()
        
        // Add shares one by one
        for (i in shares.indices) {
            progressiveShares.add(shares[i])
            
            val result = sss.reconstruct(progressiveShares)
            
            if (progressiveShares.size < config.threshold) {
                assertFalse(result is SSSResult.Success,
                    "Should fail with ${progressiveShares.size} shares (need ${config.threshold})")
            } else {
                assertTrue(result is SSSResult.Success,
                    "Should succeed with ${progressiveShares.size} shares (need ${config.threshold})")
                
                val reconstructed = (result as SSSResult.Success).value
                assertArrayEquals(secret, reconstructed)
            }
        }
    }
    
    @Test
    fun `handle shares with mismatched configurations`() {
        val config1 = SSSConfig(3, 5)
        val config2 = SSSConfig(4, 7)
        
        val secret1 = "First configuration".toByteArray()
        val secret2 = "Second configuratio".toByteArray()
        
        val splitResult1 = sss.split(secret1, config1)
        val splitResult2 = sss.split(secret2, config2)
        
        assertTrue(splitResult1 is SSSResult.Success)
        assertTrue(splitResult2 is SSSResult.Success)
        
        val shares1 = (splitResult1 as SSSResult.Success).value.shares
        val shares2 = (splitResult2 as SSSResult.Success).value.shares
        
        // Try to mix shares from different configurations
        val mixedShares = listOf(shares1[0], shares1[1], shares2[0])
        
        val result = sss.reconstruct(mixedShares)
        assertFalse(result is SSSResult.Success, 
            "Reconstruction should fail when mixing shares from different configurations")
    }
    
    private fun generateCombinations(n: Int, k: Int): List<List<Int>> {
        val result = mutableListOf<List<Int>>()
        val combination = IntArray(k) { it }
        
        while (true) {
            result.add(combination.toList())
            
            var i = k - 1
            while (i >= 0 && combination[i] == n - k + i) {
                i--
            }
            
            if (i < 0) break
            
            combination[i]++
            for (j in i + 1 until k) {
                combination[j] = combination[j - 1] + 1
            }
        }
        
        return result
    }
}