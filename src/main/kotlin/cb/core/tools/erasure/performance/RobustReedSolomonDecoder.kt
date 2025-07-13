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

import cb.core.tools.erasure.math.PolynomialMath
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
            
            // Reconstruct each chunk
            val reconstructedChunks = mutableListOf<ByteArray>()
            
            for (chunkIndex in shardsByChunk.keys.sorted()) {
                val chunkShards = shardsByChunk[chunkIndex]!!
                val chunkResult = reconstructChunk(chunkShards, config)
                
                when (chunkResult) {
                    is ChunkReconstructionResult.Success -> {
                        reconstructedChunks.add(chunkResult.data)
                    }
                    is ChunkReconstructionResult.Failure -> {
                        return ReconstructionResult.Failure(
                            ReconstructionError.MATH_ERROR,
                            "Failed to reconstruct chunk $chunkIndex: ${chunkResult.error}"
                        )
                    }
                }
            }
            
            // Combine chunks
            val reconstructedData = combineChunks(reconstructedChunks, originalSize)
            
            // Verify checksum
            val actualChecksum = calculateChecksum(reconstructedData)
            if (actualChecksum != expectedChecksum) {
                return ReconstructionResult.Failure(
                    ReconstructionError.CORRUPTED_SHARDS,
                    "Checksum mismatch: expected $expectedChecksum, got $actualChecksum"
                )
            }
            
            ReconstructionResult.Success(reconstructedData)
            
        } catch (e: Exception) {
            ReconstructionResult.Failure(
                ReconstructionError.MATH_ERROR,
                "Unexpected error during reconstruction: ${e.message}"
            )
        }
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
                // Need to reconstruct using Reed-Solomon decoding
                val reconstructedData = reconstructWithReedSolomon(sortedShards, config)
                ChunkReconstructionResult.Success(reconstructedData)
            }
        } catch (e: Exception) {
            ChunkReconstructionResult.Failure("Reconstruction failed: ${e.message}")
        }
    }
    
    private fun reconstructWithReedSolomon(
        sortedShards: List<Shard>,
        config: EncodingConfig
    ): ByteArray {
        val chunkData = ByteArray(config.shardSize * config.dataShards)
        
        // Process each byte position across all shards
        for (byteIndex in 0 until config.shardSize) {
            val shardData = Array<Int?>(config.totalShards) { null }
            
            // Fill in available shard data
            for (shard in sortedShards) {
                val localIndex = shard.index % config.totalShards
                shardData[localIndex] = shard.data[byteIndex].toInt() and 0xFF
            }
            
            // Find erasures (missing shards)
            val erasures = shardData.indices.filter { shardData[it] == null }.toIntArray()
            
            // Convert Array<Int?> to Array<IntArray?> format expected by decode
            val shardArrays = Array<IntArray?>(config.totalShards) { index ->
                shardData[index]?.let { intArrayOf(it) }
            }
            
            // Decode using Reed-Solomon
            val decoded = PolynomialMath.decode(shardArrays, erasures, config.dataShards, config.parityShards)
            
            // Extract data bytes
            for (dataIndex in 0 until config.dataShards) {
                val globalIndex = dataIndex * config.shardSize + byteIndex
                if (globalIndex < chunkData.size && decoded != null) {
                    chunkData[globalIndex] = decoded[dataIndex].toByte()
                }
            }
        }
        
        return chunkData
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
}