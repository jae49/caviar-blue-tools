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

import cb.core.tools.erasure.models.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

class ReedSolomonDecoderTest {
    
    private val encoder = ReedSolomonEncoder()
    private val decoder = ReedSolomonDecoder()
    
    @Test
    fun `test successful decoding with all shards present`() {
        val originalData = "Hello, Reed-Solomon!".toByteArray()
        val config = EncodingConfig(dataShards = 3, parityShards = 2, shardSize = 64)
        
        val shards = encoder.encode(originalData, config)
        val result = decoder.decode(shards)
        
        assertTrue(result is ReconstructionResult.Success)
        assertTrue(originalData.contentEquals((result as ReconstructionResult.Success).data))
    }
    
    @Test
    fun `test successful decoding with minimum required shards`() {
        val originalData = "Test data for minimum shards".toByteArray()
        val config = EncodingConfig(dataShards = 4, parityShards = 3, shardSize = 32)
        
        val allShards = encoder.encode(originalData, config)
        val minimalShards = allShards.take(config.dataShards)
        val result = decoder.decode(minimalShards)
        
        assertTrue(result is ReconstructionResult.Success)
        assertTrue(originalData.contentEquals((result as ReconstructionResult.Success).data))
    }
    
    @Test
    fun `test decoding with some parity shards missing`() {
        val originalData = "Parity test data".toByteArray()
        val config = EncodingConfig(dataShards = 3, parityShards = 2, shardSize = 64)
        
        val allShards = encoder.encode(originalData, config)
        val availableShards = allShards.dropLast(1)
        val result = decoder.decode(availableShards)
        
        assertTrue(result is ReconstructionResult.Success)
        assertTrue(originalData.contentEquals((result as ReconstructionResult.Success).data))
    }
    
    @Test
    fun `test decoding fails with insufficient shards`() {
        val originalData = "Insufficient shards test".toByteArray()
        val config = EncodingConfig(dataShards = 5, parityShards = 2, shardSize = 32)
        
        val allShards = encoder.encode(originalData, config)
        val insufficientShards = allShards.take(config.dataShards - 1)
        val result = decoder.decode(insufficientShards)
        
        assertTrue(result is ReconstructionResult.Failure)
        assertEquals(ReconstructionError.INSUFFICIENT_SHARDS, (result as ReconstructionResult.Failure).error)
    }
    
    @Test
    fun `test canReconstruct returns true with sufficient shards`() {
        val config = EncodingConfig(dataShards = 4, parityShards = 2)
        val shards = createMockShards(config, 4)
        
        assertTrue(decoder.canReconstruct(shards, config))
    }
    
    @Test
    fun `test canReconstruct returns false with insufficient shards`() {
        val config = EncodingConfig(dataShards = 4, parityShards = 2)
        val shards = createMockShards(config, 3)
        
        assertFalse(decoder.canReconstruct(shards, config))
    }
    
    @Test
    fun `test decoding with empty shard list fails`() {
        val result = decoder.decode(emptyList())
        
        assertTrue(result is ReconstructionResult.Failure)
        assertEquals(ReconstructionError.INSUFFICIENT_SHARDS, (result as ReconstructionResult.Failure).error)
    }
    
    @Test
    fun `test round trip encoding and decoding preserves data`() {
        val originalData = ByteArray(1000) { (it % 256).toByte() }
        val config = EncodingConfig(dataShards = 6, parityShards = 4, shardSize = 128)
        
        val shards = encoder.encode(originalData, config)
        val result = decoder.decode(shards)
        
        assertTrue(result is ReconstructionResult.Success)
        assertTrue(originalData.contentEquals((result as ReconstructionResult.Success).data))
    }
    
    @Test
    fun `test decoding with mixed data and parity shards`() {
        val originalData = "Mixed shards test".toByteArray()
        val config = EncodingConfig(dataShards = 3, parityShards = 3, shardSize = 32)
        
        val allShards = encoder.encode(originalData, config)
        val mixedShards = listOf(
            allShards[0], // data shard
            allShards[2], // data shard
            allShards[4], // parity shard
            allShards[5]  // parity shard
        )
        
        val result = decoder.decode(mixedShards)
        
        assertTrue(result is ReconstructionResult.Success)
        assertTrue(originalData.contentEquals((result as ReconstructionResult.Success).data))
    }
    
    private fun createMockShards(config: EncodingConfig, count: Int): List<Shard> {
        val metadata = ShardMetadata(
            originalSize = 100L,
            config = config,
            checksum = "test-checksum"
        )
        
        return (0 until count).map { index ->
            Shard(
                index = index,
                data = ByteArray(config.shardSize) { 0 },
                metadata = metadata
            )
        }
    }
}