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

import cb.core.tools.erasure.math.GaloisField
import cb.core.tools.erasure.models.*
import java.security.MessageDigest

/**
 * Space-efficient Reed-Solomon encoder that minimizes storage overhead.
 * 
 * Key optimizations:
 * 1. Dynamic shard sizing based on data size
 * 2. Single-chunk encoding for smaller files
 * 3. Minimal padding overhead
 * 
 * This encoder ensures that the actual storage overhead closely matches
 * the theoretical overhead (parityShards/dataShards).
 */
class SpaceEfficientReedSolomonEncoder {
    
    /**
     * Encodes data with automatic shard size calculation for minimal overhead.
     * 
     * @param data Input data to encode
     * @param config Base configuration (shard size will be optimized)
     * @return List of space-efficient shards
     */
    fun encode(data: ByteArray, config: EncodingConfig): List<Shard> {
        require(data.isNotEmpty()) { "Data cannot be empty" }
        
        // Calculate optimal shard size
        val optimalShardSize = calculateOptimalShardSize(data.size, config)
        val optimizedConfig = config.copy(shardSize = optimalShardSize)
        
        // For single-chunk data, use direct encoding
        if (data.size <= optimalShardSize * config.dataShards) {
            return encodeSingleChunk(data, optimizedConfig)
        }
        
        // For multi-chunk data, use standard chunking
        return encodeMultiChunk(data, optimizedConfig)
    }
    
    /**
     * Encodes data with a specific shard size (for compatibility).
     */
    fun encode(data: ByteArray, dataShards: Int, parityShards: Int, shardSize: Int? = null): List<ByteArray> {
        require(dataShards > 0) { "dataShards must be positive" }
        require(parityShards > 0) { "parityShards must be positive" }
        require(data.isNotEmpty()) { "data cannot be empty" }
        
        val totalShards = dataShards + parityShards
        require(totalShards <= 256) { "Total shards cannot exceed 256 for GF(256)" }
        
        // Calculate optimal shard size if not provided
        val actualShardSize = shardSize ?: ((data.size + dataShards - 1) / dataShards)
        
        // Create data shards by splitting input data
        val shards = mutableListOf<ByteArray>()
        for (i in 0 until dataShards) {
            val shard = ByteArray(actualShardSize)
            val start = i * actualShardSize
            val end = minOf(start + actualShardSize, data.size)
            if (start < data.size) {
                data.copyInto(shard, 0, start, end)
            }
            shards.add(shard)
        }
        
        // Generate parity matrix
        val parityMatrix = Array(parityShards) { i ->
            IntArray(dataShards) { j ->
                val evalPoint = GaloisField.exp(dataShards + i)
                GaloisField.power(evalPoint, j)
            }
        }
        
        // Generate parity shards
        for (i in 0 until parityShards) {
            val parityShard = ByteArray(actualShardSize)
            
            for (bytePos in 0 until actualShardSize) {
                val dataBytes = IntArray(dataShards) { j ->
                    shards[j][bytePos].toInt() and 0xFF
                }
                
                var parityByte = 0
                for (j in 0 until dataShards) {
                    parityByte = GaloisField.add(
                        parityByte,
                        GaloisField.multiply(parityMatrix[i][j], dataBytes[j])
                    )
                }
                
                parityShard[bytePos] = parityByte.toByte()
            }
            
            shards.add(parityShard)
        }
        
        return shards
    }
    
    private fun calculateOptimalShardSize(dataSize: Int, config: EncodingConfig): Int {
        // For small files, use a shard size that minimizes padding
        val minShardSize = 256 // Minimum practical shard size
        val maxShardSize = config.shardSize // Maximum from config
        
        // Calculate the ideal shard size (no padding)
        val idealShardSize = (dataSize + config.dataShards - 1) / config.dataShards
        
        // Choose a shard size that balances efficiency and practicality
        return when {
            idealShardSize <= minShardSize -> minShardSize
            idealShardSize >= maxShardSize -> maxShardSize
            else -> {
                // Round up to next power of 2 for better alignment
                val nextPowerOf2 = 1 shl (32 - Integer.numberOfLeadingZeros(idealShardSize - 1))
                minOf(nextPowerOf2, maxShardSize)
            }
        }
    }
    
    private fun encodeSingleChunk(data: ByteArray, config: EncodingConfig): List<Shard> {
        val checksum = calculateChecksum(data)
        
        // Minimal padding - only pad to make divisible by dataShards
        val paddedSize = ((data.size + config.dataShards - 1) / config.dataShards) * config.dataShards
        val actualShardSize = paddedSize / config.dataShards
        
        // Create metadata with the actual shard size used
        val actualConfig = config.copy(shardSize = actualShardSize)
        val metadata = ShardMetadata(
            originalSize = data.size.toLong(),
            config = actualConfig,
            checksum = checksum
        )
        
        val shards = mutableListOf<Shard>()
        
        // Create data shards
        for (i in 0 until config.dataShards) {
            val shardData = ByteArray(actualShardSize)
            val start = i * actualShardSize
            val end = minOf(start + actualShardSize, data.size)
            if (start < data.size) {
                System.arraycopy(data, start, shardData, 0, end - start)
            }
            shards.add(Shard(i, shardData, metadata))
        }
        
        // Generate parity shards
        val parityMatrix = Array(config.parityShards) { i ->
            IntArray(config.dataShards) { j ->
                val evalPoint = GaloisField.exp(config.dataShards + i)
                GaloisField.power(evalPoint, j)
            }
        }
        
        for (i in 0 until config.parityShards) {
            val parityShard = ByteArray(actualShardSize)
            
            for (bytePos in 0 until actualShardSize) {
                val dataBytes = IntArray(config.dataShards) { j ->
                    shards[j].data[bytePos].toInt() and 0xFF
                }
                
                var parityByte = 0
                for (j in 0 until config.dataShards) {
                    parityByte = GaloisField.add(
                        parityByte,
                        GaloisField.multiply(parityMatrix[i][j], dataBytes[j])
                    )
                }
                
                parityShard[bytePos] = parityByte.toByte()
            }
            
            shards.add(Shard(config.dataShards + i, parityShard, metadata))
        }
        
        return shards
    }
    
    private fun encodeMultiChunk(data: ByteArray, config: EncodingConfig): List<Shard> {
        // For larger files, use the standard encoder
        val standardEncoder = ReedSolomonEncoder()
        return standardEncoder.encode(data, config)
    }
    
    private fun calculateChecksum(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return hash.joinToString("") { "%02x".format(it) }
    }
}