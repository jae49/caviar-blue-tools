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
import cb.core.tools.erasure.models.*
import java.security.MessageDigest
import java.util.concurrent.*
import kotlin.concurrent.thread

class TwentyShardOptimizedEncoder {
    
    private val threadPool = ForkJoinPool(
        Runtime.getRuntime().availableProcessors(),
        ForkJoinPool.defaultForkJoinWorkerThreadFactory,
        null,
        true
    )
    
    // Pre-allocated buffers for better memory locality
    private val threadLocalBuffers = ThreadLocal.withInitial {
        ByteArray(1024 * 1024) // 1MB buffer per thread
    }
    
    fun encode(data: ByteArray, config: EncodingConfig): List<Shard> {
        require(data.isNotEmpty()) { "Data cannot be empty" }
        require(config.totalShards == 20) { "This encoder is optimized for exactly 20 shards" }
        
        val checksum = calculateChecksum(data)
        val metadata = ShardMetadata(
            originalSize = data.size.toLong(),
            config = config,
            checksum = checksum
        )
        
        // Pre-compute encoding matrix
        val encodingMatrix = OptimizedPolynomialMath.encodeMatrix(config.dataShards, config.parityShards)
        
        val paddedData = padData(data, config)
        val chunks = chunkData(paddedData, config)
        
        // Process chunks in parallel using Fork/Join
        val tasks = chunks.mapIndexed { chunkIndex, chunk ->
            ChunkEncodingTask(chunk, encodingMatrix, config, metadata, chunkIndex)
        }
        
        val results = tasks.map { task ->
            threadPool.invoke(task)
        }
        
        return results.flatten()
    }
    
    private inner class ChunkEncodingTask(
        private val chunk: ByteArray,
        private val encodingMatrix: Array<IntArray>,
        private val config: EncodingConfig,
        private val metadata: ShardMetadata,
        private val chunkIndex: Int
    ) : RecursiveTask<List<Shard>>() {
        
        override fun compute(): List<Shard> {
            // Create data shards
            val dataShards = createDataShards(chunk, config, metadata, chunkIndex)
            
            // Create parity shards with optimized encoding
            val parityShards = createOptimizedParityShards(
                chunk, encodingMatrix, config, metadata, chunkIndex
            )
            
            return dataShards + parityShards
        }
    }
    
    private fun createOptimizedParityShards(
        chunk: ByteArray,
        encodingMatrix: Array<IntArray>,
        config: EncodingConfig,
        metadata: ShardMetadata,
        chunkIndex: Int
    ): List<Shard> {
        val parityData = Array(config.parityShards) { ByteArray(config.shardSize) }
        
        // Process in larger blocks for better cache usage
        val blockSize = 256 // Process 256 bytes at a time
        val numBlocks = (config.shardSize + blockSize - 1) / blockSize
        
        // Parallel processing of blocks
        val blockTasks = (0 until numBlocks).map { blockIndex ->
            threadPool.submit {
                processBlock(
                    chunk, encodingMatrix, config, parityData,
                    blockIndex * blockSize,
                    minOf((blockIndex + 1) * blockSize, config.shardSize)
                )
            }
        }
        
        // Wait for all blocks to complete
        blockTasks.forEach { it.get() }
        
        // Create shard objects
        return (0 until config.parityShards).map { parityIndex ->
            val globalShardIndex = chunkIndex * config.totalShards + config.dataShards + parityIndex
            Shard(globalShardIndex, parityData[parityIndex], metadata)
        }
    }
    
    private fun processBlock(
        chunk: ByteArray,
        encodingMatrix: Array<IntArray>,
        config: EncodingConfig,
        parityData: Array<ByteArray>,
        startByte: Int,
        endByte: Int
    ) {
        // Use thread-local buffer for intermediate calculations
        val buffer = IntArray(config.dataShards)
        
        for (byteIndex in startByte until endByte) {
            // Gather data bytes from each shard
            for (shardIndex in 0 until config.dataShards) {
                val globalByteIndex = shardIndex * config.shardSize + byteIndex
                buffer[shardIndex] = if (globalByteIndex < chunk.size) {
                    chunk[globalByteIndex].toInt() and 0xFF
                } else {
                    0
                }
            }
            
            // Compute parity bytes using vectorized operations
            computeParityOptimized(buffer, encodingMatrix, config.parityShards) { parityIndex, value ->
                parityData[parityIndex][byteIndex] = value.toByte()
            }
        }
    }
    
