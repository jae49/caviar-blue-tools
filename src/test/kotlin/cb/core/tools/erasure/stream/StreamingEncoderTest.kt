package cb.core.tools.erasure.stream

import cb.core.tools.erasure.models.EncodingConfig
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.ByteArrayInputStream

class StreamingEncoderTest {
    
    @Test
    fun testBasicStreaming() = runBlocking {
        val encoder = StreamingEncoder()
        val config = EncodingConfig(dataShards = 4, parityShards = 2, shardSize = 10)
        val data = "Hello, World! This is a test of streaming encoding.".toByteArray()
        val input = ByteArrayInputStream(data)
        
        val shards = encoder.encodeStream(input, config).toList()
        
        assertTrue(shards.isNotEmpty())
        assertEquals(0, shards.first().index)
        assertEquals(config, shards.first().metadata.config)
    }
    
    @Test
    fun testChunkedEncoding() = runBlocking {
        val encoder = StreamingEncoder()
        val config = EncodingConfig(dataShards = 2, parityShards = 1, shardSize = 8)
        val data = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toByteArray()
        val input = ByteArrayInputStream(data)
        
        val chunks = encoder.encodeChunked(input, 16, config).toList()
        
        assertTrue(chunks.isNotEmpty())
        assertEquals(config.totalShards, chunks.first().size)
        
        // Verify chunk indexing
        chunks.forEachIndexed { index, shardList ->
            shardList.forEach { shard ->
                assertEquals(index, shard.metadata.chunkIndex)
            }
        }
    }
    
    @Test
    fun testBufferSizeConfiguration() = runBlocking {
        val encoder = StreamingEncoder()
        encoder.setBufferSize(512)
        
        val config = EncodingConfig(dataShards = 2, parityShards = 1, shardSize = 128)
        val data = ByteArray(1024) { it.toByte() }
        val input = ByteArrayInputStream(data)
        
        val shards = encoder.encodeStream(input, config).toList()
        
        assertTrue(shards.isNotEmpty())
    }
    
    @Test
    fun testEmptyStream() = runBlocking {
        val encoder = StreamingEncoder()
        val config = EncodingConfig(dataShards = 2, parityShards = 1)
        val input = ByteArrayInputStream(ByteArray(0))
        
        val shards = encoder.encodeStream(input, config).toList()
        
        assertTrue(shards.isEmpty())
    }
    
    @Test
    fun testSmallDataStreaming() = runBlocking {
        val encoder = StreamingEncoder()
        val config = EncodingConfig(dataShards = 3, parityShards = 2, shardSize = 4)
        val data = "ABC".toByteArray()
        val input = ByteArrayInputStream(data)
        
        val shards = encoder.encodeStream(input, config).toList()
        
        assertEquals(config.totalShards, shards.size)
        assertEquals(data.size.toLong(), shards.first().metadata.originalSize)
    }
}