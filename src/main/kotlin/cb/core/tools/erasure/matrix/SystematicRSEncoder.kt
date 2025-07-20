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
    
    // Cache for encoding matrices to avoid recomputation
    private val matrixCache = mutableMapOf<Pair<Int, Int>, Array<IntArray>>()
    
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
        
        // For systematic encoding, we need a special encoding matrix where:
        // - The first k rows form an identity matrix (for data shards)
        // - The remaining rows are from a Vandermonde matrix (for parity shards)
        
        // Generate encoding matrix for parity calculation
        // Use powers of primitive element 2 for better matrix properties
        // This ensures all k×k submatrices are invertible (MDS property)
        val parityMatrix = Array(parityShards) { i ->
            IntArray(dataShards) { j ->
                // Use evaluation point 2^(dataShards + i) for parity shard i
                val evalPoint = GaloisField.exp(dataShards + i)
                GaloisField.power(evalPoint, j)
            }
        }
        
        // Generate parity shards
        for (i in 0 until parityShards) {
            val parityShard = ByteArray(shardSize)
            
            // Process each byte position
            for (bytePos in 0 until shardSize) {
                // Extract data bytes at this position
                val dataBytes = IntArray(dataShards) { j ->
                    shards[j][bytePos].toInt() and 0xFF
                }
                
                // Multiply parity row by data vector
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
    
    /**
     * Gets the encoding matrix used for systematic Reed-Solomon encoding.
     * This is primarily for testing and debugging purposes.
     * 
     * @param dataShards Number of data shards
     * @param totalShards Total number of shards
     * @return The full encoding matrix
     */
    fun getEncodingMatrix(dataShards: Int, totalShards: Int): Array<IntArray> {
        val parityShards = totalShards - dataShards
        val matrix = Array(totalShards) { IntArray(dataShards) }
        
        // First k rows are identity matrix
        for (i in 0 until dataShards) {
            for (j in 0 until dataShards) {
                matrix[i][j] = if (i == j) 1 else 0
            }
        }
        
        // For parity rows, use powers of primitive element 2 for better matrix properties
        for (i in 0 until parityShards) {
            for (j in 0 until dataShards) {
                // Use evaluation point 2^(dataShards + i) for parity shard i
                val evalPoint = GaloisField.exp(dataShards + i)
                matrix[dataShards + i][j] = GaloisField.power(evalPoint, j)
            }
        }
        
        return matrix
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
        
        // Get or create the parity generation matrix
        val key = Pair(config.dataShards, config.parityShards)
        val parityMatrix = matrixCache.getOrPut(key) {
            generateParityMatrix(config.dataShards, config.parityShards)
        }
        
        // Generate parity shards
        for (parityIndex in 0 until config.parityShards) {
            val parityShard = ByteArray(config.shardSize)
            
            // Process each byte position
            for (bytePos in 0 until config.shardSize) {
                // Extract data bytes at this position from all data shards
                val dataBytes = IntArray(config.dataShards) { i ->
                    shards[i].data[bytePos].toInt() and 0xFF
                }
                
                // Multiply parity row by data vector
                var parityByte = 0
                for (j in 0 until config.dataShards) {
                    parityByte = GaloisField.add(
                        parityByte,
                        GaloisField.multiply(parityMatrix[parityIndex][j], dataBytes[j])
                    )
                }
                
                parityShard[bytePos] = parityByte.toByte()
            }
            
            val globalShardIndex = chunkIndex * config.totalShards + config.dataShards + parityIndex
            shards.add(Shard(globalShardIndex, parityShard, metadata))
        }
        
        return shards
    }
    
    private fun generateParityMatrix(dataShards: Int, parityShards: Int): Array<IntArray> {
        return Array(parityShards) { i ->
            IntArray(dataShards) { j ->
                // Use evaluation point 2^(dataShards + i) for parity shard i
                val evalPoint = GaloisField.exp(dataShards + i)
                GaloisField.power(evalPoint, j)
            }
        }
    }
}