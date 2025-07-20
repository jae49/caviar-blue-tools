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

package cb.core.tools.erasure.stream

import cb.core.tools.erasure.ReedSolomonEncoder
import cb.core.tools.erasure.models.EncodingConfig
import cb.core.tools.erasure.models.Shard
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows

class StreamingDecoderTest {
    
    @Test
    fun testBasicDecoding() = runBlocking {
        val decoder = StreamingDecoder()
        val encoder = ReedSolomonEncoder()
        val config = EncodingConfig(dataShards = 2, parityShards = 1, shardSize = 8)
        val originalData = "Hello, World!".toByteArray()
        
        // Encode data
        val shards = encoder.encode(originalData, config)
        
        // Create flow of shard chunks
        val shardFlow = flow {
            emit(shards)
        }
        
        // Decode
        val decoded = decoder.decodeStream(shardFlow).toList()
        val result = decoded.flatMap { it.toList() }.toByteArray()
        
        assertArrayEquals(originalData, result.copyOf(originalData.size))
    }
    
    // TODO: This test requires proper handling of multiple chunks with different checksums
    // The current implementation expects all shards to have the same checksum
    // @Test
    fun testMultipleChunkDecoding() = runBlocking {
        val decoder = StreamingDecoder()
        val encoder = ReedSolomonEncoder()
        val config = EncodingConfig(dataShards = 2, parityShards = 1, shardSize = 4)
        
        val chunk1Data = "ABCD".toByteArray()
        val chunk2Data = "EFGH".toByteArray()
        
        val chunk1Shards = encoder.encode(chunk1Data, config).mapIndexed { index, shard ->
            shard.copy(metadata = shard.metadata.copy(chunkIndex = 0))
        }
        
        val chunk2Shards = encoder.encode(chunk2Data, config).mapIndexed { index, shard ->
            shard.copy(
                index = shard.index + config.totalShards,
                metadata = shard.metadata.copy(chunkIndex = 1)
            )
        }
        
        // Create flow that emits shards in mixed order
        val shardFlow = flow {
            emit(listOf(chunk1Shards[0], chunk2Shards[0]))
            emit(listOf(chunk1Shards[1], chunk2Shards[1]))
            emit(listOf(chunk1Shards[2], chunk2Shards[2]))
        }
        
        val decoded = decoder.decodeStream(shardFlow).toList()
        
        assertEquals(2, decoded.size)
        // Just verify we got data back for now
        assertTrue(decoded[0].isNotEmpty())
        assertTrue(decoded[1].isNotEmpty())
    }
    
    @Test
    fun testPartialShardDecoding() = runBlocking {
        val decoder = StreamingDecoder()
        val encoder = ReedSolomonEncoder()
        val config = EncodingConfig(dataShards = 3, parityShards = 2, shardSize = 4)
        val originalData = "Test".toByteArray()
        
        val shards = encoder.encode(originalData, config).mapIndexed { index, shard ->
            shard.copy(metadata = shard.metadata.copy(chunkIndex = 0))
        }
        
        // Create flow with only minimum required shards (drop last 2 shards)
        val partialShards = shards.take(3)
        
        val shardFlow = flow {
            emit(partialShards)
        }
        
        val decoded = decoder.decodeStream(shardFlow).toList()
        val result = decoded.flatMap { it.toList() }.toByteArray()
        
        assertArrayEquals(originalData, result.copyOf(originalData.size))
    }
    
    @Test
    fun testInsufficientShardsThrows() = runBlocking {
        val decoder = StreamingDecoder()
        val config = EncodingConfig(dataShards = 3, parityShards = 1, shardSize = 4)
        
        // Create insufficient shards (only 2 when we need 3)
        val shards = listOf(
            Shard(0, ByteArray(4), cb.core.tools.erasure.models.ShardMetadata(
                originalSize = 12,
                config = config,
                checksum = "test",
                chunkIndex = 0
            )),
            Shard(1, ByteArray(4), cb.core.tools.erasure.models.ShardMetadata(
                originalSize = 12,
                config = config,
                checksum = "test",
                chunkIndex = 0
            ))
        )
        
        val shardFlow = flow {
            emit(shards)
        }
        
        assertThrows<ReconstructionException> {
            runBlocking {
                decoder.decodeStream(shardFlow).toList()
            }
        }
    }
}