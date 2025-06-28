package cb.core.tools.erasure

import cb.core.tools.erasure.models.EncodingConfig
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows

class ReedSolomonEncoderTest {
    
    private val encoder = ReedSolomonEncoder()
    
    @Test
    fun `test basic encoding with simple data`() {
        val data = "Hello, World!".toByteArray()
        val config = EncodingConfig(dataShards = 3, parityShards = 2, shardSize = 64)
        
        val shards = encoder.encode(data, config)
        
        assertEquals(config.totalShards, shards.size)
        assertTrue(shards.all { it.metadata.originalSize == data.size.toLong() })
        assertTrue(shards.all { it.metadata.config == config })
    }
    
    @Test
    fun `test encoding creates correct number of data and parity shards`() {
        val data = ByteArray(100) { it.toByte() }
        val config = EncodingConfig(dataShards = 4, parityShards = 3, shardSize = 32)
        
        val shards = encoder.encode(data, config)
        
        val dataShards = shards.filter { it.isDataShard }
        val parityShards = shards.filter { it.isParityShard }
        
        assertEquals(config.dataShards, dataShards.size)
        assertEquals(config.parityShards, parityShards.size)
    }
    
    @Test
    fun `test encoding with large data`() {
        val data = ByteArray(10000) { (it % 256).toByte() }
        val config = EncodingConfig(dataShards = 6, parityShards = 4, shardSize = 1024)
        
        val shards = encoder.encode(data, config)
        
        assertTrue(shards.isNotEmpty())
        assertTrue(shards.all { it.data.size == config.shardSize })
        assertTrue(shards.all { it.metadata.originalSize == data.size.toLong() })
    }
    
    @Test
    fun `test encoding with empty data throws exception`() {
        val data = ByteArray(0)
        val config = EncodingConfig(dataShards = 2, parityShards = 1)
        
        assertThrows(IllegalArgumentException::class.java) {
            encoder.encode(data, config)
        }
    }
    
    @Test
    fun `test encoding preserves data integrity in checksum`() {
        val data = "Test data for checksum verification".toByteArray()
        val config = EncodingConfig(dataShards = 3, parityShards = 2)
        
        val shards1 = encoder.encode(data, config)
        val shards2 = encoder.encode(data, config)
        
        assertEquals(shards1.first().metadata.checksum, shards2.first().metadata.checksum)
    }
    
    @Test
    fun `test shard indices are correctly assigned`() {
        val data = ByteArray(200) { it.toByte() }
        val config = EncodingConfig(dataShards = 3, parityShards = 2, shardSize = 100)
        
        val shards = encoder.encode(data, config)
        
        val indices = shards.map { it.index }.sorted()
        assertEquals((0 until config.totalShards).toList(), indices)
    }
    
    @Test
    fun `test metadata consistency across shards`() {
        val data = "Consistency test data".toByteArray()
        val config = EncodingConfig(dataShards = 2, parityShards = 1)
        
        val shards = encoder.encode(data, config)
        
        val firstMetadata = shards.first().metadata
        assertTrue(shards.all { it.metadata.originalSize == firstMetadata.originalSize })
        assertTrue(shards.all { it.metadata.config == firstMetadata.config })
        assertTrue(shards.all { it.metadata.checksum == firstMetadata.checksum })
    }
}