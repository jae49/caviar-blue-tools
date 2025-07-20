package cb.core.tools.erasure.matrix

import cb.core.tools.erasure.math.GaloisField
import cb.core.tools.erasure.models.*
import java.security.MessageDigest

/**
 * Systematic Reed-Solomon decoder using matrix inversion.
 * 
 * This decoder can reconstruct the original data from any k shards out of n total shards,
 * where k is the number of data shards. This is the key advantage over polynomial
 * division-based approaches.
 */
class SystematicRSDecoder {
    
    /**
     * Decodes data from available Reed-Solomon shards using the full API.
     * 
     * @param shards List of available Shard objects
     * @return ReconstructionResult indicating success or failure
     */
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
                ReconstructionResult.Success(
                    reconstructedData,
                    ReconstructionDiagnostics(
                        usedShardIndices = shards.map { it.index },
                        decodingStrategy = DecodingStrategy.MATRIX_INVERSION
                    )
                )
            } else {
                ReconstructionResult.Failure(ReconstructionError.CORRUPTED_SHARDS)
            }
        } catch (e: ArithmeticException) {
            ReconstructionResult.Failure(
                ReconstructionError.MATRIX_INVERSION_FAILED,
                "Matrix inversion failed: ${e.message}"
            )
        } catch (e: IllegalArgumentException) {
            ReconstructionResult.Failure(
                ReconstructionError.INVALID_CONFIGURATION,
                "Invalid configuration: ${e.message}"
            )
        } catch (e: Exception) {
            ReconstructionResult.Failure(
                ReconstructionError.MATH_ERROR,
                "Reconstruction failed: ${e.message}"
            )
        }
    }
    
    /**
     * Decodes data from available Reed-Solomon shards.
     * 
     * @param shards List of available shards (some may be missing/null)
     * @param shardIndices List of indices corresponding to available shards
     * @param dataShards Number of data shards (k)
     * @param totalShards Total number of shards (n)
     * @return Reconstructed data, or null if reconstruction fails
     */
    fun decode(
        shards: List<ByteArray>,
        shardIndices: List<Int>,
        dataShards: Int,
        totalShards: Int
    ): ByteArray? {
        require(shards.size == shardIndices.size) { 
            "Number of shards must match number of indices" 
        }
        require(shards.size >= dataShards) { 
            "Need at least $dataShards shards for reconstruction, got ${shards.size}" 
        }
        require(shardIndices.all { it in 0 until totalShards }) { 
            "Invalid shard indices" 
        }
        require(shards.all { it.size == shards[0].size }) { 
            "All shards must have the same size" 
        }
        
        val shardSize = shards[0].size
        
        // Check if we have all data shards (fast path)
        val dataShardIndices = (0 until dataShards).toList()
        if (shardIndices.containsAll(dataShardIndices)) {
            // We have all data shards, just concatenate them
            val result = ByteArray(dataShards * shardSize)
            for (i in 0 until dataShards) {
                val shardIndex = shardIndices.indexOf(i)
                shards[shardIndex].copyInto(result, i * shardSize)
            }
            return result
        }
        
        // Generate the full encoding matrix (same as encoder uses)
        val fullMatrix = getEncodingMatrix(dataShards, totalShards)
        
        // We need to form a system of equations to solve for the data shards
        // The encoding matrix tells us how each shard was generated:
        // shard[i] = sum(matrix[i][j] * dataBlock[j]) for all j
        
        // Take the first dataShards available shards and their corresponding rows
        val selectedIndices = shardIndices.take(dataShards)
        val selectedShards = shards.take(dataShards)
        
        // Extract the rows from the encoding matrix corresponding to our available shards
        val subMatrix = MatrixUtils.extractSubmatrix(fullMatrix, selectedIndices)
        
        // To find the data blocks, we need to solve: subMatrix * data = selectedShards
        // Therefore: data = subMatrix^(-1) * selectedShards
        val invertedMatrix = MatrixUtils.invertMatrix(subMatrix)
            ?: return null // Matrix is singular, cannot reconstruct
        
        // Reconstruct data shards
        val reconstructedShards = Array(dataShards) { ByteArray(shardSize) }
        
        // Process each byte position
        for (bytePos in 0 until shardSize) {
            // Extract bytes at this position from selected shards
            val shardBytes = IntArray(dataShards) { i ->
                selectedShards[i][bytePos].toInt() and 0xFF
            }
            
            // Multiply inverted matrix by shard bytes to get original data bytes
            val dataBytes = MatrixUtils.multiplyMatrixVector(invertedMatrix, shardBytes)
            
            // Store reconstructed bytes
            for (i in 0 until dataShards) {
                reconstructedShards[i][bytePos] = dataBytes[i].toByte()
            }
        }
        
        // Concatenate reconstructed data shards
        val result = ByteArray(dataShards * shardSize)
        for (i in 0 until dataShards) {
            reconstructedShards[i].copyInto(result, i * shardSize)
        }
        
        return result
    }
    
    /**
     * Gets the encoding matrix used for systematic Reed-Solomon encoding.
     * Must match the matrix used by the encoder.
     * 
     * @param dataShards Number of data shards
     * @param totalShards Total number of shards
     * @return The full encoding matrix
     */
    private fun getEncodingMatrix(dataShards: Int, totalShards: Int): Array<IntArray> {
        val parityShards = totalShards - dataShards
        val matrix = Array(totalShards) { IntArray(dataShards) }
        
        // First k rows are identity matrix
        for (i in 0 until dataShards) {
            for (j in 0 until dataShards) {
                matrix[i][j] = if (i == j) 1 else 0
            }
        }
        
        // For parity rows, use powers of primitive element 2 to match encoder
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
    
    private fun canReconstruct(shards: List<Shard>, config: EncodingConfig): Boolean {
        return shards.size >= config.dataShards
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
        // Sort shards by their local index within the chunk
        val sortedShards = shards.sortedBy { it.index % config.totalShards }
        
        // Extract shard data and indices
        val shardData = sortedShards.map { it.data }.toTypedArray()
        val shardIndices = sortedShards.map { it.index % config.totalShards }
        
        // Use the simple decode method
        val result = decode(
            shardData.toList(),
            shardIndices,
            config.dataShards,
            config.totalShards
        ) ?: throw IllegalStateException("Failed to reconstruct chunk")
        
        return result
    }
    
    private fun combineChunks(chunks: List<ByteArray>): ByteArray {
        val totalSize = chunks.sumOf { it.size }
        val result = ByteArray(totalSize)
        var offset = 0
        
        for (chunk in chunks) {
            chunk.copyInto(result, offset)
            offset += chunk.size
        }
        
        return result
    }
}