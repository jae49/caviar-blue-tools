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

package cb.core.tools.erasure.performance

import cb.core.tools.erasure.math.GaloisField
import cb.core.tools.erasure.matrix.MatrixUtils
import cb.core.tools.erasure.matrix.SystematicRSEncoder
import cb.core.tools.erasure.models.*
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.concurrent.thread
import kotlinx.coroutines.*

class OptimizedReedSolomonEncoder {
    
    private val threadPool = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors()
    )
    
    // Cache for encoding matrices (systematic algorithm)
    private val matrixCache = mutableMapOf<Pair<Int, Int>, Array<IntArray>>()
    
    // Pre-populate cache with common configurations
    init {
        val commonConfigs = listOf(
            Pair(4, 2), Pair(8, 2), Pair(8, 4), Pair(10, 4),
            Pair(16, 4), Pair(20, 4), Pair(32, 8)
        )
        MatrixUtils.prePopulateCache(commonConfigs.map { Pair(it.first, it.first + it.second) })
    }
    
    fun encode(data: ByteArray, config: EncodingConfig): List<Shard> {
        require(data.isNotEmpty()) { "Data cannot be empty" }
        
        return encodeSystematic(data, config)
    }
    
    
    private fun encodeSystematic(data: ByteArray, config: EncodingConfig): List<Shard> {
        val checksum = calculateChecksum(data)
        val metadata = ShardMetadata(
            originalSize = data.size.toLong(),
            config = config,
            checksum = checksum
        )
        
        val paddedData = padData(data, config)
        val chunks = chunkData(paddedData, config)
        
        // Get or generate encoding matrix
        val encodingMatrix = getOrGenerateEncodingMatrix(config.dataShards, config.parityShards)
        
        // Process chunks in parallel
        return runBlocking {
            val deferreds = chunks.mapIndexed { chunkIndex, chunk ->
                async(Dispatchers.Default) {
                    processChunkSystematic(chunk, encodingMatrix, config, metadata, chunkIndex)
                }
            }
            deferreds.flatMap { it.await() }
        }
    }
    
    
    private fun calculateChecksum(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return hash.joinToString("") { "%02x".format(it) }
    }
    
    private fun padData(data: ByteArray, config: EncodingConfig): ByteArray {
        val totalDataSize = ((data.size + config.shardSize - 1) / config.shardSize) * config.shardSize
        return if (data.size == totalDataSize) {
            data // No padding needed, avoid copy
        } else {
            val paddedData = ByteArray(totalDataSize)
            System.arraycopy(data, 0, paddedData, 0, data.size)
            paddedData
        }
    }
    
    private fun chunkData(data: ByteArray, config: EncodingConfig): List<ByteArray> {
        val chunks = mutableListOf<ByteArray>()
        val chunkSize = config.shardSize * config.dataShards
        
        for (i in data.indices step chunkSize) {
            val endIndex = minOf(i + chunkSize, data.size)
            val chunk = ByteArray(chunkSize)
            System.arraycopy(data, i, chunk, 0, endIndex - i)
            chunks.add(chunk)
        }
        
        return chunks
    }
    
    private fun createDataShards(
        chunk: ByteArray,
        config: EncodingConfig,
        metadata: ShardMetadata,
        chunkIndex: Int
    ): List<Shard> {
        val dataShards = ArrayList<Shard>(config.dataShards)
        
        for (shardIndex in 0 until config.dataShards) {
            val shardData = ByteArray(config.shardSize)
            val startIndex = shardIndex * config.shardSize
            val endIndex = minOf(startIndex + config.shardSize, chunk.size)
            
            if (startIndex < chunk.size) {
                System.arraycopy(chunk, startIndex, shardData, 0, endIndex - startIndex)
            }
            
            val globalShardIndex = chunkIndex * config.totalShards + shardIndex
            dataShards.add(Shard(globalShardIndex, shardData, metadata))
        }
        
        return dataShards
    }
    
    
    
    private fun getOrGenerateEncodingMatrix(dataShards: Int, parityShards: Int): Array<IntArray> {
        val key = Pair(dataShards, parityShards)
        return matrixCache.getOrPut(key) {
            // Generate only the parity portion of the matrix
            // (data portion is identity and doesn't need to be stored)
            Array(parityShards) { i ->
                IntArray(dataShards) { j ->
                    GaloisField.power(dataShards + i, j)
                }
            }
        }
    }
    
    private fun processChunkSystematic(
        chunk: ByteArray,
        parityMatrix: Array<IntArray>,
        config: EncodingConfig,
        metadata: ShardMetadata,
        chunkIndex: Int
    ): List<Shard> {
        val shards = mutableListOf<Shard>()
        
        // Create data shards (systematic - contain original data)
        val dataShardsList = createDataShards(chunk, config, metadata, chunkIndex)
        shards.addAll(dataShardsList)
        
        // Create parity shards using optimized matrix multiplication
        val parityShards = createSystematicParityShards(
            dataShardsList, parityMatrix, config, metadata, chunkIndex
        )
        shards.addAll(parityShards)
        
        return shards
    }
    
    private fun createSystematicParityShards(
        dataShards: List<Shard>,
        parityMatrix: Array<IntArray>,
        config: EncodingConfig,
        metadata: ShardMetadata,
        chunkIndex: Int
    ): List<Shard> {
        val parityCount = config.parityShards
        val parityData = Array(parityCount) { ByteArray(config.shardSize) }
        
        // Use parallel processing for parity generation
        runBlocking {
            val jobs = mutableListOf<Job>()
            
            // Process in blocks for better cache locality
            val blockSize = 64
            for (blockStart in 0 until config.shardSize step blockSize) {
                val blockEnd = minOf(blockStart + blockSize, config.shardSize)
                
                jobs.add(launch(Dispatchers.Default) {
                    // Process each byte position in the block
                    for (bytePos in blockStart until blockEnd) {
                        // Extract data bytes at this position
                        val dataBytes = IntArray(config.dataShards) { i ->
                            dataShards[i].data[bytePos].toInt() and 0xFF
                        }
                        
                        // Compute parity bytes using matrix multiplication
                        // with loop unrolling for common cases
                        when (parityCount) {
                            2 -> {
                                // Optimized for 2 parity shards
                                for (p in 0 until 2) {
                                    var sum = 0
                                    for (d in dataBytes.indices) {
                                        sum = GaloisField.add(sum,
                                            GaloisField.multiply(parityMatrix[p][d], dataBytes[d]))
                                    }
                                    parityData[p][bytePos] = sum.toByte()
                                }
                            }
                            4 -> {
                                // Optimized for 4 parity shards
                                for (p in 0 until 4) {
                                    var sum = 0
                                    var d = 0
                                    // Unroll by 4
                                    while (d + 3 < dataBytes.size) {
                                        sum = GaloisField.add(sum,
                                            GaloisField.multiply(parityMatrix[p][d], dataBytes[d]))
                                        sum = GaloisField.add(sum,
                                            GaloisField.multiply(parityMatrix[p][d+1], dataBytes[d+1]))
                                        sum = GaloisField.add(sum,
                                            GaloisField.multiply(parityMatrix[p][d+2], dataBytes[d+2]))
                                        sum = GaloisField.add(sum,
                                            GaloisField.multiply(parityMatrix[p][d+3], dataBytes[d+3]))
                                        d += 4
                                    }
                                    // Handle remainder
                                    while (d < dataBytes.size) {
                                        sum = GaloisField.add(sum,
                                            GaloisField.multiply(parityMatrix[p][d], dataBytes[d]))
                                        d++
                                    }
                                    parityData[p][bytePos] = sum.toByte()
                                }
                            }
                            else -> {
                                // General case using optimized matrix-vector multiply
                                val parityBytes = MatrixUtils.multiplyMatrixVector(parityMatrix, dataBytes)
                                for (p in parityBytes.indices) {
                                    parityData[p][bytePos] = parityBytes[p].toByte()
                                }
                            }
                        }
                    }
                })
            }
            
            jobs.joinAll()
        }
        
        // Create shard objects
        return (0 until parityCount).map { parityIndex ->
            val globalShardIndex = chunkIndex * config.totalShards + config.dataShards + parityIndex
            Shard(globalShardIndex, parityData[parityIndex], metadata)
        }
    }
    
    fun shutdown() {
        threadPool.shutdown()
    }
}