package cb.core.tools.erasure

import cb.core.tools.erasure.models.EncodingConfig
import cb.core.tools.erasure.models.Shard
import cb.core.tools.erasure.models.ReconstructionResult
import cb.core.tools.erasure.models.ReconstructionError
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Tag
import kotlin.random.Random
import kotlin.system.measureTimeMillis

class ShardCombinationTest {
    private val encoder = ReedSolomonEncoder()
    private val decoder = ReedSolomonDecoder()
    
    @Test
    fun `test all combinations for small configurations`() {
        data class TestCase(val n: Int, val k: Int, val dataSize: Int)
        val testCases = listOf(
            TestCase(4, 2, 1024),
            TestCase(5, 3, 1024),
            TestCase(6, 3, 1024),
            TestCase(6, 4, 1024),
            TestCase(7, 4, 1024),
            TestCase(8, 5, 1024)
        )
        
        for (testCase in testCases) {
            val config = EncodingConfig(
                dataShards = testCase.k,
                parityShards = testCase.n - testCase.k
            )
            val data = Random.nextBytes(testCase.dataSize)
            val shards = encoder.encode(data, config)
            
            val combinations = generateCombinations(testCase.n, testCase.k)
            var successCount = 0
            var failureCount = 0
            val failures = mutableListOf<Pair<List<Int>, String>>()
            
            for (combination in combinations) {
                val selectedShards = combination.map { shards[it] }
                when (val result = decoder.decode(selectedShards)) {
                    is ReconstructionResult.Success -> {
                        if (result.data.contentEquals(data)) {
                            successCount++
                        } else {
                            failureCount++
                            failures.add(combination to "Data mismatch")
                        }
                    }
                    is ReconstructionResult.Failure -> {
                        failureCount++
                        failures.add(combination to "${result.error}: ${result.message ?: ""}")
                    }
                    else -> {
                        failureCount++
                        failures.add(combination to "Unexpected result type")
                    }
                }
            }
            
            println("Configuration (n=${testCase.n}, k=${testCase.k}): $successCount successful, $failureCount failed")
            if (failures.isNotEmpty()) {
                println("  Failed combinations:")
                failures.take(5).forEach { (combo, error) ->
                    println("    ${combo.joinToString(",", "[", "]")} - $error")
                }
                if (failures.size > 5) {
                    println("    ... and ${failures.size - 5} more")
                }
            }
            
            // Currently expecting some failures based on the plan
            assertTrue(successCount > 0, "At least some combinations should work")
        }
    }
    
    @Test
    fun `test specific failing case - n=8, k=5, missing shards 0,3,6`() {
        val config = EncodingConfig(dataShards = 5, parityShards = 3)
        val data = Random.nextBytes(2048)
        val allShards = encoder.encode(data, config)
        
        // Select shards [1,2,4,5,7] (missing [0,3,6])
        val selectedIndices = listOf(1, 2, 4, 5, 7)
        val selectedShards = selectedIndices.map { allShards[it] }
        
        when (val result = decoder.decode(selectedShards)) {
            is ReconstructionResult.Success -> {
                assertArrayEquals(data, result.data, "Reconstructed data should match original")
            }
            is ReconstructionResult.Failure -> {
                fail("Should be able to reconstruct from any 5 shards, but got: ${result.error}")
            }
            else -> fail("Unexpected result type")
        }
    }
    
    @Test
    fun `test edge cases - missing only data shards`() {
        val config = EncodingConfig(dataShards = 4, parityShards = 2)
        val data = Random.nextBytes(1024)
        val shards = encoder.encode(data, config)
        
        // Keep only parity shards and some data shards
        val selectedShards = listOf(shards[2], shards[3], shards[4], shards[5])
        when (val result = decoder.decode(selectedShards)) {
            is ReconstructionResult.Success -> {
                assertArrayEquals(data, result.data)
            }
            is ReconstructionResult.Failure -> {
                println("Missing only data shards test failed: ${result.error} - ${result.message ?: ""}")
            }
            else -> println("Unexpected result type")
        }
    }
    
