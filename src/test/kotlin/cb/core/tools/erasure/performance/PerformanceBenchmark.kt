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

import cb.core.tools.erasure.ReedSolomonEncoder
import cb.core.tools.erasure.ReedSolomonDecoder
import cb.core.tools.erasure.models.EncodingConfig
import cb.core.tools.erasure.models.ReconstructionResult
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Assertions.*
import kotlin.random.Random
import kotlin.system.measureNanoTime
import kotlin.math.roundToInt

@Tag("slow")
class PerformanceBenchmark {
    
    data class BenchmarkResult(
        val dataSize: Int,
        val dataShards: Int,
        val parityShards: Int,
        val encodingTimeMs: Double,
        val decodingTimeMs: Double,
        val encodingThroughputMBps: Double,
        val decodingThroughputMBps: Double
    )
    
    @Test
    fun benchmarkVariousDataSizes() {
        val encoder = ReedSolomonEncoder()
        val decoder = ReedSolomonDecoder()
        
        val dataSizes = listOf(
            1024,           // 1 KB
            16 * 1024,      // 16 KB
            256 * 1024,     // 256 KB
            1024 * 1024     // 1 MB
        )
        
        val configurations = listOf(
            EncodingConfig(dataShards = 4, parityShards = 2),
            EncodingConfig(dataShards = 8, parityShards = 4)
        )
        
        println("\n=== Reed-Solomon Performance Benchmark ===\n")
        
        for (config in configurations) {
            println("Configuration: ${config.dataShards} data shards, ${config.parityShards} parity shards")
            println("-".repeat(80))
            
            for (dataSize in dataSizes) {
                val result = runBenchmark(encoder, decoder, dataSize, config)
                printResult(result)
            }
            println()
        }
    }
    
    @Test
    fun benchmarkErasureRecoveryPerformance() {
        val encoder = ReedSolomonEncoder()
        val decoder = ReedSolomonDecoder()
        val config = EncodingConfig(dataShards = 10, parityShards = 6)
        val dataSize = 1024 * 1024 // 1 MB
        
        println("\n=== Erasure Recovery Performance ===")
        println("Testing with 1MB data, 10 data shards, 6 parity shards")
        println("-".repeat(80))
        
        // Test different erasure patterns
        val erasureCounts = listOf(1, 2, 3, 4, 5, 6)
        
        for (erasureCount in erasureCounts) {
            val results = mutableListOf<Double>()
            
            repeat(3) { // Run 3 iterations for averaging
                val data = ByteArray(dataSize) { Random.nextBytes(1)[0] }
                val shards = encoder.encode(data, config)
                
                // Simulate erasures by removing random shards
                val availableShards = shards.shuffled(Random).drop(erasureCount)
                
                val decodingTime = measureNanoTime {
                    val result = decoder.decode(availableShards)
                    assertTrue(result is ReconstructionResult.Success)
                }
                
                results.add(decodingTime / 1_000_000.0) // Convert to ms
            }
            
            val avgTime = results.average()
            val throughput = (dataSize / (1024.0 * 1024.0)) / (avgTime / 1000.0)
            
            println("$erasureCount erasures: avg ${String.format("%.2f", avgTime)}ms, " +
                    "${String.format("%.2f", throughput)} MB/s")
        }
    }
    
    @Test
    fun benchmarkParallelProcessing() {
        val encoder = ReedSolomonEncoder()
        val config = EncodingConfig(dataShards = 8, parityShards = 4)
        val dataSize = 4 * 1024 * 1024 // 4 MB
        
        println("\n=== Parallel Processing Benchmark ===")
        println("Testing 4MB data with 8 data shards, 4 parity shards")
        println("-".repeat(80))
        
        // Sequential processing
        val data = ByteArray(dataSize) { Random.nextBytes(1)[0] }
        
        val sequentialTime = measureNanoTime {
            encoder.encode(data, config)
        } / 1_000_000.0
        
        println("Sequential encoding: ${String.format("%.2f", sequentialTime)}ms")
        println("Throughput: ${String.format("%.2f", (dataSize / (1024.0 * 1024.0)) / (sequentialTime / 1000.0))} MB/s")
        
        // Note: Actual parallel implementation would be added in the optimization phase
        println("\nNote: Parallel processing optimization to be implemented")
    }
    
