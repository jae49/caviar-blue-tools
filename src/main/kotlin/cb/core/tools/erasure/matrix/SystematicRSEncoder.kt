package cb.core.tools.erasure.matrix

import cb.core.tools.erasure.math.GaloisField
import cb.core.tools.erasure.models.*
import java.security.MessageDigest

/**
 * Systematic Reed-Solomon encoder using matrix multiplication.
 * 
 * This encoder implements a systematic Reed-Solomon code where:
 * - The first k shards contain the original data unchanged
 * - The remaining n-k shards contain parity data
 * 
 * The systematic property ensures that if no data shards are lost,
 * reconstruction requires no computation.
 */
class SystematicRSEncoder {

    /**
     * Encodes data into systematic Reed-Solomon shards using the full API.
     * 
     * @param data Input data to encode
     * @param config Encoding configuration
     * @return List of Shard objects with proper metadata
     */
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
        
        val shards = mutableListOf<Shard>()
        
        for (chunkIndex in chunks.indices) {
            val chunk = chunks[chunkIndex]
            val encodedShards = encodeChunk(chunk, config, metadata, chunkIndex)
            shards.addAll(encodedShards)
        }
        
        return shards
    }
    
    /**
     * Encodes data into systematic Reed-Solomon shards (simple interface).
     * 
     * @param data Input data to encode
     * @param dataShards Number of data shards (k)
     * @param parityShards Number of parity shards (n-k)
     * @return List of encoded shards where the first k are data shards
     *         and the remaining are parity shards
     */
    fun encode(data: ByteArray, dataShards: Int, parityShards: Int): List<ByteArray> {
        require(dataShards > 0) { "dataShards must be positive" }
        require(parityShards > 0) { "parityShards must be positive" }
        require(data.isNotEmpty()) { "data cannot be empty" }
        
        val totalShards = dataShards + parityShards
        require(totalShards <= 256) { "Total shards cannot exceed 256 for GF(256)" }
        
        // Calculate shard size (pad if necessary)
        val shardSize = (data.size + dataShards - 1) / dataShards
        
        // Create data shards by splitting input data
        val shards = mutableListOf<ByteArray>()
        for (i in 0 until dataShards) {
            val shard = ByteArray(shardSize)
            val start = i * shardSize
            val end = minOf(start + shardSize, data.size)
            if (start < data.size) {
                data.copyInto(shard, 0, start, end)
                // Remaining bytes are already 0 from ByteArray initialization
            }
            shards.add(shard)
        }
        
        // Systematic encoding: the first k rows of the generator are the identity
        // (the data shards above) and the parity rows are a Cauchy matrix, which
        // guarantees the MDS property (recover from ANY k of n). See RSMatrix.
        val parityMatrix = RSMatrix.parityMatrix(dataShards, parityShards)

        // Generate parity shards a whole shard at a time via region multiply-add.
        for (i in 0 until parityShards) {
            val parityShard = ByteArray(shardSize)
            for (j in 0 until dataShards) {
                GaloisField.multiplyRegionInto(parityMatrix[i][j], shards[j], parityShard)
            }
            shards.add(parityShard)
        }

        return shards
    }
    
    /**
     * Gets the encoding matrix used for systematic Reed-Solomon encoding.
     * This is primarily for testing and debugging purposes.
     * 
     * @param dataShards Number of data shards
     * @param totalShards Total number of shards
     * @return The full encoding matrix
     */
    fun getEncodingMatrix(dataShards: Int, totalShards: Int): Array<IntArray> =
        RSMatrix.generator(dataShards, totalShards)
    
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
    
    private fun encodeChunk(
        chunk: ByteArray,
        config: EncodingConfig,
        metadata: ShardMetadata,
        chunkIndex: Int
    ): List<Shard> {
        val shards = mutableListOf<Shard>()
        
        // Create data shards by splitting the chunk
        for (shardIndex in 0 until config.dataShards) {
            val shardData = ByteArray(config.shardSize)
            val startIndex = shardIndex * config.shardSize
            val endIndex = minOf(startIndex + config.shardSize, chunk.size)
            
            if (startIndex < chunk.size) {
                System.arraycopy(chunk, startIndex, shardData, 0, endIndex - startIndex)
            }
            
            val globalShardIndex = chunkIndex * config.totalShards + shardIndex
            shards.add(Shard(globalShardIndex, shardData, metadata))
        }
        
        // Cauchy parity matrix (MDS, cached in RSMatrix).
        val parityMatrix = RSMatrix.parityMatrix(config.dataShards, config.parityShards)

        // Generate parity shards a whole shard at a time via region multiply-add.
        for (parityIndex in 0 until config.parityShards) {
            val parityShard = ByteArray(config.shardSize)
            for (j in 0 until config.dataShards) {
                GaloisField.multiplyRegionInto(parityMatrix[parityIndex][j], shards[j].data, parityShard)
            }
            val globalShardIndex = chunkIndex * config.totalShards + config.dataShards + parityIndex
            shards.add(Shard(globalShardIndex, parityShard, metadata))
        }

        return shards
    }
}