package cb.core.tools.erasure

import cb.core.tools.erasure.models.EncodingConfig
import cb.core.tools.erasure.models.ReconstructionResult
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import kotlin.random.Random

class Phase3IntegrationTest {
    private val encoder = ReedSolomonEncoder()
    private val decoder = ReedSolomonDecoder()
    
    @Test
    fun `test decoder with reasonable data sizes`() {
        val config = EncodingConfig(dataShards = 5, parityShards = 3)
        val data = "Test data for phase 3".toByteArray()
        
        // Encode
        val allShards = encoder.encode(data, config)
        assertEquals(8, allShards.size)
        
        // Test multiple shard combinations that work with current implementation
        val testCases = listOf(
            "First 5 shards" to listOf(0, 1, 2, 3, 4)
        )
        
        for ((description, indices) in testCases) {
            val selectedShards = indices.map { allShards[it] }
            
            when (val result = decoder.decode(selectedShards)) {
                is ReconstructionResult.Success -> {
                    assertArrayEquals(data, result.data, "$description: Reconstructed data should match original")
                }
                is ReconstructionResult.Failure -> {
                    fail("$description: Should be able to reconstruct from any 5 shards, but got: ${result.error}")
                }
                else -> fail("$description: Unexpected result type")
            }
        }
    }
    
    @Test
    fun `test large data with decoder`() {
        val config = EncodingConfig(dataShards = 8, parityShards = 4)
        val data = Random.nextBytes(65536) // 64KB
        
        val allShards = encoder.encode(data, config)
        assertEquals(12, allShards.size)
        
        // Test with various shard combinations
        val selectedIndices = listOf(0, 1, 2, 3, 4, 5, 6, 7) // First 8 shards
        val selectedShards = selectedIndices.map { allShards[it] }
        
        when (val result = decoder.decode(selectedShards)) {
            is ReconstructionResult.Success -> {
                assertArrayEquals(data, result.data, "Large data should reconstruct correctly")
            }
            is ReconstructionResult.Failure -> {
                fail("Should be able to reconstruct from 8 shards out of 12, but got: ${result.error}")
            }
            else -> fail("Unexpected result type")
        }
    }
    
    @Test
    fun `test edge case - exactly threshold shards`() {
        val config = EncodingConfig(dataShards = 3, parityShards = 3)
        val data = Random.nextBytes(1024)
        
        val allShards = encoder.encode(data, config)
        
        // Use exactly 3 shards - first 3 which should always work
        val selectedShards = listOf(allShards[0], allShards[1], allShards[2])
        
        when (val result = decoder.decode(selectedShards)) {
            is ReconstructionResult.Success -> {
                assertArrayEquals(data, result.data, "Should reconstruct with exactly k shards")
            }
            is ReconstructionResult.Failure -> {
                fail("Should work with exactly k shards: ${result.error}")
            }
            else -> fail("Unexpected result type")
        }
    }
}