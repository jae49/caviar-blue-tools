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

package cb.core.tools.erasure.stream

import cb.core.tools.erasure.ReedSolomonEncoder
import cb.core.tools.erasure.models.EncodingConfig
import cb.core.tools.erasure.models.Shard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.InputStream

class StreamingEncoder {
    private val encoder = ReedSolomonEncoder()
    private var bufferSize = 1024 * 1024 // 1MB default buffer size
    
    fun setBufferSize(size: Int) {
        require(size > 0) { "Buffer size must be positive" }
        bufferSize = size
    }
    
    fun encodeStream(input: InputStream, config: EncodingConfig): Flow<Shard> = flow {
        val buffer = ByteArray(bufferSize)
        var bytesRead = input.read(buffer)
        
        while (bytesRead > 0) {
            val data = if (bytesRead < bufferSize) {
                buffer.copyOf(bytesRead)
            } else {
                buffer.copyOf()
            }
            
            val shards = encoder.encode(data, config)
            for (shard in shards) {
                emit(shard)
            }
            
            bytesRead = input.read(buffer)
        }
    }
    
    fun encodeChunked(
        input: InputStream,
        chunkSize: Int,
        config: EncodingConfig
    ): Flow<List<Shard>> = flow {
        require(chunkSize > 0) { "Chunk size must be positive" }
        
        val buffer = ByteArray(chunkSize)
        var bytesRead = input.read(buffer)
        var chunkIndex = 0
        
        while (bytesRead > 0) {
            val data = if (bytesRead < chunkSize) {
                buffer.copyOf(bytesRead)
            } else {
                buffer.copyOf()
            }
            
            val shards = encoder.encode(data, config)
            val chunkedShards = shards.map { shard ->
                shard.copy(
                    metadata = shard.metadata.copy(
                        chunkIndex = chunkIndex
                    )
                )
            }
            
            emit(chunkedShards)
            chunkIndex++
            bytesRead = input.read(buffer)
        }
    }
}