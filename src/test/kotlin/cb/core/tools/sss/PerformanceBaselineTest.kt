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

package cb.core.tools.sss

import cb.core.tools.sss.models.SSSConfig
import cb.core.tools.sss.models.SSSResult
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.system.measureNanoTime
import org.junit.jupiter.api.Assertions.*

@Tag("slow")
class PerformanceBaselineTest {
    
    private val sss = ShamirSecretSharing()
    
    @Test
    fun `measure split performance for various secret sizes`() {
        val config = SSSConfig(3, 5)
        val sizes = listOf(1, 10, 100, 500, 1024)
        val iterations = 100
        
        println("\n=== Split Performance by Secret Size ===")
        println("Config: k=${config.threshold}, n=${config.totalShares}")
        
        for (size in sizes) {
            val secret = ByteArray(size) { (it % 256).toByte() }
            
            // Warmup
            repeat(10) {
                sss.split(secret, config)
            }
            
            // Measure
            val times = mutableListOf<Long>()
            repeat(iterations) {
                val time = measureNanoTime {
                    val result = sss.split(secret, config)
                    assertTrue(result is SSSResult.Success)
                }
                times.add(time)
            }
            
            val avgTime = times.average()
            val minTime = times.minOrNull() ?: 0
            val maxTime = times.maxOrNull() ?: 0
            val throughput = (size * 1_000_000_000.0) / avgTime / (1024 * 1024) // MB/s
            
            println("Size: ${"%4d".format(size)} bytes | Avg: ${"%6.2f".format(avgTime/1000)} μs | " +
                    "Min: ${"%6.2f".format(minTime/1000.0)} μs | Max: ${"%6.2f".format(maxTime/1000.0)} μs | " +
                    "Throughput: ${"%6.2f".format(throughput)} MB/s")
        }
    }
    
    @Test
    fun `measure reconstruct performance for various secret sizes`() {
        val config = SSSConfig(3, 5)
        val sizes = listOf(1, 10, 100, 500, 1024)
        val iterations = 100
        
        println("\n=== Reconstruct Performance by Secret Size ===")
        println("Config: k=${config.threshold}, n=${config.totalShares}")
        
        for (size in sizes) {
            val secret = ByteArray(size) { (it % 256).toByte() }
            val splitResult = sss.split(secret, config)
            assertTrue(splitResult is SSSResult.Success)
            val shares = (splitResult as SSSResult.Success).value.shares.take(config.threshold)
            
            // Warmup
            repeat(10) {
                sss.reconstruct(shares)
            }
            
            // Measure
            val times = mutableListOf<Long>()
            repeat(iterations) {
                val time = measureNanoTime {
                    val result = sss.reconstruct(shares)
                    assertTrue(result is SSSResult.Success)
                }
                times.add(time)
            }
            
            val avgTime = times.average()
            val minTime = times.minOrNull() ?: 0
            val maxTime = times.maxOrNull() ?: 0
            val throughput = (size * 1_000_000_000.0) / avgTime / (1024 * 1024) // MB/s
            
            println("Size: ${"%4d".format(size)} bytes | Avg: ${"%6.2f".format(avgTime/1000)} μs | " +
                    "Min: ${"%6.2f".format(minTime/1000.0)} μs | Max: ${"%6.2f".format(maxTime/1000.0)} μs | " +
                    "Throughput: ${"%6.2f".format(throughput)} MB/s")
        }
    }
    
    @Test
    fun `measure performance for various k n combinations`() {
        val secretSize = 100
        val secret = ByteArray(secretSize) { (it % 256).toByte() }
        val iterations = 50
        
        val configs = listOf(
            SSSConfig(2, 3),
            SSSConfig(3, 5),
            SSSConfig(5, 10),
            SSSConfig(10, 20),
            SSSConfig(20, 40),
            SSSConfig(50, 100),
            SSSConfig(64, 128)
        )
        
        println("\n=== Performance by Configuration (k/n) ===")
        println("Secret size: $secretSize bytes")
        
        for (config in configs) {
            // Warmup
            repeat(5) {
                val splitResult = sss.split(secret, config)
                if (splitResult is SSSResult.Success) {
                    val shares = (splitResult as SSSResult.Success).value.shares.take(config.threshold)
                    sss.reconstruct(shares)
                }
            }
            
            // Measure split
            val splitTimes = mutableListOf<Long>()
            repeat(iterations) {
                val time = measureNanoTime {
                    val result = sss.split(secret, config)
                    assertTrue(result is SSSResult.Success)
                }
                splitTimes.add(time)
            }
            
            // Measure reconstruct
            val splitResult = sss.split(secret, config)
            assertTrue(splitResult is SSSResult.Success)
            val shares = (splitResult as SSSResult.Success).value.shares.take(config.threshold)
            
            val reconstructTimes = mutableListOf<Long>()
            repeat(iterations) {
                val time = measureNanoTime {
                    val result = sss.reconstruct(shares)
                    assertTrue(result is SSSResult.Success)
                }
                reconstructTimes.add(time)
            }
            
            val avgSplitTime = splitTimes.average()
            val avgReconstructTime = reconstructTimes.average()
            
            println("Config: k=${"%3d".format(config.threshold)}, n=${"%3d".format(config.totalShares)} | " +
                    "Split: ${"%7.2f".format(avgSplitTime/1000)} μs | " +
                    "Reconstruct: ${"%7.2f".format(avgReconstructTime/1000)} μs")
        }
    }
    
