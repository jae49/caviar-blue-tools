package cb.core.tools.erasure.models

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows

class EncodingConfigTest {
    
    @Test
    fun `test valid configuration creation`() {
        val config = EncodingConfig(dataShards = 4, parityShards = 2, shardSize = 1024)
        
        assertEquals(4, config.dataShards)
        assertEquals(2, config.parityShards)
        assertEquals(1024, config.shardSize)
        assertEquals(6, config.totalShards)
    }
    
    @Test
    fun `test default shard size`() {
        val config = EncodingConfig(dataShards = 3, parityShards = 2)
        
        assertEquals(8192, config.shardSize)
    }
    
    @Test
    fun `test total shards calculation`() {
        val config = EncodingConfig(dataShards = 10, parityShards = 5)
        
        assertEquals(15, config.totalShards)
    }
    
    @Test
    fun `test zero data shards throws exception`() {
        assertThrows(IllegalArgumentException::class.java) {
            EncodingConfig(dataShards = 0, parityShards = 2)
        }
    }
    
    @Test
    fun `test negative data shards throws exception`() {
        assertThrows(IllegalArgumentException::class.java) {
            EncodingConfig(dataShards = -1, parityShards = 2)
        }
    }
    
    @Test
    fun `test zero parity shards throws exception`() {
        assertThrows(IllegalArgumentException::class.java) {
            EncodingConfig(dataShards = 3, parityShards = 0)
        }
    }
    
    @Test
    fun `test negative parity shards throws exception`() {
        assertThrows(IllegalArgumentException::class.java) {
            EncodingConfig(dataShards = 3, parityShards = -1)
        }
    }
    
    @Test
    fun `test maximum total shards allowed`() {
        val config = EncodingConfig(dataShards = 200, parityShards = 56)
        
        assertEquals(256, config.totalShards)
    }
    
    @Test
    fun `test exceeding maximum total shards throws exception`() {
        assertThrows(IllegalArgumentException::class.java) {
            EncodingConfig(dataShards = 200, parityShards = 57)
        }
    }
    
    @Test
    fun `test zero shard size throws exception`() {
        assertThrows(IllegalArgumentException::class.java) {
            EncodingConfig(dataShards = 3, parityShards = 2, shardSize = 0)
        }
    }
    
    @Test
    fun `test negative shard size throws exception`() {
        assertThrows(IllegalArgumentException::class.java) {
            EncodingConfig(dataShards = 3, parityShards = 2, shardSize = -100)
        }
    }
}