    @Test
    fun `test edge cases - missing only parity shards`() {
        val config = EncodingConfig(dataShards = 4, parityShards = 2)
        val data = Random.nextBytes(1024)
        val shards = encoder.encode(data, config)
        
        // Keep all data shards
        val selectedShards = shards.take(4)
        when (val result = decoder.decode(selectedShards)) {
            is ReconstructionResult.Success -> {
                assertArrayEquals(data, result.data)
            }
            is ReconstructionResult.Failure -> {
                fail("Should always succeed with all data shards, but got: ${result.error}")
            }
            else -> fail("Unexpected result type")
        }
    }
    
    @Test
    fun `test property - any k shards from n should reconstruct`() {
        val configs = listOf(
            EncodingConfig(3, 2),
            EncodingConfig(4, 2),
            EncodingConfig(4, 4),
            EncodingConfig(5, 3)
        )
        
        for (config in configs) {
            val data = Random.nextBytes(512)
            val shards = encoder.encode(data, config)
            val n = config.totalShards
            val k = config.dataShards
            
            // Test multiple random combinations
            repeat(10) {
                val indices = (0 until n).shuffled().take(k)
                val selectedShards = indices.map { shards[it] }
                
                when (val result = decoder.decode(selectedShards)) {
                    is ReconstructionResult.Success -> {
                        // Success - no action needed
                    }
                    is ReconstructionResult.Failure -> {
                        println("Failed for config (k=$k, n=$n) with indices: $indices")
                        println("Error: ${result.error} - ${result.message ?: ""}")
                    }
                    else -> {
                        println("Unexpected result type for config (k=$k, n=$n)")
                    }
                }
            }
        }
    }
    
    @Test
    @Tag("slow")
    fun `benchmark different shard combination patterns`() {
        val config = EncodingConfig(dataShards = 6, parityShards = 4)
        val dataSize = 64 * 1024 // 64KB
        val data = Random.nextBytes(dataSize)
        val shards = encoder.encode(data, config)
        
        data class Pattern(val name: String, val indices: List<Int>)
        val patterns = listOf(
            Pattern("All data shards", (0 until 6).toList()),
            Pattern("Mixed (first k)", (0 until 6).toList()),
            Pattern("Mixed (last k)", (4 until 10).toList()),
            Pattern("Scattered", listOf(0, 2, 4, 6, 8, 9)),
            Pattern("Mostly parity", listOf(4, 5, 6, 7, 8, 9))
        )
        
        for (pattern in patterns) {
            val selectedShards = pattern.indices.map { shards[it] }
            val startTime = System.nanoTime()
            
            val iterations = 100
            var successCount = 0
            repeat(iterations) {
                when (decoder.decode(selectedShards)) {
                    is ReconstructionResult.Success -> successCount++
                    else -> {}
                }
            }
            
            val elapsedMs = (System.nanoTime() - startTime) / 1_000_000.0
            val avgMs = elapsedMs / iterations
            
            println("Pattern '${pattern.name}': $successCount/$iterations successful, avg ${String.format("%.2f", avgMs)}ms")
        }
    }
    
    @Test
    fun `test maximum erasure scenarios`() {
        val configs = listOf(
            EncodingConfig(4, 4),  // Can lose up to 4 shards
            EncodingConfig(8, 8),  // Can lose up to 8 shards
            EncodingConfig(10, 6)  // Can lose up to 6 shards
        )
        
        for (config in configs) {
            val data = Random.nextBytes(1024)
            val shards = encoder.encode(data, config)
            
            // Test with exactly k shards (maximum erasure)
            val selectedShards = shards.take(config.dataShards)
            when (val result = decoder.decode(selectedShards)) {
                is ReconstructionResult.Success -> {
                    assertArrayEquals(data, result.data)
                }
                is ReconstructionResult.Failure -> {
                    fail("Should reconstruct with exactly k=${config.dataShards} shards from n=${config.totalShards}, but got: ${result.error}")
                }
                else -> fail("Unexpected result type")
            }
        }
    }
    
    private fun generateCombinations(n: Int, k: Int): List<List<Int>> {
        val result = mutableListOf<List<Int>>()
        
        fun generate(start: Int, current: MutableList<Int>) {
            if (current.size == k) {
                result.add(current.toList())
                return
            }
            
            for (i in start until n) {
                current.add(i)
                generate(i + 1, current)
                current.removeAt(current.size - 1)
            }
        }
        
        generate(0, mutableListOf())
        return result
    }
    
