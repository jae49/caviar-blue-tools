package cb.core.tools.erasure.performance

import cb.core.tools.erasure.math.GaloisField
import cb.core.tools.erasure.math.PolynomialMath
import cb.core.tools.erasure.models.*
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.concurrent.thread

class OptimizedReedSolomonEncoder {
    
    private val threadPool = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors()
    )
    
    fun encode(data: ByteArray, config: EncodingConfig): List<Shard> {
        require(data.isNotEmpty()) { "Data cannot be empty" }
        
        val checksum = calculateChecksum(data)
        val metadata = ShardMetadata(
            originalSize = data.size.toLong(),
            config = config,
            checksum = checksum
        )
        
        val paddedData = padData(data, config)
        val chunks = chunkData(paddedData, config)
        
        // Pre-compute generator polynomial once
        val generator = PolynomialMath.generateGenerator(config.parityShards)
        
        // Process chunks in parallel
        val futures = mutableListOf<Future<List<Shard>>>()
        
        for (chunkIndex in chunks.indices) {
            val chunk = chunks[chunkIndex]
            val future = threadPool.submit<List<Shard>> {
                processChunk(chunk, generator, config, metadata, chunkIndex)
            }
            futures.add(future)
        }
        
        // Collect results
        val shards = mutableListOf<Shard>()
        for (future in futures) {
            shards.addAll(future.get())
        }
        
        return shards
    }
    
    private fun processChunk(
        chunk: ByteArray,
        generator: IntArray,
        config: EncodingConfig,
        metadata: ShardMetadata,
        chunkIndex: Int
    ): List<Shard> {
        val dataShards = createDataShards(chunk, config, metadata, chunkIndex)
        val parityShards = createOptimizedParityShards(chunk, generator, config, metadata, chunkIndex)
        return dataShards + parityShards
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
    
    private fun createOptimizedParityShards(
        chunk: ByteArray,
        generator: IntArray,
        config: EncodingConfig,
        metadata: ShardMetadata,
        chunkIndex: Int
    ): List<Shard> {
        // Pre-allocate parity shard arrays
        val parityData = Array(config.parityShards) { ByteArray(config.shardSize) }
        
        // Process in blocks for better cache locality
        val blockSize = 64 // Process 64 bytes at a time for better cache usage
        
        for (blockStart in 0 until config.shardSize step blockSize) {
            val blockEnd = minOf(blockStart + blockSize, config.shardSize)
            
            for (byteIndex in blockStart until blockEnd) {
                val dataBytes = IntArray(config.dataShards)
                
                // Gather data bytes from each shard
                for (shardIndex in 0 until config.dataShards) {
                    val globalByteIndex = shardIndex * config.shardSize + byteIndex
                    dataBytes[shardIndex] = if (globalByteIndex < chunk.size) {
                        chunk[globalByteIndex].toInt() and 0xFF
                    } else {
                        0
                    }
                }
                
                // Compute parity bytes using optimized polynomial encoding
                val parityBytes = encodeOptimized(dataBytes, generator)
                
                // Store parity bytes
                for (parityIndex in parityBytes.indices) {
                    parityData[parityIndex][byteIndex] = parityBytes[parityIndex].toByte()
                }
            }
        }
        
        // Create shard objects
        val parityShards = ArrayList<Shard>(config.parityShards)
        for (parityIndex in 0 until config.parityShards) {
            val globalShardIndex = chunkIndex * config.totalShards + config.dataShards + parityIndex
            parityShards.add(Shard(globalShardIndex, parityData[parityIndex], metadata))
        }
        
        return parityShards
    }
    
    // Optimized encoding with loop unrolling and reduced allocations
    private fun encodeOptimized(data: IntArray, generator: IntArray): IntArray {
        val parityCount = generator.size - 1
        val parity = IntArray(parityCount)
        
        // Direct implementation optimized for common cases
        when (parityCount) {
            2 -> {
                // Optimized for 2 parity shards (common case)
                for (i in data.indices) {
                    val feedback = data[i] xor parity[0]
                    if (feedback != 0) {
                        parity[0] = parity[1] xor GaloisField.multiply(generator[1], feedback)
                        parity[1] = GaloisField.multiply(generator[0], feedback)
                    } else {
                        parity[0] = parity[1]
                        parity[1] = 0
                    }
                }
            }
            4 -> {
                // Optimized for 4 parity shards (common case)
                for (i in data.indices) {
                    val feedback = data[i] xor parity[0]
                    if (feedback != 0) {
                        parity[0] = parity[1] xor GaloisField.multiply(generator[3], feedback)
                        parity[1] = parity[2] xor GaloisField.multiply(generator[2], feedback)
                        parity[2] = parity[3] xor GaloisField.multiply(generator[1], feedback)
                        parity[3] = GaloisField.multiply(generator[0], feedback)
                    } else {
                        parity[0] = parity[1]
                        parity[1] = parity[2]
                        parity[2] = parity[3]
                        parity[3] = 0
                    }
                }
            }
            else -> {
                // General case
                return PolynomialMath.encode(data, generator)
            }
        }
        
        return parity
    }
    
    fun shutdown() {
        threadPool.shutdown()
    }
}