    private inline fun computeParityOptimized(
        data: IntArray,
        matrix: Array<IntArray>,
        parityCount: Int,
        store: (Int, Int) -> Unit
    ) {
        // Unrolled computation for common cases
        when (parityCount) {
            4 -> {
                // Optimized for 16+4 configuration
                var p0 = 0
                var p1 = 0
                var p2 = 0
                var p3 = 0
                
                for (i in data.indices) {
                    val d = data[i]
                    if (d != 0) {
                        p0 = GaloisField.add(p0, GaloisField.multiply(matrix[0][i], d))
                        p1 = GaloisField.add(p1, GaloisField.multiply(matrix[1][i], d))
                        p2 = GaloisField.add(p2, GaloisField.multiply(matrix[2][i], d))
                        p3 = GaloisField.add(p3, GaloisField.multiply(matrix[3][i], d))
                    }
                }
                
                store(0, p0)
                store(1, p1)
                store(2, p2)
                store(3, p3)
            }
            6 -> {
                // Optimized for 14+6 configuration
                var p0 = 0
                var p1 = 0
                var p2 = 0
                var p3 = 0
                var p4 = 0
                var p5 = 0
                
                for (i in data.indices) {
                    val d = data[i]
                    if (d != 0) {
                        p0 = GaloisField.add(p0, GaloisField.multiply(matrix[0][i], d))
                        p1 = GaloisField.add(p1, GaloisField.multiply(matrix[1][i], d))
                        p2 = GaloisField.add(p2, GaloisField.multiply(matrix[2][i], d))
                        p3 = GaloisField.add(p3, GaloisField.multiply(matrix[3][i], d))
                        p4 = GaloisField.add(p4, GaloisField.multiply(matrix[4][i], d))
                        p5 = GaloisField.add(p5, GaloisField.multiply(matrix[5][i], d))
                    }
                }
                
                store(0, p0)
                store(1, p1)
                store(2, p2)
                store(3, p3)
                store(4, p4)
                store(5, p5)
            }
            8 -> {
                // Optimized for 12+8 configuration
                val parity = IntArray(8)
                
                for (i in data.indices) {
                    val d = data[i]
                    if (d != 0) {
                        // Unroll by 2 for better performance
                        for (j in 0 until 8 step 2) {
                            parity[j] = GaloisField.add(parity[j], GaloisField.multiply(matrix[j][i], d))
                            parity[j + 1] = GaloisField.add(parity[j + 1], GaloisField.multiply(matrix[j + 1][i], d))
                        }
                    }
                }
                
                for (i in 0 until 8) {
                    store(i, parity[i])
                }
            }
            10 -> {
                // Optimized for 10+10 configuration
                val parity = IntArray(10)
                
                for (i in data.indices) {
                    val d = data[i]
                    if (d != 0) {
                        // Unroll by 2
                        for (j in 0 until 10 step 2) {
                            parity[j] = GaloisField.add(parity[j], GaloisField.multiply(matrix[j][i], d))
                            parity[j + 1] = GaloisField.add(parity[j + 1], GaloisField.multiply(matrix[j + 1][i], d))
                        }
                    }
                }
                
                for (i in 0 until 10) {
                    store(i, parity[i])
                }
            }
            else -> {
                // General case
                for (row in 0 until parityCount) {
                    var sum = 0
                    for (col in data.indices) {
                        sum = GaloisField.add(sum, GaloisField.multiply(matrix[row][col], data[col]))
                    }
                    store(row, sum)
                }
            }
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
            data
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
        return (0 until config.dataShards).map { shardIndex ->
            val shardData = ByteArray(config.shardSize)
            val startIndex = shardIndex * config.shardSize
            val endIndex = minOf(startIndex + config.shardSize, chunk.size)
            
            if (startIndex < chunk.size) {
                System.arraycopy(chunk, startIndex, shardData, 0, endIndex - startIndex)
            }
            
            val globalShardIndex = chunkIndex * config.totalShards + shardIndex
            Shard(globalShardIndex, shardData, metadata)
        }
    }
    
    fun shutdown() {
        threadPool.shutdown()
        threadPool.awaitTermination(10, TimeUnit.SECONDS)
    }
}