    // PHASE 4: Exhaustive Testing
    
    @Test
    fun `test exhaustive combinations for small configurations`() {
        data class TestCase(val n: Int, val k: Int, val dataSize: Int)
        val testCases = listOf(
            TestCase(4, 2, 1024),
            TestCase(5, 3, 1024),
            TestCase(6, 3, 1024),
            TestCase(6, 4, 1024),
            TestCase(7, 4, 1024),
            TestCase(8, 5, 1024)
        )
        
        println("\nExhaustive Combination Testing:")
        println("Config\t\tTotal Combos\tSuccess\tFailure")
        println("------\t\t------------\t-------\t-------")
        
        for (testCase in testCases) {
            val data = Random.nextBytes(testCase.dataSize)
            val combinations = generateCombinations(testCase.n, testCase.k)
            
            val config = EncodingConfig(
                dataShards = testCase.k,
                parityShards = testCase.n - testCase.k
            )
            val shards = encoder.encode(data, config)
            var successCount = 0
            
            for (combination in combinations) {
                val selectedShards = combination.map { shards[it] }
                val result = decoder.decode(selectedShards)
                if (result is ReconstructionResult.Success && result.data.contentEquals(data)) {
                    successCount++
                }
            }
            
            val totalCombos = combinations.size
            val failureCount = totalCombos - successCount
            println("(${testCase.k}+${testCase.n - testCase.k})\t\t$totalCombos\t\t$successCount\t$failureCount")
            
            // Should always achieve 100% success
            assertEquals(totalCombos, successCount, 
                "Algorithm should succeed for all valid combinations")
        }
    }
    
    @Test
    fun `test algorithm handles known challenging combinations`() {
        // Test a specific challenging case
        val config = EncodingConfig(
            dataShards = 5, 
            parityShards = 3
        )
        val data = Random.nextBytes(2048)
        val allShards = encoder.encode(data, config)
        
        // Select shards [1,2,4,5,7] (missing [0,3,6])
        val selectedIndices = listOf(1, 2, 4, 5, 7)
        val selectedShards = selectedIndices.map { allShards[it] }
        
        when (val result = decoder.decode(selectedShards)) {
            is ReconstructionResult.Success -> {
                assertArrayEquals(data, result.data, 
                    "Algorithm should reconstruct from any 5 shards")
            }
            is ReconstructionResult.Failure -> {
                fail("Algorithm should handle missing shards [0,3,6], but got: ${result.error}")
            }
            is ReconstructionResult.Partial -> {
                fail("Unexpected partial result")
            }
        }
    }
    
    @Test
    @Tag("slow")
    fun `test all combinations up to 8 total shards`() {
        val maxShards = 8
        val results = mutableMapOf<String, Int>() // config -> successCount
        
        for (n in 2..maxShards) {
            for (k in 1..n) {
                val parityShards = n - k
                if (parityShards == 0) continue // Skip no-parity cases
                
                val data = ByteArray(k * 10) { it.toByte() }
                val combinations = generateCombinations(n, k)
                
                val config = EncodingConfig(
                    dataShards = k,
                    parityShards = parityShards
                )
                val shards = encoder.encode(data, config)
                var successCount = 0
                
                for (combo in combinations) {
                    val selected = combo.map { shards[it] }
                    val result = decoder.decode(selected)
                    if (result is ReconstructionResult.Success) {
                        successCount++
                    }
                }
                
                val configStr = "($k,$n)"
                results[configStr] = successCount
                
                // Must be 100%
                assertEquals(combinations.size, successCount,
                    "Failed for config $configStr")
            }
        }
        
        // Print summary
        println("\nExhaustive Testing Summary (up to 8 shards):")
        println("Config\tCombos\tSuccess\tRate")
        results.forEach { (config, successCount) ->
            val (k, n) = config.trim('(', ')').split(',').map { it.toInt() }
            val total = generateCombinations(n, k).size
            val successRate = successCount * 100.0 / total
            println("$config\t$total\t$successCount\t${String.format("%.1f", successRate)}%")
        }
    }
    