    private fun runBenchmark(
        encoder: ReedSolomonEncoder,
        decoder: ReedSolomonDecoder,
        dataSize: Int,
        config: EncodingConfig
    ): BenchmarkResult {
        val data = ByteArray(dataSize) { Random.nextBytes(1)[0] }
        
        // Warm up
        encoder.encode(data, config)
        
        // Measure encoding
        val encodingTimes = mutableListOf<Long>()
        repeat(3) {
            val time = measureNanoTime {
                encoder.encode(data, config)
            }
            encodingTimes.add(time)
        }
        
        val avgEncodingTime = encodingTimes.average() / 1_000_000.0 // ms
        val dataSizeMB = dataSize / (1024.0 * 1024.0)
        
        // Measure decoding (with simulated erasures)
        val shards = encoder.encode(data, config)
        // Take minimum required shards plus one (to simulate loss of some shards)
        val shardsToKeep = minOf(config.dataShards + 1, config.totalShards)
        val availableShards = shards.shuffled(Random).take(shardsToKeep)
        
        val decodingTimes = mutableListOf<Long>()
        var decodingSuccessful = true
        
        repeat(3) {
            val time = measureNanoTime {
                val result = decoder.decode(availableShards)
                if (result !is ReconstructionResult.Success) {
                    decodingSuccessful = false
                }
            }
            decodingTimes.add(time)
        }
        
        // If decoding failed, skip this configuration
        if (!decodingSuccessful) {
            return BenchmarkResult(
                dataSize = dataSize,
                dataShards = config.dataShards,
                parityShards = config.parityShards,
                encodingTimeMs = avgEncodingTime,
                decodingTimeMs = -1.0,
                encodingThroughputMBps = dataSizeMB / (avgEncodingTime / 1000.0),
                decodingThroughputMBps = -1.0
            )
        }
        
        val avgDecodingTime = decodingTimes.average() / 1_000_000.0 // ms
        
        val encodingThroughput = dataSizeMB / (avgEncodingTime / 1000.0)
        val decodingThroughput = dataSizeMB / (avgDecodingTime / 1000.0)
        
        return BenchmarkResult(
            dataSize = dataSize,
            dataShards = config.dataShards,
            parityShards = config.parityShards,
            encodingTimeMs = avgEncodingTime,
            decodingTimeMs = avgDecodingTime,
            encodingThroughputMBps = encodingThroughput,
            decodingThroughputMBps = decodingThroughput
        )
    }
    
    private fun printResult(result: BenchmarkResult) {
        val dataSizeStr = when {
            result.dataSize < 1024 -> "${result.dataSize} B"
            result.dataSize < 1024 * 1024 -> "${result.dataSize / 1024} KB"
            else -> "${result.dataSize / (1024 * 1024)} MB"
        }
        
        if (result.decodingTimeMs < 0) {
            println(String.format(
                "%-8s | Encode: %6.2f ms (%6.2f MB/s) | Decode: FAILED",
                dataSizeStr,
                result.encodingTimeMs,
                result.encodingThroughputMBps
            ))
        } else {
            println(String.format(
                "%-8s | Encode: %6.2f ms (%6.2f MB/s) | Decode: %6.2f ms (%6.2f MB/s)",
                dataSizeStr,
                result.encodingTimeMs,
                result.encodingThroughputMBps,
                result.decodingTimeMs,
                result.decodingThroughputMBps
            ))
        }
    }
    
    @Test
    fun benchmarkMemoryUsage() {
        println("\n=== Memory Usage Analysis ===")
        println("-".repeat(80))
        
        val config = EncodingConfig(dataShards = 10, parityShards = 6)
        val dataSizes = listOf(1024 * 1024, 4 * 1024 * 1024, 16 * 1024 * 1024) // 1MB, 4MB, 16MB
        
        for (dataSize in dataSizes) {
            val runtime = Runtime.getRuntime()
            runtime.gc() // Force GC before measurement
            
            val beforeMemory = runtime.totalMemory() - runtime.freeMemory()
            
            val encoder = ReedSolomonEncoder()
            val data = ByteArray(dataSize) { Random.nextBytes(1)[0] }
            val shards = encoder.encode(data, config)
            
            val afterMemory = runtime.totalMemory() - runtime.freeMemory()
            val memoryUsed = (afterMemory - beforeMemory) / (1024.0 * 1024.0)
            
            val theoreticalMemory = (dataSize * (config.totalShards + 1)) / (1024.0 * 1024.0)
            
            println(String.format(
                "%d MB data: Used %.2f MB memory (theoretical: %.2f MB, overhead: %.1f%%)",
                dataSize / (1024 * 1024),
                memoryUsed,
                theoreticalMemory,
                ((memoryUsed / theoreticalMemory) - 1) * 100
            ))
        }
    }
}