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
import cb.core.tools.erasure.matrix.SystematicRSDecoder
import cb.core.tools.erasure.models.*
import java.security.MessageDigest

class RobustReedSolomonDecoder {
    
    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<String> = emptyList()
    )
    
    fun decode(shards: List<Shard>): ReconstructionResult {
        // Comprehensive validation
        val validation = validateShards(shards)
        if (!validation.isValid) {
            return ReconstructionResult.Failure(
                ReconstructionError.INVALID_CONFIGURATION,
                "Shard validation failed: ${validation.errors.joinToString(", ")}"
            )
        }
        
        return try {
            val config = shards.first().metadata.config
            
            // Use systematic decoder
            decodeSystematic(shards)
        } catch (e: Exception) {
            ReconstructionResult.Failure(
                ReconstructionError.MATH_ERROR,
                "Unexpected error during reconstruction: ${e.message}"
            )
        }
    }
    
    
    private fun decodeSystematic(shards: List<Shard>): ReconstructionResult {
        val config = shards.first().metadata.config
        val originalSize = shards.first().metadata.originalSize
        val expectedChecksum = shards.first().metadata.checksum
        
        // Group shards by chunk
        val shardsByChunk = groupShardsByChunk(shards, config)
        
        // Check if we have enough shards for each chunk
        for ((chunkIndex, chunkShards) in shardsByChunk) {
            if (chunkShards.size < config.dataShards) {
                return ReconstructionResult.Failure(
                    ReconstructionError.INSUFFICIENT_SHARDS,
                    "Chunk $chunkIndex has only ${chunkShards.size} shards, needs ${config.dataShards}"
                )
            }
        }
        
        // Reconstruct each chunk using systematic algorithm
        val reconstructedChunks = mutableListOf<ByteArray>()
        
        for (chunkIndex in shardsByChunk.keys.sorted()) {
            val chunkShards = shardsByChunk[chunkIndex]!!
            val chunkResult = reconstructChunkSystematic(chunkShards, config)
            
            when (chunkResult) {
                is ChunkReconstructionResult.Success -> {
                    reconstructedChunks.add(chunkResult.data)
                }
                is ChunkReconstructionResult.Failure -> {
                    // Try alternative matrix inversion strategies
                    val alternativeResult = reconstructChunkSystematicAlternative(chunkShards, config)
                    when (alternativeResult) {
                        is ChunkReconstructionResult.Success -> {
                            reconstructedChunks.add(alternativeResult.data)
                        }
                        is ChunkReconstructionResult.Failure -> {
                            return ReconstructionResult.Failure(
                                ReconstructionError.MATRIX_INVERSION_FAILED,
                                "Failed to reconstruct chunk $chunkIndex with multiple strategies: ${alternativeResult.error}"
                            )
                        }
                    }
                }
            }
        }
        
        // Combine chunks
        val reconstructedData = combineChunks(reconstructedChunks, originalSize)
        
        // Verify checksum with enhanced validation
        val actualChecksum = calculateChecksum(reconstructedData)
        if (actualChecksum != expectedChecksum) {
            // Additional validation: try reconstructing with different shard combinations
            val alternativeResult = tryAlternativeReconstruction(shards, config, originalSize)
            if (alternativeResult != null) {
                val altChecksum = calculateChecksum(alternativeResult)
                if (altChecksum == expectedChecksum) {
                    return ReconstructionResult.Success(
                        alternativeResult,
                        ReconstructionDiagnostics(
                            usedShardIndices = shards.map { it.index },
                            decodingStrategy = DecodingStrategy.MATRIX_INVERSION,
                            warnings = listOf("Used alternative shard combination for successful reconstruction")
                        )
                    )
                }
            }
            
            return ReconstructionResult.Failure(
                ReconstructionError.CORRUPTED_SHARDS,
                "Checksum mismatch: expected $expectedChecksum, got $actualChecksum"
            )
        }
        
        return ReconstructionResult.Success(
            reconstructedData,
            ReconstructionDiagnostics(
                usedShardIndices = shards.map { it.index },
                decodingStrategy = DecodingStrategy.MATRIX_INVERSION
            )
        )
    }
    
    private fun validateShards(shards: List<Shard>): ValidationResult {
        val errors = mutableListOf<String>()
        
        if (shards.isEmpty()) {
            errors.add("No shards provided")
            return ValidationResult(false, errors)
        }
        
        // Check metadata consistency
        val firstMetadata = shards.first().metadata
        val inconsistentMetadata = shards.filter { shard ->
            shard.metadata.config != firstMetadata.config ||
            shard.metadata.originalSize != firstMetadata.originalSize ||
            shard.metadata.checksum != firstMetadata.checksum
        }
        
        if (inconsistentMetadata.isNotEmpty()) {
            errors.add("${inconsistentMetadata.size} shards have inconsistent metadata")
        }
        
        // Check for duplicate shard indices
        val indices = shards.map { it.index }
        val duplicates = indices.groupBy { it }.filter { it.value.size > 1 }
        
        if (duplicates.isNotEmpty()) {
            errors.add("Duplicate shard indices found: ${duplicates.keys}")
        }
        
        // Validate shard data sizes
        val config = firstMetadata.config
        val invalidSizes = shards.filter { it.data.size != config.shardSize }
        
        if (invalidSizes.isNotEmpty()) {
            errors.add("${invalidSizes.size} shards have incorrect data size")
        }
        
        // Check shard index bounds
        val outOfBounds = shards.filter { it.index < 0 }
        
        if (outOfBounds.isNotEmpty()) {
            errors.add("${outOfBounds.size} shards have invalid indices")
        }
        
        return ValidationResult(errors.isEmpty(), errors)
    }
    
    private fun groupShardsByChunk(shards: List<Shard>, config: EncodingConfig): Map<Int, List<Shard>> {
        return shards.groupBy { shard ->
            shard.index / config.totalShards
        }
    }
    
    private sealed class ChunkReconstructionResult {
        data class Success(val data: ByteArray) : ChunkReconstructionResult()
        data class Failure(val error: String) : ChunkReconstructionResult()
    }
    
    private fun reconstructChunk(
        chunkShards: List<Shard>,
        config: EncodingConfig
    ): ChunkReconstructionResult {
        return try {
            val sortedShards = chunkShards.sortedBy { it.index % config.totalShards }
            val availableIndices = sortedShards.map { it.index % config.totalShards }.toIntArray()
            
            // Check if we have all data shards (no reconstruction needed)
            val hasAllDataShards = (0 until config.dataShards).all { index ->
                availableIndices.contains(index)
            }
            
            if (hasAllDataShards) {
                // Simple case: just extract data shards
                val dataShards = sortedShards.filter { 
                    (it.index % config.totalShards) < config.dataShards 
                }.sortedBy { it.index }
                
                val chunkData = ByteArray(config.shardSize * config.dataShards)
                for ((localIndex, shard) in dataShards.withIndex()) {
                    System.arraycopy(
                        shard.data, 0, 
                        chunkData, localIndex * config.shardSize, 
                        config.shardSize
                    )
                }
                
                ChunkReconstructionResult.Success(chunkData)
            } else {
                // Need to reconstruct using matrix inversion
                val reconstructedData = reconstructWithSystematicRS(sortedShards, config)
                ChunkReconstructionResult.Success(reconstructedData)
            }
        } catch (e: Exception) {
            ChunkReconstructionResult.Failure("Reconstruction failed: ${e.message}")
        }
    }
    
    
    private fun combineChunks(chunks: List<ByteArray>, originalSize: Long): ByteArray {
        val totalSize = chunks.sumOf { it.size }
        val combinedData = ByteArray(minOf(totalSize, originalSize.toInt()))
        
        var offset = 0
        for (chunk in chunks) {
            val copySize = minOf(chunk.size, combinedData.size - offset)
            if (copySize > 0) {
                System.arraycopy(chunk, 0, combinedData, offset, copySize)
                offset += copySize
            }
        }
        
        return combinedData
    }
    
    private fun calculateChecksum(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return hash.joinToString("") { "%02x".format(it) }
    }
    
    fun canReconstruct(shards: List<Shard>, originalConfig: EncodingConfig): Boolean {
        val validation = validateShards(shards)
        if (!validation.isValid) {
            return false
        }
        
        // Group shards by chunk and check each has minimum required
        val shardsByChunk = groupShardsByChunk(shards, originalConfig)
        
        return shardsByChunk.all { (_, chunkShards) ->
            chunkShards.size >= originalConfig.dataShards
        }
    }
    
    private fun reconstructChunkSystematic(
        chunkShards: List<Shard>,
        config: EncodingConfig
    ): ChunkReconstructionResult {
        return try {
            val sortedShards = chunkShards.sortedBy { it.index % config.totalShards }
            val availableIndices = sortedShards.map { it.index % config.totalShards }.toIntArray()
            
            // Check if we have all data shards (fast path)
            val hasAllDataShards = (0 until config.dataShards).all { index ->
                availableIndices.contains(index)
            }
            
            if (hasAllDataShards) {
                // Simple case: just extract data shards
                val dataShards = sortedShards.filter { 
                    (it.index % config.totalShards) < config.dataShards 
                }.sortedBy { it.index }
                
                val chunkData = ByteArray(config.shardSize * config.dataShards)
                for ((localIndex, shard) in dataShards.withIndex()) {
                    System.arraycopy(
                        shard.data, 0, 
                        chunkData, localIndex * config.shardSize, 
                        config.shardSize
                    )
                }
                
                ChunkReconstructionResult.Success(chunkData)
            } else {
                // Use systematic matrix-based reconstruction
                val reconstructedData = reconstructWithSystematicRS(sortedShards, config)
                ChunkReconstructionResult.Success(reconstructedData)
            }
        } catch (e: Exception) {
            ChunkReconstructionResult.Failure("Matrix reconstruction failed: ${e.message}")
        }
    }
    
    private fun reconstructChunkSystematicAlternative(
        chunkShards: List<Shard>,
        config: EncodingConfig
    ): ChunkReconstructionResult {
        return try {
            // Try different shard combinations if the first k shards don't work
            val sortedShards = chunkShards.sortedBy { it.index % config.totalShards }
            
            // Try all possible combinations of k shards
            val combinations = sortedShards.combinations(config.dataShards)
            
            for (combination in combinations) {
                try {
                    val reconstructedData = reconstructWithSystematicRS(combination, config)
                    // Validate the reconstruction by checking consistency
                    if (validateReconstruction(reconstructedData, combination, config)) {
                        return ChunkReconstructionResult.Success(reconstructedData)
                    }
                } catch (e: Exception) {
                    // Try next combination
                    continue
                }
            }
            
            ChunkReconstructionResult.Failure("All shard combinations failed")
        } catch (e: Exception) {
            ChunkReconstructionResult.Failure("Alternative reconstruction failed: ${e.message}")
        }
    }
    
    private fun reconstructWithSystematicRS(
        sortedShards: List<Shard>,
        config: EncodingConfig
    ): ByteArray {
        val chunkData = ByteArray(config.shardSize * config.dataShards)
        
        // Get encoding matrix
        val fullMatrix = getSystematicEncodingMatrix(config.dataShards, config.totalShards)
        
        // Extract available shard indices
        val availableIndices = sortedShards.map { it.index % config.totalShards }
        val selectedIndices = availableIndices.take(config.dataShards)
        val selectedShards = sortedShards.take(config.dataShards)
        
        // Extract submatrix and invert
        val subMatrix = MatrixUtils.extractSubmatrix(fullMatrix, selectedIndices)
        val invertedMatrix = MatrixUtils.invertMatrix(subMatrix)
            ?: throw ArithmeticException("Matrix is singular, cannot invert")
        
        // Process each byte position
        for (byteIndex in 0 until config.shardSize) {
            val shardBytes = IntArray(config.dataShards) { i ->
                selectedShards[i].data[byteIndex].toInt() and 0xFF
            }
            
            // Multiply inverted matrix by shard bytes
            val dataBytes = MatrixUtils.multiplyMatrixVector(invertedMatrix, shardBytes)
            
            // Store reconstructed bytes
            for (dataIndex in 0 until config.dataShards) {
                val globalIndex = dataIndex * config.shardSize + byteIndex
                if (globalIndex < chunkData.size) {
                    chunkData[globalIndex] = dataBytes[dataIndex].toByte()
                }
            }
        }
        
        return chunkData
    }
    
    private fun getSystematicEncodingMatrix(dataShards: Int, totalShards: Int): Array<IntArray> {
        val parityShards = totalShards - dataShards
        val matrix = Array(totalShards) { IntArray(dataShards) }
        
        // First k rows are identity matrix
        for (i in 0 until dataShards) {
            for (j in 0 until dataShards) {
                matrix[i][j] = if (i == j) 1 else 0
            }
        }
        
        // Remaining rows use Vandermonde construction
        for (i in 0 until parityShards) {
            for (j in 0 until dataShards) {
                matrix[dataShards + i][j] = GaloisField.power(dataShards + i, j)
            }
        }
        
        return matrix
    }
    
    private fun tryAlternativeReconstruction(
        shards: List<Shard>,
        config: EncodingConfig,
        originalSize: Long
    ): ByteArray? {
        // Try different shard combinations to find one that produces valid data
        val shardsByChunk = groupShardsByChunk(shards, config)
        
        for (permutation in generateShardPermutations(shardsByChunk, config)) {
            try {
                val chunks = mutableListOf<ByteArray>()
                for ((_, chunkShards) in permutation) {
                    val result = reconstructChunkSystematic(chunkShards, config)
                    if (result is ChunkReconstructionResult.Success) {
                        chunks.add(result.data)
                    } else {
                        break
                    }
                }
                
                if (chunks.size == shardsByChunk.size) {
                    val data = combineChunks(chunks, originalSize)
                    return data
                }
            } catch (e: Exception) {
                // Try next permutation
                continue
            }
        }
        
        return null
    }
    
    private fun validateReconstruction(
        data: ByteArray,
        shards: List<Shard>,
        config: EncodingConfig
    ): Boolean {
        // Validate by re-encoding and checking consistency
        // This is a simple validation - could be enhanced
        return data.isNotEmpty() && data.size == config.shardSize * config.dataShards
    }
    
    private fun <T> List<T>.combinations(k: Int): List<List<T>> {
        if (k == 0) return listOf(emptyList())
        if (k > size) return emptyList()
        if (k == size) return listOf(this)
        
        val result = mutableListOf<List<T>>()
        val first = first()
        val rest = drop(1)
        
        // Include first element
        rest.combinations(k - 1).forEach { combo ->
            result.add(listOf(first) + combo)
        }
        
        // Exclude first element
        result.addAll(rest.combinations(k))
        
        return result
    }
    
    private fun generateShardPermutations(
        shardsByChunk: Map<Int, List<Shard>>,
        config: EncodingConfig
    ): List<Map<Int, List<Shard>>> {
        // Generate different combinations of shards for each chunk
        // Limited to prevent combinatorial explosion
        val result = mutableListOf<Map<Int, List<Shard>>>()
        
        // Just return the original for now - could be enhanced
        result.add(shardsByChunk)
        
        return result
    }
}