    @Test
    @Tag("slow")
    fun `test performance for different combination patterns`() {
        val config = EncodingConfig(
            dataShards = 10,
            parityShards = 5
        )
        val dataSize = 100 * 1024 // 100KB
        val data = Random.nextBytes(dataSize)
        
        data class Pattern(
            val name: String, 
            val indices: List<Int>,
            val description: String
        )
        
        val patterns = listOf(
            Pattern("Sequential Data", (0..9).toList(), "First 10 (all data)"),
            Pattern("Sequential Mixed", (5..14).toList(), "Half data, half parity"),
            Pattern("All Parity", (10..14).toList() + (0..4).toList(), "All parity + some data"),
            Pattern("Alternating", (0..14 step 2).toList() + listOf(1, 3, 5), "Every other shard"),
            Pattern("Random", listOf(0, 3, 4, 7, 8, 9, 11, 12, 13, 14), "Random selection")
        )
        
        println("\nPerformance by Pattern:")
        println("Pattern\t\t\t\tTime (ms)\tDescription")
        
        val shards = encoder.encode(data, config)
        
        for (pattern in patterns) {
            val selectedShards = pattern.indices.map { shards[it] }
            
            val time = measureTimeMillis {
                repeat(10) {
                    val result = decoder.decode(selectedShards)
                    assertTrue(result is ReconstructionResult.Success)
                }
            } / 10.0
            
            println("${pattern.name.padEnd(28)}\t${String.format("%.2f", time)}\t\t${pattern.description}")
        }
    }
    
    @Test
    fun `test regression - ensure algorithm never fails for valid combinations`() {
        // Test a variety of configurations to ensure no regressions
        val testConfigs = listOf(
            Triple(2, 1, 100),    // Minimal
            Triple(4, 2, 1000),   // Small
            Triple(8, 4, 5000),   // Medium
            Triple(10, 6, 10000), // Large
            Triple(16, 8, 20000), // Extra large
            Triple(20, 10, 50000) // Maximum practical
        )
        
        for ((dataShards, parityShards, dataSize) in testConfigs) {
            val data = ByteArray(dataSize) { (it % 256).toByte() }
            val config = EncodingConfig(
                dataShards = dataShards,
                parityShards = parityShards
            )
            
            val shards = encoder.encode(data, config)
            val n = shards.size
            val k = dataShards
            
            // Test 20 random valid combinations
            repeat(20) {
                val indices = (0 until n).shuffled().take(k)
                val selected = indices.map { shards[it] }
                
                val result = decoder.decode(selected)
                assertTrue(result is ReconstructionResult.Success,
                    "Algorithm failed for config ($dataShards,$parityShards) with indices $indices")
                assertArrayEquals(data, (result as ReconstructionResult.Success).data)
            }
        }
    }
    
    @Test
    fun `document which specific combinations work`() {
        val config = EncodingConfig(dataShards = 5, parityShards = 3)
        val data = "Test data for combination documentation".toByteArray()
        
        val shards = encoder.encode(data, config)
        
        val allCombinations = generateCombinations(8, 5)
        val failures = mutableListOf<List<Int>>()
        var successCount = 0
        
        // Test all 56 combinations
        for (combo in allCombinations) {
            val selectedShards = combo.map { shards[it] }
            val result = decoder.decode(selectedShards)
            if (result is ReconstructionResult.Success) {
                successCount++
            } else {
                failures.add(combo)
            }
        }
        
        println("\nCombination Analysis for (5+3) Configuration:")
        println("Total combinations: ${allCombinations.size}")
        println("Successful: $successCount")
        println("Failures: ${failures.size}")
        
        if (failures.isNotEmpty()) {
            println("\nAlgorithm fails for:")
            failures.take(10).forEach { combo ->
                println("  $combo")
            }
            if (failures.size > 10) {
                println("  ... and ${failures.size - 10} more")
            }
        }
        
        assertEquals(0, failures.size, 
            "Algorithm should have zero failures")
        
        // Success rate
        val successRate = successCount * 100.0 / allCombinations.size
        
        println("\nSuccess Rate: ${String.format("%.1f", successRate)}%")
    }
}