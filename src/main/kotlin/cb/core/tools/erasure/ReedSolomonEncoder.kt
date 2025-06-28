package cb.core.tools.erasure

import cb.core.tools.erasure.math.PolynomialMath
import cb.core.tools.erasure.models.*
import java.security.MessageDigest

class ReedSolomonEncoder {
    
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
        
        val generator = PolynomialMath.generateGenerator(config.parityShards)
        val shards = mutableListOf<Shard>()
        
        for (chunkIndex in chunks.indices) {
            val chunk = chunks[chunkIndex]
            val dataShards = createDataShards(chunk, config, metadata, chunkIndex)
            val parityShards = createParityShards(chunk, generator, config, metadata, chunkIndex)
            
            shards.addAll(dataShards)
            shards.addAll(parityShards)
        }
        
        return shards
    }
    
    private fun calculateChecksum(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return hash.joinToString("") { "%02x".format(it) }
    }
    
    private fun padData(data: ByteArray, config: EncodingConfig): ByteArray {
        val totalDataSize = ((data.size + config.shardSize - 1) / config.shardSize) * config.shardSize
        val paddedData = ByteArray(totalDataSize)
        System.arraycopy(data, 0, paddedData, 0, data.size)
        return paddedData
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
        val dataShards = mutableListOf<Shard>()
        
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
    
    private fun createParityShards(
        chunk: ByteArray,
        generator: IntArray,
        config: EncodingConfig,
        metadata: ShardMetadata,
        chunkIndex: Int
    ): List<Shard> {
        val parityShards = mutableListOf<Shard>()
        
        for (byteIndex in 0 until config.shardSize) {
            val dataBytes = IntArray(config.dataShards)
            
            for (shardIndex in 0 until config.dataShards) {
                val globalByteIndex = shardIndex * config.shardSize + byteIndex
                dataBytes[shardIndex] = if (globalByteIndex < chunk.size) {
                    chunk[globalByteIndex].toInt() and 0xFF
                } else {
                    0
                }
            }
            
            val parityBytes = PolynomialMath.encode(dataBytes, generator)
            
            for (parityIndex in parityBytes.indices) {
                val shardIndex = config.dataShards + parityIndex
                val globalShardIndex = chunkIndex * config.totalShards + shardIndex
                
                if (parityIndex >= parityShards.size) {
                    val newParityShard = Shard(
                        globalShardIndex,
                        ByteArray(config.shardSize),
                        metadata
                    )
                    parityShards.add(newParityShard)
                }
                
                parityShards[parityIndex].data[byteIndex] = parityBytes[parityIndex].toByte()
            }
        }
        
        return parityShards
    }
}