    @Test
    fun `measure memory allocation during operations`() {
        val config = SSSConfig(5, 10)
        val sizes = listOf(100, 500, 1024)
        
        println("\n=== Memory Usage Analysis ===")
        println("Config: k=${config.threshold}, n=${config.totalShares}")
        
        for (size in sizes) {
            val secret = ByteArray(size) { (it % 256).toByte() }
            
            // Force GC before measurement
            System.gc()
            Thread.sleep(100)
            
            val runtime = Runtime.getRuntime()
            val beforeMemory = runtime.totalMemory() - runtime.freeMemory()
            
            // Perform operations
            val splitResult = sss.split(secret, config)
            assertTrue(splitResult is SSSResult.Success)
            val shares = (splitResult as SSSResult.Success).value.shares
            
            val afterSplitMemory = runtime.totalMemory() - runtime.freeMemory()
            
            val reconstructResult = sss.reconstruct(shares.take(config.threshold))
            assertTrue(reconstructResult is SSSResult.Success)
            
            val afterReconstructMemory = runtime.totalMemory() - runtime.freeMemory()
            
            val splitMemoryUsed = (afterSplitMemory - beforeMemory) / 1024
            val reconstructMemoryUsed = (afterReconstructMemory - afterSplitMemory) / 1024
            
            println("Secret size: ${"%4d".format(size)} bytes | " +
                    "Split memory: ~${"%4d".format(splitMemoryUsed)} KB | " +
                    "Reconstruct memory: ~${"%4d".format(reconstructMemoryUsed)} KB")
        }
    }
    
    @Test
    fun `establish throughput baseline for target performance`() {
        val targetThroughputMBps = 1.0 // 1 MB/s as per requirements
        val testSizes = listOf(1024, 10240, 102400) // 1KB, 10KB, 100KB
        val config = SSSConfig(3, 5)
        val iterations = 20
        
        println("\n=== Throughput vs Target (${targetThroughputMBps} MB/s) ===")
        
        for (size in testSizes) {
            val secret = ByteArray(size) { (it % 256).toByte() }
            
            // Warmup
            repeat(5) {
                sss.split(secret, config)
            }
            
            val times = mutableListOf<Long>()
            repeat(iterations) {
                val time = measureNanoTime {
                    val splitResult = sss.split(secret, config)
                    assertTrue(splitResult is SSSResult.Success)
                    val shares = (splitResult as SSSResult.Success).value.shares
                    val reconstructResult = sss.reconstruct(shares.take(config.threshold))
                    assertTrue(reconstructResult is SSSResult.Success)
                }
                times.add(time)
            }
            
            val avgTime = times.average()
            val throughputMBps = (size * 1_000_000_000.0) / avgTime / (1024 * 1024)
            val percentOfTarget = (throughputMBps / targetThroughputMBps) * 100
            
            println("Size: ${"%6d".format(size)} bytes | " +
                    "Throughput: ${"%8.2f".format(throughputMBps)} MB/s | " +
                    "Target: ${"%6.1f".format(percentOfTarget)}% | " +
                    if (throughputMBps >= targetThroughputMBps) "✓ PASS" else "✗ FAIL")
            
            assertTrue(throughputMBps >= targetThroughputMBps, 
                "Throughput ${throughputMBps} MB/s is below target ${targetThroughputMBps} MB/s")
        }
    }
    
    @Test
    fun `measure polynomial evaluation performance`() {
        val coefficientCounts = listOf(2, 5, 10, 20, 50, 100)
        val iterations = 1000
        
        println("\n=== Polynomial Evaluation Performance ===")
        
        for (coeffCount in coefficientCounts) {
            val coefficients = ByteArray(coeffCount) { (it * 31 % 256).toByte() }
            val x = 42.toByte()
            
            // Direct polynomial evaluation timing
            val times = mutableListOf<Long>()
            repeat(iterations) {
                val time = measureNanoTime {
                    // Simulate polynomial evaluation
                    var result = 0
                    var xPower = 1
                    for (coeff in coefficients) {
                        result = result xor (coeff.toInt() and 0xFF) * xPower
                        xPower = (xPower * (x.toInt() and 0xFF)) and 0xFF
                    }
                }
                times.add(time)
            }
            
            val avgTime = times.average()
            val opsPerSecond = 1_000_000_000.0 / avgTime
            
            println("Degree: ${"%3d".format(coeffCount-1)} | " +
                    "Avg time: ${"%6.0f".format(avgTime)} ns | " +
                    "Ops/sec: ${"%,.0f".format(opsPerSecond)}")
        }
    }
}