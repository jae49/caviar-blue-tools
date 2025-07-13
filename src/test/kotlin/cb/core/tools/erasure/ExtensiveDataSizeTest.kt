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

package cb.core.tools.erasure

import cb.core.tools.erasure.models.EncodingConfig
import cb.core.tools.erasure.models.ReconstructionResult
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.random.Random

@Tag("slow")
class ExtensiveDataSizeTest {
    
    @ParameterizedTest
    @CsvSource(
        "1, 4, 2",           // 1 byte with 4 data, 2 parity
        "100, 4, 2",         // 100 bytes
        "1024, 4, 2",        // 1 KB
        "10240, 8, 4",       // 10 KB
        "102400, 10, 6"      // 100 KB
    )
    fun testVariousDataSizesWithDifferentConfigurations(
        dataSize: Int,
        dataShards: Int,
        parityShards: Int
    ) {
        val encoder = ReedSolomonEncoder()
        val decoder = ReedSolomonDecoder()
        val config = EncodingConfig(dataShards = dataShards, parityShards = parityShards)
        
        // Create random data
        val originalData = ByteArray(dataSize) { Random.nextBytes(1)[0] }
        
        // Encode
        val shards = encoder.encode(originalData, config)
        assertEquals(config.totalShards, shards.size, "Should produce correct number of shards")
        
        // Test 1: All shards available
        val allShardsResult = decoder.decode(shards)
        assertTrue(allShardsResult is ReconstructionResult.Success)
        assertArrayEquals(originalData, (allShardsResult as ReconstructionResult.Success).data)
        
        // Test 2: Minimum shards available
        val minShards = shards.shuffled(Random).take(dataShards)
        val minShardsResult = decoder.decode(minShards)
        assertTrue(minShardsResult is ReconstructionResult.Success)
        assertArrayEquals(originalData, (minShardsResult as ReconstructionResult.Success).data)
        
        // Test 3: Random subset with some erasures
        val erasureCount = Random.nextInt(1, parityShards + 1)
        val availableCount = config.totalShards - erasureCount
        val randomSubset = shards.shuffled(Random).take(availableCount)
        val randomResult = decoder.decode(randomSubset)
        assertTrue(randomResult is ReconstructionResult.Success)
        assertArrayEquals(originalData, (randomResult as ReconstructionResult.Success).data)
    }
    
    @Test
    fun testEdgeCaseSizes() {
        val encoder = ReedSolomonEncoder()
        val decoder = ReedSolomonDecoder()
        val config = EncodingConfig(dataShards = 4, parityShards = 2)
        
        // Test edge cases
        val edgeCaseSizes = listOf(
            1,                              // Single byte
            config.shardSize - 1,           // Just under one shard
            config.shardSize,               // Exactly one shard
            config.shardSize + 1,           // Just over one shard
            config.shardSize * config.dataShards - 1,  // Just under full chunk
            config.shardSize * config.dataShards,      // Exactly full chunk
            config.shardSize * config.dataShards + 1   // Just over full chunk
        )
        
        for (size in edgeCaseSizes) {
            val data = ByteArray(size) { (it % 256).toByte() }
            val shards = encoder.encode(data, config)
            
            // Test with minimum shards
            val minShards = shards.take(config.dataShards)
            val result = decoder.decode(minShards)
            
            assertTrue(result is ReconstructionResult.Success, 
                "Failed to reconstruct data of size $size")
            assertArrayEquals(data, (result as ReconstructionResult.Success).data,
                "Data mismatch for size $size")
        }
    }
    
    @Test
    fun testPowerOfTwoSizes() {
        val encoder = ReedSolomonEncoder()
        val decoder = ReedSolomonDecoder()
        val config = EncodingConfig(dataShards = 8, parityShards = 4)
        
        // Test power of 2 sizes from 2^0 to 2^16
        for (power in 0..16) {
            val size = 1 shl power // 2^power
            val data = ByteArray(size) { Random.nextBytes(1)[0] }
            
            val shards = encoder.encode(data, config)
            
            // Simulate loss of random shards
            val availableShards = shards.shuffled(Random).take(config.dataShards + 1)
            val result = decoder.decode(availableShards)
            
            assertTrue(result is ReconstructionResult.Success,
                "Failed to reconstruct 2^$power = $size bytes")
            assertArrayEquals(data, (result as ReconstructionResult.Success).data,
                "Data mismatch for 2^$power = $size bytes")
        }
    }
    
    @Test
    fun testPatternedData() {
        val encoder = ReedSolomonEncoder()
        val decoder = ReedSolomonDecoder()
        val config = EncodingConfig(dataShards = 6, parityShards = 3)
        
        // Test various patterns
        val patterns = listOf(
            // All zeros
            ByteArray(10000) { 0 },
            
            // All ones
            ByteArray(10000) { 0xFF.toByte() },
            
            // Alternating pattern
            ByteArray(10000) { if (it % 2 == 0) 0 else 0xFF.toByte() },
            
            // Sequential values
            ByteArray(10000) { (it % 256).toByte() },
            
            // Random pattern
            ByteArray(10000).also { Random.nextBytes(it) }
        )
        
        for ((index, pattern) in patterns.withIndex()) {
            val shards = encoder.encode(pattern, config)
            
            // Test with various erasure patterns
            val erasurePatterns = listOf(
                shards.drop(1),                    // Lost first shard
                shards.dropLast(1),                // Lost last shard
                shards.filterIndexed { i, _ -> i % 2 == 0 }, // Lost odd shards
                shards.shuffled(Random).take(config.dataShards) // Random minimum
            )
            
            for ((erasureIndex, availableShards) in erasurePatterns.withIndex()) {
                val result = decoder.decode(availableShards)
                assertTrue(result is ReconstructionResult.Success,
                    "Pattern $index, erasure pattern $erasureIndex failed")
                assertArrayEquals(pattern, (result as ReconstructionResult.Success).data,
                    "Pattern $index, erasure pattern $erasureIndex data mismatch")
            }
        }
    }
    
    @Test
    fun testMaximumErasures() {
        val encoder = ReedSolomonEncoder()
        val decoder = ReedSolomonDecoder()
        
        // Test different configurations at their erasure limits
        val configurations = listOf(
            EncodingConfig(dataShards = 2, parityShards = 1),
            EncodingConfig(dataShards = 4, parityShards = 2),
            EncodingConfig(dataShards = 8, parityShards = 4),
            EncodingConfig(dataShards = 16, parityShards = 8),
            EncodingConfig(dataShards = 32, parityShards = 16)
        )
        
        for (config in configurations) {
            val data = ByteArray(1024) { Random.nextBytes(1)[0] }
            val shards = encoder.encode(data, config)
            
            // Test with exactly maximum erasures (keep minimum shards)
            val minShards = shards.take(config.dataShards)
            val result = decoder.decode(minShards)
            
            assertTrue(result is ReconstructionResult.Success,
                "Failed with config ${config.dataShards}+${config.parityShards}")
            assertArrayEquals(data, (result as ReconstructionResult.Success).data)
            
            // Test with one less than minimum (should fail)
            val insufficientShards = shards.take(config.dataShards - 1)
            val failResult = decoder.decode(insufficientShards)
            
            assertTrue(failResult is ReconstructionResult.Failure,
                "Should fail with insufficient shards")
        }
    }
}