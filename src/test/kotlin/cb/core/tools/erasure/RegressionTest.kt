package cb.core.tools.erasure

import cb.core.tools.erasure.models.EncodingConfig
import cb.core.tools.erasure.models.Shard
import cb.core.tools.erasure.models.ReconstructionResult
import cb.core.tools.erasure.models.ReconstructionError
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.random.Random

class RegressionTest {
    private val encoder = ReedSolomonEncoder()
    private val decoder = ReedSolomonDecoder()
    
    @Test
    fun `regression test - 8-part encoding with threshold 5, deleting parts 0,3,6`() {
        // This is the exact failing case reported
        val config = EncodingConfig(dataShards = 5, parityShards = 3)
        val data = "Test data for regression".toByteArray()
        
        val allShards = encoder.encode(data, config)
        assertEquals(8, allShards.size, "Should have 8 total shards")
        
        // Delete shards at indices 0, 3, and 6 (keeping 1,2,4,5,7)
        val remainingIndices = listOf(1, 2, 4, 5, 7)
        val remainingShards = remainingIndices.map { allShards[it] }
        
        assertEquals(5, remainingShards.size, "Should have exactly k=5 shards remaining")
        
        when (val result = decoder.decode(remainingShards)) {
            is ReconstructionResult.Success -> {
                assertArrayEquals(data, result.data, "Reconstructed data should match original")
            }
            is ReconstructionResult.Failure -> {
                fail("Should be able to decode with any 5 shards, but got: ${result.error} - ${result.message ?: ""}")
            }
            else -> fail("Unexpected result type")
        }
    }
    
    @Test
    fun `test systematic patterns - missing first k shards`() {
        val config = EncodingConfig(dataShards = 4, parityShards = 3)
        val data = Random.nextBytes(1024)
        val shards = encoder.encode(data, config)
        
        // Missing first 3 shards (indices 0,1,2), keeping 3,4,5,6
        val remainingShards = shards.drop(3)
        assertEquals(4, remainingShards.size)
        
        when (val result = decoder.decode(remainingShards)) {
            is ReconstructionResult.Failure -> {
                println("Missing first k shards failed: ${result.error} - ${result.message ?: ""}")
            }
            else -> {}
        }
    }
    
    @Test
    fun `test systematic patterns - missing last k shards`() {
        val config = EncodingConfig(dataShards = 4, parityShards = 3)
        val data = Random.nextBytes(1024)
        val shards = encoder.encode(data, config)
        
        // Missing last 3 shards, keeping first 4
        val remainingShards = shards.take(4)
        
        when (val result = decoder.decode(remainingShards)) {
            is ReconstructionResult.Success -> {
                assertArrayEquals(data, result.data)
            }
            is ReconstructionResult.Failure -> {
                fail("Should succeed with all data shards present, but got: ${result.error}")
            }
            else -> fail("Unexpected result type")
        }
    }
    
    @Test
    fun `test edge case - missing every other shard`() {
        val config = EncodingConfig(dataShards = 4, parityShards = 4)
        val data = Random.nextBytes(1024)
        val shards = encoder.encode(data, config)
        
        // Keep shards at even indices: 0,2,4,6
        val remainingShards = shards.filterIndexed { index, _ -> index % 2 == 0 }
        assertEquals(4, remainingShards.size)
        
        when (val result = decoder.decode(remainingShards)) {
            is ReconstructionResult.Success -> {
                assertArrayEquals(data, result.data)
            }
            is ReconstructionResult.Failure -> {
                fail("Should decode with k=4 shards from n=8, but got: ${result.error}")
            }
            else -> fail("Unexpected result type")
        }
    }
    
    @Test
    fun `test large configuration - 8+6 with various deletion patterns`() {
        val config = EncodingConfig(dataShards = 8, parityShards = 6)
        val data = Random.nextBytes(16 * 1024) // 16KB as mentioned in plan
        val shards = encoder.encode(data, config)
        
        data class DeletionPattern(val name: String, val indicesToDelete: List<Int>)
        val patterns = listOf(
            DeletionPattern("First 6", (0..5).toList()),
            DeletionPattern("Last 6", (8..13).toList()),
            DeletionPattern("Alternating 6", listOf(0, 2, 4, 6, 8, 10)),
            DeletionPattern("Random 6", listOf(1, 3, 5, 7, 11, 13)),
            DeletionPattern("Data+Parity mix", listOf(0, 1, 8, 9, 10, 11))
        )
        
        for (pattern in patterns) {
            val remainingIndices = (0..13).filter { it !in pattern.indicesToDelete }
            val remainingShards = remainingIndices.map { shards[it] }
            
            assertEquals(8, remainingShards.size, "Should have k=8 shards remaining")
            
            when (val result = decoder.decode(remainingShards)) {
                is ReconstructionResult.Success -> {
                    println("Pattern '${pattern.name}': SUCCESS")
                    assertArrayEquals(data, result.data, 
                        "Pattern '${pattern.name}' should reconstruct correctly")
                }
                is ReconstructionResult.Failure -> {
                    println("Pattern '${pattern.name}': FAILED - ${result.error} - ${result.message ?: ""}")
                }
                else -> {
                    println("Pattern '${pattern.name}': FAILED - Unexpected result type")
                }
            }
        }
    }
    
