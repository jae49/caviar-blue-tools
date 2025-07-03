package cb.core.tools.erasure.math

import kotlin.system.measureTimeMillis
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Tag

@Tag("slow")
class MathBenchmark {
    
    @Test
    fun benchmarkGaloisFieldOperations() {
        val iterations = 1000000
        
        println("Galois Field Operations Benchmark")
        println("=================================")
        
        val addTime = measureTimeMillis {
            for (i in 0 until iterations) {
                val a = i % 256
                val b = (i * 7) % 256
                GaloisField.add(a, b)
            }
        }
        println("Addition: ${iterations} operations in ${addTime}ms (${iterations.toDouble() / addTime * 1000} ops/sec)")
        
        val multiplyTime = measureTimeMillis {
            for (i in 0 until iterations) {
                val a = (i % 255) + 1
                val b = ((i * 7) % 255) + 1
                GaloisField.multiply(a, b)
            }
        }
        println("Multiplication: ${iterations} operations in ${multiplyTime}ms (${iterations.toDouble() / multiplyTime * 1000} ops/sec)")
        
        val divideTime = measureTimeMillis {
            for (i in 0 until iterations) {
                val a = (i % 255) + 1
                val b = ((i * 7) % 255) + 1
                GaloisField.divide(a, b)
            }
        }
        println("Division: ${iterations} operations in ${divideTime}ms (${iterations.toDouble() / divideTime * 1000} ops/sec)")
        
        val inverseTime = measureTimeMillis {
            for (i in 0 until iterations) {
                val a = (i % 255) + 1
                GaloisField.inverse(a)
            }
        }
        println("Inverse: ${iterations} operations in ${inverseTime}ms (${iterations.toDouble() / inverseTime * 1000} ops/sec)")
    }
    
    @Test
    fun benchmarkPolynomialOperations() {
        println("\nPolynomial Operations Benchmark")
        println("===============================")
        
        val poly1 = IntArray(100) { (it * 3 + 7) % 256 }
        val poly2 = IntArray(50) { (it * 5 + 11) % 256 }
        val iterations = 10000
        
        val multiplyTime = measureTimeMillis {
            for (i in 0 until iterations) {
                GaloisField.multiplyPolynomial(poly1, poly2)
            }
        }
        println("Polynomial multiplication (100x50): ${iterations} operations in ${multiplyTime}ms (${iterations.toDouble() / multiplyTime * 1000} ops/sec)")
        
        val evaluateTime = measureTimeMillis {
            for (i in 0 until iterations) {
                val x = (i % 255) + 1
                GaloisField.evaluatePolynomial(poly1, x)
            }
        }
        println("Polynomial evaluation (degree 100): ${iterations} operations in ${evaluateTime}ms (${iterations.toDouble() / evaluateTime * 1000} ops/sec)")
        
        val divideTime = measureTimeMillis {
            for (i in 0 until iterations / 10) {
                GaloisField.dividePolynomial(poly1, poly2.take(10).toIntArray())
            }
        }
        println("Polynomial division (100÷10): ${iterations / 10} operations in ${divideTime}ms (${(iterations / 10).toDouble() / divideTime * 1000} ops/sec)")
    }
    
    @Test
    fun benchmarkReedSolomonOperations() {
        println("\nReed-Solomon Operations Benchmark")
        println("=================================")
        
        val dataSizes = listOf(1024, 4096, 16384, 65536)
        val parityShards = 4
        
        for (dataSize in dataSizes) {
            val data = IntArray(dataSize) { (it * 13 + 37) % 256 }
            val generator = PolynomialMath.generateGenerator(parityShards)
            val iterations = maxOf(1, 1000 / (dataSize / 1024))
            
            val encodeTime = measureTimeMillis {
                for (i in 0 until iterations) {
                    PolynomialMath.encode(data, generator)
                }
            }
            
            val throughputMBps = (dataSize.toLong() * iterations) / (encodeTime * 1024.0)
            println("Encode ${dataSize} bytes: ${iterations} operations in ${encodeTime}ms (${String.format("%.2f", throughputMBps)} MB/s)")
        }
        
        val dataShards = 8
        val testData = IntArray(dataShards) { (it * 17 + 23) % 256 }
        val generator = PolynomialMath.generateGenerator(parityShards)
        val parity = PolynomialMath.encode(testData, generator)
        
        val allShards = Array<IntArray?>(dataShards + parityShards) { null }
        for (i in testData.indices) {
            allShards[i] = intArrayOf(testData[i])
        }
        for (i in parity.indices) {
            allShards[dataShards + i] = intArrayOf(parity[i])
        }
        
        allShards[0] = null
        allShards[2] = null
        val erasures = intArrayOf(0, 2)
        val iterations = 10000
        
        val decodeTime = measureTimeMillis {
            for (i in 0 until iterations) {
                PolynomialMath.decode(allShards.copyOf(), erasures, dataShards, parityShards)
            }
        }
        println("Decode with 2 erasures: ${iterations} operations in ${decodeTime}ms (${iterations.toDouble() / decodeTime * 1000} ops/sec)")
    }
    
    @Test
    fun benchmarkMemoryUsage() {
        println("\nMemory Usage Analysis")
        println("====================")
        
        val runtime = Runtime.getRuntime()
        val initialMemory = runtime.totalMemory() - runtime.freeMemory()
        
        val largePoly1 = IntArray(10000) { it % 256 }
        val largePoly2 = IntArray(5000) { (it * 3) % 256 }
        
        val memoryAfterAllocation = runtime.totalMemory() - runtime.freeMemory()
        println("Memory used for large polynomials: ${(memoryAfterAllocation - initialMemory) / 1024} KB")
        
        val result = GaloisField.multiplyPolynomial(largePoly1, largePoly2)
        
        val memoryAfterOperation = runtime.totalMemory() - runtime.freeMemory()
        println("Memory used after multiplication: ${(memoryAfterOperation - initialMemory) / 1024} KB")
        println("Result polynomial size: ${result.size} coefficients")
        
        System.gc()
        Thread.sleep(100)
        
        val memoryAfterGC = runtime.totalMemory() - runtime.freeMemory()
        println("Memory after GC: ${(memoryAfterGC - initialMemory) / 1024} KB")
    }
    
    @Test
    fun benchmarkScalability() {
        println("\nScalability Analysis")
        println("===================")
        
        val shardCounts = listOf(4, 8, 16, 32)
        val parityRatios = listOf(0.25, 0.5, 1.0)
        
        for (dataShards in shardCounts) {
            for (ratio in parityRatios) {
                val parityShards = (dataShards * ratio).toInt().coerceAtLeast(1)
                val totalShards = dataShards + parityShards
                
                if (totalShards > 255) continue
                
                val data = IntArray(dataShards) { (it * 19 + 41) % 256 }
                val generator = PolynomialMath.generateGenerator(parityShards)
                val iterations = maxOf(1, 1000 / dataShards)
                
                val time = measureTimeMillis {
                    for (i in 0 until iterations) {
                        PolynomialMath.encode(data, generator)
                    }
                }
                
                val avgTime = time.toDouble() / iterations
                println("${dataShards}+${parityShards} shards: ${String.format("%.2f", avgTime)}ms per encode")
            }
        }
    }
}