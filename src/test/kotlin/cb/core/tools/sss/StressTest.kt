package cb.core.tools.sss

import cb.core.tools.sss.models.SSSConfig
import cb.core.tools.sss.models.SSSResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

@Tag("slow")
class StressTest {
    
    private val sss = ShamirSecretSharing()
    
    @Test
    fun `rapid sequential split and reconstruct operations`() {
        val config = SSSConfig(3, 5)
        val iterations = 1000
        val secret = "Rapid fire test secret".toByteArray()
        
        var successCount = 0
        val startTime = System.currentTimeMillis()
        
        repeat(iterations) {
            val splitResult = sss.split(secret, config)
            assertTrue(splitResult is SSSResult.Success)
            
            val shares = (splitResult as SSSResult.Success).value.shares
            val reconstructResult = sss.reconstruct(shares.take(config.threshold))
            assertTrue(reconstructResult is SSSResult.Success)
            
            val reconstructed = (reconstructResult as SSSResult.Success).value
            assertArrayEquals(secret, reconstructed)
            successCount++
        }
        
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        val opsPerSecond = (iterations * 1000.0) / duration
        
        println("Rapid sequential test: $iterations iterations in ${duration}ms (${opsPerSecond.toInt()} ops/sec)")
        assertEquals(iterations, successCount)
    }
    
    @Test
    fun `concurrent split operations`() = runBlocking {
        val config = SSSConfig(5, 10)
        val concurrentOps = 100
        val secrets = (0 until concurrentOps).map { i ->
            "Concurrent secret #$i with some padding".toByteArray()
        }
        
        val results = secrets.map { secret ->
            async {
                sss.split(secret, config)
            }
        }.awaitAll()
        
        // Verify all operations succeeded
        results.forEachIndexed { index, result ->
            assertTrue(result is SSSResult.Success, "Split operation $index failed")
            val shares = (result as SSSResult.Success).value.shares
            assertEquals(config.totalShares, shares.size)
            
            // Verify reconstruction works
            val reconstructResult = sss.reconstruct(shares.take(config.threshold))
            assertTrue(reconstructResult is SSSResult.Success)
            assertArrayEquals(secrets[index], (reconstructResult as SSSResult.Success).value)
        }
    }
    
    @Test
    fun `concurrent reconstruct operations`() = runBlocking {
        val config = SSSConfig(3, 6)
        val concurrentOps = 100
        
        // First create shares for all secrets
        val sharesSets = (0 until concurrentOps).map { i ->
            val secret = "Concurrent reconstruction test #$i".toByteArray()
            val splitResult = sss.split(secret, config)
            assertTrue(splitResult is SSSResult.Success)
            (splitResult as SSSResult.Success).value.shares to secret
        }
        
        // Concurrently reconstruct all
        val results = sharesSets.map { (shares, originalSecret) ->
            async {
                sss.reconstruct(shares.take(config.threshold)) to originalSecret
            }
        }.awaitAll()
        
        // Verify all reconstructions succeeded and match
        results.forEach { (result, originalSecret) ->
            assertTrue(result is SSSResult.Success)
            val reconstructed = (result as SSSResult.Success).value
            assertArrayEquals(originalSecret, reconstructed)
        }
    }
    
    @Test
    fun `maximum shares stress test`() {
        val maxConfig = SSSConfig(64, 128)
        val secretSizes = listOf(10, 100, 500, 1024)
        
        println("\n=== Maximum Shares Stress Test ===")
        
        for (size in secretSizes) {
            val secret = ByteArray(size) { (it * 7 % 256).toByte() }
            val startTime = System.currentTimeMillis()
            
            val splitResult = sss.split(secret, maxConfig)
            assertTrue(splitResult is SSSResult.Success)
            
            val shares = (splitResult as SSSResult.Success).value.shares
            assertEquals(128, shares.size)
            
            val splitTime = System.currentTimeMillis() - startTime
            
            // Test reconstruction with minimum shares
            val reconstructStartTime = System.currentTimeMillis()
            val reconstructResult = sss.reconstruct(shares.take(64))
            assertTrue(reconstructResult is SSSResult.Success)
            
            val reconstructTime = System.currentTimeMillis() - reconstructStartTime
            
            val reconstructed = (reconstructResult as SSSResult.Success).value
            assertArrayEquals(secret, reconstructed)
            
            println("Secret size: ${"%4d".format(size)} bytes | " +
                    "Split time: ${"%4d".format(splitTime)}ms | " +
                    "Reconstruct time: ${"%4d".format(reconstructTime)}ms")
        }
    }
    