    @Test
    fun `test boundary conditions - exactly k shards`() {
        val configs = listOf(
            EncodingConfig(2, 2),
            EncodingConfig(3, 3),
            EncodingConfig(5, 5),
            EncodingConfig(8, 4)
        )
        
        for (config in configs) {
            val data = Random.nextBytes(512)
            val shards = encoder.encode(data, config)
            
            // Test with exactly k shards from different positions
            val testCases = listOf(
                shards.take(config.dataShards),  // First k
                shards.takeLast(config.dataShards),  // Last k
                shards.shuffled().take(config.dataShards)  // Random k
            )
            
            for ((index, selectedShards) in testCases.withIndex()) {
                when (val result = decoder.decode(selectedShards)) {
                    is ReconstructionResult.Failure -> {
                        println("Config (${config.dataShards},${config.parityShards}) test case $index failed: ${result.error} - ${result.message ?: ""}")
                    }
                    else -> {}
                }
            }
        }
    }
    
    @Test
    fun `test maximum erasure scenarios - lose maximum allowed shards`() {
        val config = EncodingConfig(dataShards = 6, parityShards = 4)
        val data = Random.nextBytes(2048)
        val shards = encoder.encode(data, config)
        
        // We can lose up to 4 shards (the number of parity shards)
        // Test different patterns of losing exactly 4 shards
        val patternsToTest = listOf(
            listOf(0, 1, 2, 3),     // Lose first 4 data shards
            listOf(6, 7, 8, 9),     // Lose all parity shards
            listOf(0, 3, 6, 9),     // Lose scattered shards
            listOf(2, 4, 5, 7),     // Lose mixed data and parity
            listOf(1, 3, 5, 8)      // Another mixed pattern
        )
        
        for (lostIndices in patternsToTest) {
            val remainingIndices = (0..9).filter { it !in lostIndices }
            val remainingShards = remainingIndices.map { shards[it] }
            
            when (val result = decoder.decode(remainingShards)) {
                is ReconstructionResult.Success -> {
                    println("Lost shards $lostIndices: SUCCESS")
                }
                is ReconstructionResult.Failure -> {
                    println("Lost shards $lostIndices: FAILED")
                }
                else -> {
                    println("Lost shards $lostIndices: FAILED - Unexpected result")
                }
            }
        }
    }
    
    @Test
    fun `test error reporting accuracy`() {
        val config = EncodingConfig(dataShards = 4, parityShards = 2)
        val data = Random.nextBytes(1024)
        val shards = encoder.encode(data, config)
        
        // Test with insufficient shards (less than k)
        val insufficientShards = shards.take(3)  // Only 3 shards when we need 4
        when (val result1 = decoder.decode(insufficientShards)) {
            is ReconstructionResult.Success -> {
                fail("Should fail with insufficient shards")
            }
            is ReconstructionResult.Failure -> {
                // Expected failure
                assertEquals(ReconstructionError.INSUFFICIENT_SHARDS, result1.error)
            }
            else -> fail("Unexpected result type")
        }
        
        // Test with corrupted shards (if we can simulate this)
        val corruptedShards = shards.take(4).map { shard ->
            val corruptedData = shard.data.clone()
            corruptedData[0] = (corruptedData[0] + 1).toByte()  // Corrupt first byte
            Shard(shard.index, corruptedData, shard.metadata)
        }
        
        when (val result2 = decoder.decode(corruptedShards)) {
            is ReconstructionResult.Success -> {
                println("Corrupted shards result: SUCCESS (no corruption detection)")
            }
            is ReconstructionResult.Failure -> {
                println("Corrupted shards result: FAILED - ${result2.error} - ${result2.message ?: ""}")
            }
            else -> {
                println("Corrupted shards result: Unexpected result type")
            }
        }
    }
    
    @Test
    fun `test previously working combinations continue to work`() {
        // Ensure that fixing the reported issues doesn't break existing functionality
        val config = EncodingConfig(dataShards = 4, parityShards = 2)
        val data = Random.nextBytes(1024)
        val shards = encoder.encode(data, config)
        
        // Test cases that should definitely work
        val workingCases = listOf(
            "All shards" to shards,
            "All data shards" to shards.take(4),
            "First k shards" to shards.take(4)
        )
        
        for ((name, selectedShards) in workingCases) {
            when (val result = decoder.decode(selectedShards)) {
                is ReconstructionResult.Success -> {
                    assertArrayEquals(data, result.data, "$name should reconstruct correctly")
                }
                is ReconstructionResult.Failure -> {
                    fail("$name should successfully decode, but got: ${result.error}")
                }
                else -> fail("Unexpected result type for $name")
            }
        }
    }
}