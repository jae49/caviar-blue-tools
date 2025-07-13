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

import cb.core.tools.erasure.math.PolynomialMath
import cb.core.tools.erasure.models.*
import java.security.MessageDigest

class ReedSolomonDecoder {
    
    fun decode(shards: List<Shard>): ReconstructionResult {
        if (shards.isEmpty()) {
            return ReconstructionResult.Failure(ReconstructionError.INSUFFICIENT_SHARDS)
        }
        
        val config = shards.first().metadata.config
        val originalSize = shards.first().metadata.originalSize
        val expectedChecksum = shards.first().metadata.checksum
        
        if (!canReconstruct(shards, config)) {
            return ReconstructionResult.Failure(ReconstructionError.INSUFFICIENT_SHARDS)
        }
        
        return try {
            val reconstructedData = reconstructData(shards, config, originalSize)
            val actualChecksum = calculateChecksum(reconstructedData)
            
            if (actualChecksum == expectedChecksum) {
                ReconstructionResult.Success(reconstructedData)
            } else {
                ReconstructionResult.Failure(ReconstructionError.CORRUPTED_SHARDS)
            }
        } catch (e: Exception) {
            ReconstructionResult.Failure(ReconstructionError.MATH_ERROR)
        }
    }
    
    fun canReconstruct(shards: List<Shard>, config: EncodingConfig): Boolean {
        val availableShards = shards.size
        return availableShards >= config.dataShards
    }
    
    private fun reconstructData(shards: List<Shard>, config: EncodingConfig, originalSize: Long): ByteArray {
        val chunkGroups = groupShardsByChunk(shards, config)
        val reconstructedChunks = mutableListOf<ByteArray>()
        
        for (chunkShards in chunkGroups) {
            val reconstructedChunk = reconstructChunk(chunkShards, config)
            reconstructedChunks.add(reconstructedChunk)
        }
        
        val fullData = combineChunks(reconstructedChunks)
        return fullData.copyOf(originalSize.toInt())
    }
    
    private fun groupShardsByChunk(shards: List<Shard>, config: EncodingConfig): List<List<Shard>> {
        val chunks = mutableMapOf<Int, MutableList<Shard>>()
        
        for (shard in shards) {
            val chunkIndex = shard.index / config.totalShards
            chunks.getOrPut(chunkIndex) { mutableListOf() }.add(shard)
        }
        
        return chunks.values.toList()
    }
    
    private fun reconstructChunk(shards: List<Shard>, config: EncodingConfig): ByteArray {
        val chunk = ByteArray(config.shardSize * config.dataShards)
        
        if (hasAllDataShards(shards, config)) {
            return reconstructFromDataShards(shards, config)
        }
        
        return reconstructWithParity(shards, config)
    }
    
    private fun hasAllDataShards(shards: List<Shard>, config: EncodingConfig): Boolean {
        val dataShardIndices = shards
            .filter { it.isDataShard }
            .map { it.index % config.totalShards }
            .toSet()
        
        return dataShardIndices.size == config.dataShards
    }
    
    private fun reconstructFromDataShards(shards: List<Shard>, config: EncodingConfig): ByteArray {
        val chunk = ByteArray(config.shardSize * config.dataShards)
        
        for (shard in shards.filter { it.isDataShard }) {
            val shardIndexInChunk = shard.index % config.totalShards
            val startIndex = shardIndexInChunk * config.shardSize
            System.arraycopy(shard.data, 0, chunk, startIndex, config.shardSize)
        }
        
        return chunk
    }
    
    private fun reconstructWithParity(shards: List<Shard>, config: EncodingConfig): ByteArray {
        val chunk = ByteArray(config.shardSize * config.dataShards)
        
        for (byteIndex in 0 until config.shardSize) {
            val shardsArray = Array<IntArray?>(config.totalShards) { null }
            val erasures = mutableListOf<Int>()
            
            for (shard in shards) {
                val shardIndexInChunk = shard.index % config.totalShards
                shardsArray[shardIndexInChunk] = intArrayOf(shard.data[byteIndex].toInt() and 0xFF)
            }
            
            for (i in 0 until config.totalShards) {
                if (shardsArray[i] == null) {
                    erasures.add(i)
                }
            }
            
            val reconstructedByte = PolynomialMath.decode(
                shardsArray,
                erasures.toIntArray(),
                config.dataShards,
                config.parityShards
            )
            
            if (reconstructedByte != null && reconstructedByte.size >= config.dataShards) {
                for (dataIndex in 0 until config.dataShards) {
                    val chunkByteIndex = dataIndex * config.shardSize + byteIndex
                    if (chunkByteIndex < chunk.size) {
                        chunk[chunkByteIndex] = reconstructedByte[dataIndex].toByte()
                    }
                }
            } else {
                reconstructDataShardsOnly(shards, config, chunk, byteIndex)
            }
        }
        
        return chunk
    }
    
    private fun reconstructDataShardsOnly(
        shards: List<Shard>,
        config: EncodingConfig,
        chunk: ByteArray,
        byteIndex: Int
    ) {
        for (shard in shards.filter { it.isDataShard }) {
            val shardIndexInChunk = shard.index % config.totalShards
            val chunkByteIndex = shardIndexInChunk * config.shardSize + byteIndex
            if (chunkByteIndex < chunk.size) {
                chunk[chunkByteIndex] = shard.data[byteIndex]
            }
        }
    }
    
    private fun combineChunks(chunks: List<ByteArray>): ByteArray {
        val totalSize = chunks.sumOf { it.size }
        val result = ByteArray(totalSize)
        var offset = 0
        
        for (chunk in chunks) {
            System.arraycopy(chunk, 0, result, offset, chunk.size)
            offset += chunk.size
        }
        
        return result
    }
    
    private fun calculateChecksum(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return hash.joinToString("") { "%02x".format(it) }
    }
}