    @Test
    fun `memory stress with many large secrets`() {
        val config = SSSConfig(5, 10)
        val secretSize = 1024
        val numSecrets = 100
        
        val runtime = Runtime.getRuntime()
        val beforeMemory = runtime.totalMemory() - runtime.freeMemory()
        
        val allShares = mutableListOf<List<cb.core.tools.sss.models.SecretShare>>()
        
        // Create many secrets and keep all shares in memory
        repeat(numSecrets) { i ->
            val secret = ByteArray(secretSize) { ((it + i) % 256).toByte() }
            val splitResult = sss.split(secret, config)
            assertTrue(splitResult is SSSResult.Success)
            allShares.add((splitResult as SSSResult.Success).value.shares)
        }
        
        val afterSplitMemory = runtime.totalMemory() - runtime.freeMemory()
        val memoryUsedMB = (afterSplitMemory - beforeMemory) / (1024.0 * 1024.0)
        
        println("\nMemory stress test: $numSecrets secrets of ${secretSize}B each")
        println("Memory used: ${String.format("%.2f", memoryUsedMB)} MB")
        println("Average per secret: ${String.format("%.2f", memoryUsedMB / numSecrets)} MB")
        
        // Verify we can still reconstruct
        allShares.take(10).forEachIndexed { index, shares ->
            val reconstructResult = sss.reconstruct(shares.take(config.threshold))
            assertTrue(reconstructResult is SSSResult.Success, "Failed to reconstruct secret $index")
        }
    }
    
    @Test
    fun `varying threshold stress test`() {
        val totalShares = 20
        val secret = "Threshold variation stress test with enough data".toByteArray()
        val thresholds = (2..totalShares).toList()
        
        println("\n=== Varying Threshold Stress Test ===")
        println("Total shares: $totalShares")
        
        for (threshold in thresholds) {
            val config = SSSConfig(threshold, totalShares)
            
            val splitResult = sss.split(secret, config)
            assertTrue(splitResult is SSSResult.Success)
            
            val shares = (splitResult as SSSResult.Success).value.shares
            
            // Test reconstruction with exactly threshold shares
            val reconstructResult = sss.reconstruct(shares.take(threshold))
            assertTrue(reconstructResult is SSSResult.Success)
            
            val reconstructed = (reconstructResult as SSSResult.Success).value
            assertArrayEquals(secret, reconstructed)
            
            if (threshold % 5 == 0 || threshold == totalShares) {
                println("Threshold: ${"%2d".format(threshold)} - Success")
            }
        }
    }
    
    @Test
    fun `share permutation stress test`() {
        val config = SSSConfig(5, 10)
        val secret = "Permutation stress test secret".toByteArray()
        val iterations = 100
        
        val splitResult = sss.split(secret, config)
        assertTrue(splitResult is SSSResult.Success)
        
        val shares = (splitResult as SSSResult.Success).value.shares
        var successCount = 0
        
        // Test many different permutations of shares
        repeat(iterations) { iteration ->
            // Create a random permutation of indices
            val indices = (0 until config.totalShares).toMutableList()
            indices.shuffle()
            
            // Take first k shares from the permutation
            val selectedShares = indices.take(config.threshold).map { shares[it] }
            
            val reconstructResult = sss.reconstruct(selectedShares)
            assertTrue(reconstructResult is SSSResult.Success)
            
            val reconstructed = (reconstructResult as SSSResult.Success).value
            assertArrayEquals(secret, reconstructed)
            successCount++
        }
        
        assertEquals(iterations, successCount)
        println("Share permutation test: All $iterations permutations succeeded")
    }
    
    @Test
    fun `repeated reconstruction stress test`() {
        val config = SSSConfig(3, 5)
        val secret = ByteArray(256) { (it % 256).toByte() }
        val iterations = 500
        
        val splitResult = sss.split(secret, config)
        assertTrue(splitResult is SSSResult.Success)
        
        val shares = (splitResult as SSSResult.Success).value.shares.take(config.threshold)
        
        // Repeatedly reconstruct from the same shares
        val startTime = System.currentTimeMillis()
        repeat(iterations) {
            val reconstructResult = sss.reconstruct(shares)
            assertTrue(reconstructResult is SSSResult.Success)
            
            val reconstructed = (reconstructResult as SSSResult.Success).value
            assertArrayEquals(secret, reconstructed)
        }
        
        val duration = System.currentTimeMillis() - startTime
        val reconstructsPerSecond = (iterations * 1000.0) / duration
        
        println("Repeated reconstruction: $iterations iterations in ${duration}ms " +
                "(${reconstructsPerSecond.toInt()} reconstructs/sec)")
    }
}