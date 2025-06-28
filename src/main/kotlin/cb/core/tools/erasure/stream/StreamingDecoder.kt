package cb.core.tools.erasure.stream

import cb.core.tools.erasure.ReedSolomonDecoder
import cb.core.tools.erasure.models.ReconstructionError
import cb.core.tools.erasure.models.ReconstructionResult
import cb.core.tools.erasure.models.Shard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class StreamingDecoder {
    private val decoder = ReedSolomonDecoder()
    
    fun decodeStream(shardFlow: Flow<List<Shard>>): Flow<ByteArray> = flow {
        val chunkMap = mutableMapOf<Int, MutableList<Shard>>()
        var processedChunks = mutableSetOf<Int>()
        
        shardFlow.collect { shards ->
            for (shard in shards) {
                val chunkIndex = shard.metadata.chunkIndex ?: 0
                chunkMap.getOrPut(chunkIndex) { mutableListOf() }.add(shard)
            }
            
            val completableChunks = chunkMap.filterKeys { it !in processedChunks }
                .filter { (_, shards) -> 
                    val config = shards.firstOrNull()?.metadata?.config
                    config != null && decoder.canReconstruct(shards, config)
                }
            
            for ((chunkIndex, chunkShards) in completableChunks.toList().sortedBy { it.first }) {
                val result = decoder.decode(chunkShards)
                when (result) {
                    is ReconstructionResult.Success -> {
                        emit(result.data)
                        processedChunks.add(chunkIndex)
                        chunkMap.remove(chunkIndex)
                    }
                    is ReconstructionResult.Failure -> {
                        throw ReconstructionException(
                            "Failed to reconstruct chunk $chunkIndex: ${result.error}",
                            result.error
                        )
                    }
                    is ReconstructionResult.Partial -> {
                        // Keep collecting more shards for this chunk
                    }
                }
            }
        }
        
        if (chunkMap.isNotEmpty()) {
            val unprocessedChunks = chunkMap.keys.sorted().joinToString(", ")
            throw ReconstructionException(
                "Unable to reconstruct chunks: $unprocessedChunks",
                ReconstructionError.INSUFFICIENT_SHARDS
            )
        }
    }
}

class ReconstructionException(
    message: String,
    val error: ReconstructionError
) : Exception(message)