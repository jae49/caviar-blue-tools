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

package cb.core.tools.sss.examples

import cb.core.tools.sss.ShamirSecretSharing
import cb.core.tools.sss.models.*
import cb.core.tools.sss.security.SecureMemory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.concurrent.thread

/**
 * Advanced usage examples for Shamir Secret Sharing.
 * 
 * These examples demonstrate complex scenarios including:
 * - Multi-party key management
 * - Secure file sharing
 * - Time-based share recovery
 * - Hierarchical secret sharing
 * - Performance optimization patterns
 */
object AdvancedUsageExample {
    
    /**
     * Example 1: Multi-party key management system
     */
    fun multiPartyKeyManagement() {
        println("=== Example 1: Multi-Party Key Management ===")
        
        val sss = ShamirSecretSharing()
        
        // Simulate a master encryption key for a secure system
        val masterKey = generateAESKey()
        println("Generated master key for secure system")
        
        // Define trustees with different roles
        data class Trustee(
            val id: String,
            val role: String,
            val email: String,
            var share: SecretShare? = null
        )
        
        val trustees = listOf(
            Trustee("alice", "Security Officer", "alice@company.com"),
            Trustee("bob", "CTO", "bob@company.com"),
            Trustee("carol", "Compliance Officer", "carol@company.com"),
            Trustee("dave", "External Auditor", "dave@audit.com"),
            Trustee("eve", "Backup Administrator", "eve@company.com")
        )
        
        // Require 3 of 5 trustees to reconstruct
        val config = SSSConfig(threshold = 3, totalShares = trustees.size)
        
        // Split the master key
        val splitResult = sss.split(masterKey.encoded, config).getOrThrow()
        
        // Distribute shares to trustees
        trustees.zip(splitResult.shares).forEach { (trustee, share) ->
            trustee.share = share
            println("Distributed share ${share.index} to ${trustee.role} (${trustee.id})")
        }
        
        // Simulate key recovery scenario
        println("\nSimulating emergency key recovery...")
        println("Available trustees: Alice (Security), Bob (CTO), Dave (Auditor)")
        
        val availableTrustees = trustees.filter { it.id in listOf("alice", "bob", "dave") }
        val availableShares = availableTrustees.mapNotNull { it.share }
        
        println("Attempting reconstruction with ${availableShares.size} shares...")
        
        val reconstructedKeyBytes = sss.reconstruct(availableShares).getOrNull()
        if (reconstructedKeyBytes != null) {
            val reconstructedKey = SecretKeySpec(reconstructedKeyBytes, "AES")
            println("Master key successfully reconstructed!")
            println("Keys match: ${masterKey.encoded.contentEquals(reconstructedKey.encoded)}")
            
            // Clear sensitive data
            SecureMemory.clear(reconstructedKeyBytes)
        } else {
            println("Failed to reconstruct master key!")
        }
    }
    
    /**
     * Example 2: Secure file sharing with share distribution
     */
    fun secureFileSharing() {
        println("\n=== Example 2: Secure File Sharing ===")
        
        val sss = ShamirSecretSharing()
        
        // Simulate a sensitive document
        val sensitiveDocument = """
            CONFIDENTIAL DOCUMENT
            Project Code: TITAN
            Authorization Level: TOP SECRET
            
            This document contains sensitive information about the Titan project.
            Access is restricted to authorized personnel only.
            
            [Additional sensitive content here...]
        """.trimIndent().toByteArray()
        
        println("Document size: ${sensitiveDocument.size} bytes")
        
        // Since SSS has size limits, we'll use it to protect an encryption key
        val documentKey = generateAESKey()
        val encryptedDocument = encryptData(sensitiveDocument, documentKey)
        println("Document encrypted, size: ${encryptedDocument.size} bytes")
        
        // Split the encryption key
        val config = SSSConfig(threshold = 2, totalShares = 4)
        val splitResult = sss.split(documentKey.encoded, config).getOrThrow()
        
        // Create a secure package
        class SecurePackage(
            val packageId: String = UUID.randomUUID().toString(),
            val encryptedData: ByteArray,
            val shareLocations: MutableMap<Int, String> = mutableMapOf(),
            val metadata: ShareMetadata,
            val checksum: String
        )
        
        val documentChecksum = Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(sensitiveDocument)
        )
        
        val securePackage = SecurePackage(
            encryptedData = encryptedDocument,
            metadata = splitResult.metadata,
            checksum = documentChecksum
        )
        
        // Distribute shares to different storage locations
        val storageLocations = listOf(
            "cloud://aws-s3/bucket1/shares/",
            "cloud://azure/container2/shares/",
            "local://secure-nas/shares/",
            "backup://offsite/vault/shares/"
        )
        
        splitResult.shares.zip(storageLocations).forEach { (share, location) ->
            val shareFile = "${location}${securePackage.packageId}_share_${share.index}.enc"
            securePackage.shareLocations[share.index] = shareFile
            println("Stored share ${share.index} at: $shareFile")
        }
        
        // Simulate document recovery
        println("\nSimulating document recovery...")
        println("Retrieving shares from locations 1 and 3...")
        
        // In real implementation, these would be retrieved from actual storage
        val retrievedShares = listOf(
            splitResult.shares[0], // From AWS S3
            splitResult.shares[2]  // From NAS
        )
        
        val reconstructedKeyBytes = sss.reconstruct(retrievedShares).getOrNull()
        if (reconstructedKeyBytes != null) {
            val reconstructedKey = SecretKeySpec(reconstructedKeyBytes, "AES")
            val decryptedDocument = decryptData(encryptedDocument, reconstructedKey)
            
            // Verify checksum
            val recoveredChecksum = Base64.getEncoder().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(decryptedDocument)
            )
            
            if (recoveredChecksum == documentChecksum) {
                println("Document successfully recovered and verified!")
                println("First 50 chars: ${String(decryptedDocument).take(50)}...")
            } else {
                println("Checksum mismatch! Document may be corrupted.")
            }
            
            // Clear sensitive data
            SecureMemory.clear(reconstructedKeyBytes)
            SecureMemory.clear(decryptedDocument)
        }
    }
    
    /**
     * Example 3: Time-based share recovery with expiration
     */
    fun timeBasedShareRecovery() {
        println("\n=== Example 3: Time-Based Share Recovery ===")
        
        val sss = ShamirSecretSharing()
        
        // Time-locked share wrapper
        class TimeLockShare(
            val share: SecretShare,
            val validFrom: Instant,
            val validUntil: Instant,
            val purposeCode: String
        ) {
            fun isValid(now: Instant = Instant.now()): Boolean {
                return now.isAfter(validFrom) && now.isBefore(validUntil)
            }
            
            fun getShare(now: Instant = Instant.now()): SecretShare? {
                return if (isValid(now)) share else null
            }
        }
        
        val secret = "Time-sensitive secret data"
        val config = SSSConfig(threshold = 3, totalShares = 5)
        val splitResult = sss.split(secret, config).getOrThrow()
        
        val now = Instant.now()
        
        // Create time-locked shares with different validity periods
        val timeLockShares = listOf(
            TimeLockShare(
                splitResult.shares[0],
                now,
                now.plus(Duration.ofHours(24)),
                "IMMEDIATE"
            ),
            TimeLockShare(
                splitResult.shares[1],
                now,
                now.plus(Duration.ofHours(24)),
                "IMMEDIATE"
            ),
            TimeLockShare(
                splitResult.shares[2],
                now.plus(Duration.ofHours(12)),
                now.plus(Duration.ofHours(36)),
                "DELAYED"
            ),
            TimeLockShare(
                splitResult.shares[3],
                now,
                now.plus(Duration.ofDays(7)),
                "WEEKLY"
            ),
            TimeLockShare(
                splitResult.shares[4],
                now,
                now.plus(Duration.ofDays(30)),
                "MONTHLY_BACKUP"
            )
        )
        
        // Simulate recovery at different times
        println("Current time: $now")
        
        // Immediate recovery
        println("\nImmediate recovery attempt:")
        val immediateShares = timeLockShares.mapNotNull { it.getShare(now) }
        println("Available shares: ${immediateShares.size}")
        
        val immediateResult = sss.reconstructString(immediateShares)
        when (immediateResult) {
            is SSSResult.Success -> println("Recovered: ${immediateResult.value}")
            is SSSResult.Failure -> println("Recovery failed: ${immediateResult.message}")
            else -> println("Unexpected result")
        }
        
        // Recovery after 13 hours
        val after13Hours = now.plus(Duration.ofHours(13))
        println("\nRecovery attempt after 13 hours:")
        val delayedShares = timeLockShares.mapNotNull { it.getShare(after13Hours) }
        println("Available shares: ${delayedShares.size}")
        
        if (delayedShares.size >= config.threshold) {
            println("Sufficient shares available for recovery")
        }
        
        // Show share availability timeline
        println("\nShare availability timeline:")
        timeLockShares.forEachIndexed { index, timeShare ->
            println("Share ${index + 1} (${timeShare.purposeCode}):")
            println("  Valid from: ${timeShare.validFrom}")
            println("  Valid until: ${timeShare.validUntil}")
            println("  Duration: ${Duration.between(timeShare.validFrom, timeShare.validUntil)}")
        }
    }
    
    /**
     * Example 4: Hierarchical secret sharing
     */
    fun hierarchicalSecretSharing() {
        println("\n=== Example 4: Hierarchical Secret Sharing ===")
        
        val sss = ShamirSecretSharing()
        
        // Master secret
        val masterSecret = "Top level organizational secret"
        
        // Level 1: Split among departments
        println("Level 1: Splitting among 3 departments (need 2)")
        val level1Config = SSSConfig(threshold = 2, totalShares = 3)
        val level1Result = sss.split(masterSecret, level1Config).getOrThrow()
        
        val departments = listOf("Engineering", "Finance", "Legal")
        val departmentShares = level1Result.shares.zip(departments).toMap()
        
        // Level 2: Each department splits their share
        println("\nLevel 2: Each department splits their share among team members")
        
        val departmentSubShares = mutableMapOf<String, List<SecretShare>>()
        
        departmentShares.forEach { (share, dept) ->
            println("\n$dept department (share ${share.index}):")
            val level2Config = SSSConfig(threshold = 2, totalShares = 3)
            val level2Result = sss.split(share.toBase64().toByteArray(), level2Config).getOrThrow()
            
            departmentSubShares[dept] = level2Result.shares
            println("  Split into 3 sub-shares (need 2)")
            
            val teamMembers = when (dept) {
                "Engineering" -> listOf("Tech Lead", "Senior Dev", "DevOps")
                "Finance" -> listOf("CFO", "Controller", "Treasurer")
                "Legal" -> listOf("General Counsel", "Senior Counsel", "Compliance")
                else -> listOf()
            }
            
            level2Result.shares.zip(teamMembers).forEach { (subShare, member) ->
                println("  - Sub-share ${subShare.index} -> $member")
            }
        }
        
        // Simulate hierarchical recovery
        println("\n\nSimulating hierarchical recovery:")
        println("Need shares from 2 departments, with 2 team members each")
        
        // Select Engineering and Legal departments
        val selectedDepts = listOf("Engineering", "Legal")
        
        val reconstructedDeptShares = selectedDepts.mapNotNull { dept ->
            println("\nRecovering $dept department share:")
            val subShares = departmentSubShares[dept] ?: return@mapNotNull null
            
            // Select 2 team members
            val selectedSubShares = when (dept) {
                "Engineering" -> {
                    println("  Using: Tech Lead + DevOps")
                    listOf(subShares[0], subShares[2])
                }
                "Legal" -> {
                    println("  Using: General Counsel + Compliance")
                    listOf(subShares[0], subShares[2])
                }
                else -> emptyList()
            }
            
            // Reconstruct department share
            val deptShareData = sss.reconstruct(selectedSubShares).getOrNull()
            if (deptShareData != null) {
                println("  Department share recovered!")
                SecretShare.fromBase64(String(deptShareData))
            } else {
                println("  Failed to recover department share")
                null
            }
        }
        
        // Final reconstruction
        println("\nFinal reconstruction with department shares:")
        val finalResult = sss.reconstructString(reconstructedDeptShares)
        
        when (finalResult) {
            is SSSResult.Success -> {
                println("Master secret recovered: ${finalResult.value}")
                println("Success: ${finalResult.value == masterSecret}")
            }
            is SSSResult.Failure -> {
                println("Failed to recover master secret: ${finalResult.message}")
            }
            else -> println("Unexpected result")
        }
    }
    
    /**
     * Example 5: Performance optimization patterns
     */
    fun performanceOptimizationPatterns() {
        println("\n=== Example 5: Performance Optimization ===")
        
        val sss = ShamirSecretSharing()
        
        // Pattern 1: Batch processing
        println("Pattern 1: Batch Processing")
        val secrets = (1..100).map { "Secret_$it" }
        val config = SSSConfig(threshold = 3, totalShares = 5)
        
        val startBatch = Instant.now()
        val batchResults = secrets.map { secret ->
            sss.split(secret, config)
        }
        val batchDuration = Duration.between(startBatch, Instant.now())
        
        val successCount = batchResults.count { it.isSuccess() }
        println("  Processed ${secrets.size} secrets in ${batchDuration.toMillis()}ms")
        println("  Success rate: $successCount/${secrets.size}")
        println("  Average time: ${batchDuration.toMillis() / secrets.size}ms per secret")
        
        // Pattern 2: Concurrent processing
        println("\nPattern 2: Concurrent Processing")
        val concurrentSecrets = (1..20).map { "ConcurrentSecret_$it" }
        val results = ConcurrentHashMap<Int, SSSResult<ShamirSecretSharing.SplitResult>>()
        val processed = AtomicInteger(0)
        
        val startConcurrent = Instant.now()
        val threads = concurrentSecrets.mapIndexed { index, secret ->
            thread {
                val result = sss.split(secret, config)
                results[index] = result
                processed.incrementAndGet()
            }
        }
        
        threads.forEach { it.join() }
        val concurrentDuration = Duration.between(startConcurrent, Instant.now())
        
        println("  Processed ${concurrentSecrets.size} secrets concurrently in ${concurrentDuration.toMillis()}ms")
        println("  All successful: ${results.values.all { it.isSuccess() }}")
        
        // Pattern 3: Share caching for repeated access
        println("\nPattern 3: Share Caching")
        
        class ShareCache {
            private val cache = mutableMapOf<String, List<SecretShare>>()
            
            fun getOrCompute(key: String, compute: () -> List<SecretShare>): List<SecretShare> {
                return cache.getOrPut(key) { compute() }
            }
            
            fun invalidate(key: String) {
                cache.remove(key)
            }
            
            fun size() = cache.size
        }
        
        val cache = ShareCache()
        val cacheKey = "cached_secret"
        
        // First access - computes shares
        val start1 = Instant.now()
        val shares1 = cache.getOrCompute(cacheKey) {
            sss.split("Cached secret data", config).getOrThrow().shares
        }
        val duration1 = Duration.between(start1, Instant.now())
        println("  First access (computed): ${duration1.toNanos()}ns")
        
        // Second access - from cache
        val start2 = Instant.now()
        val shares2 = cache.getOrCompute(cacheKey) {
            sss.split("Cached secret data", config).getOrThrow().shares
        }
        val duration2 = Duration.between(start2, Instant.now())
        println("  Second access (cached): ${duration2.toNanos()}ns")
        println("  Speedup: ${duration1.toNanos() / duration2.toNanos().coerceAtLeast(1)}x")
        
        // Pattern 4: Memory-efficient large data handling
        println("\nPattern 4: Memory-Efficient Large Data")
        
        // For large data, split a key instead of the data itself
        val largeDataKey = generateAESKey()
        val keyShares = sss.split(largeDataKey.encoded, config).getOrThrow().shares
        
        println("  Large data approach:")
        println("  - Original data: Can be any size")
        println("  - Encryption key: ${largeDataKey.encoded.size} bytes")
        println("  - Each share size: ${keyShares[0].data.size} bytes")
        println("  - Total overhead: ${keyShares.sumOf { it.data.size }} bytes")
    }
    
    /**
     * Example 6: Custom share distribution strategy
     */
    fun customShareDistribution() {
        println("\n=== Example 6: Custom Share Distribution ===")
        
        val sss = ShamirSecretSharing()
        
        // Geographic distribution strategy
        data class ShareLocation(
            val region: String,
            val datacenter: String,
            val redundancy: Int
        )
        
        val locations = listOf(
            ShareLocation("US-EAST", "dc-virginia", 2),
            ShareLocation("US-WEST", "dc-oregon", 2),
            ShareLocation("EU-WEST", "dc-ireland", 2),
            ShareLocation("ASIA-PACIFIC", "dc-singapore", 1),
            ShareLocation("SOUTH-AMERICA", "dc-brazil", 1)
        )
        
        val secret = "Globally distributed secret"
        val config = SSSConfig(threshold = 3, totalShares = locations.size)
        val splitResult = sss.split(secret, config).getOrThrow()
        
        // Distribute shares according to strategy
        class DistributedShare(
            val share: SecretShare,
            val location: ShareLocation,
            val replicas: List<String>
        )
        
        val distributedShares = splitResult.shares.zip(locations).map { (share, location) ->
            val replicas = (1..location.redundancy).map { replicaNum ->
                "${location.datacenter}-replica-$replicaNum"
            }
            
            println("Share ${share.index} -> ${location.region} (${location.datacenter})")
            replicas.forEach { replica ->
                println("  - Replica: $replica")
            }
            
            DistributedShare(share, location, replicas)
        }
        
        // Simulate regional failure
        println("\nSimulating regional failure scenario:")
        println("EU-WEST datacenter is offline")
        
        val availableShares = distributedShares
            .filter { it.location.region != "EU-WEST" }
            .map { it.share }
        
        println("Available shares: ${availableShares.size} from regions:")
        availableShares.forEach { share ->
            val location = distributedShares.find { it.share == share }?.location
            println("  - Share ${share.index} from ${location?.region}")
        }
        
        // Attempt recovery
        val recoveryResult = sss.reconstructString(availableShares.take(3))
        when (recoveryResult) {
            is SSSResult.Success -> {
                println("\nRecovery successful despite regional failure!")
                println("Recovered: ${recoveryResult.value}")
            }
            is SSSResult.Failure -> {
                println("\nRecovery failed: ${recoveryResult.message}")
            }
            else -> println("Unexpected result")
        }
        
        // Calculate availability metrics
        println("\nAvailability Analysis:")
        val totalDatacenters = locations.size
        val requiredDatacenters = config.threshold
        val tolerableFailures = totalDatacenters - requiredDatacenters
        
        println("  Total datacenters: $totalDatacenters")
        println("  Required for recovery: $requiredDatacenters")
        println("  Can tolerate failures: $tolerableFailures datacenters")
        println("  Availability: ${(tolerableFailures.toDouble() / totalDatacenters * 100).toInt()}% of datacenters can fail")
    }
    
    // Utility functions
    private fun generateAESKey(): SecretKey {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        return keyGen.generateKey()
    }
    
    private fun encryptData(data: ByteArray, key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(data)
        
        // Prepend IV to ciphertext
        return iv + ciphertext
    }
    
    private fun decryptData(encryptedData: ByteArray, key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        
        // Extract IV (first 12 bytes for GCM)
        val iv = encryptedData.sliceArray(0..11)
        val ciphertext = encryptedData.sliceArray(12 until encryptedData.size)
        
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        
        return cipher.doFinal(ciphertext)
    }
    
    /**
     * Run all examples
     */
    @JvmStatic
    fun main(args: Array<String>) {
        multiPartyKeyManagement()
        secureFileSharing()
        timeBasedShareRecovery()
        hierarchicalSecretSharing()
        performanceOptimizationPatterns()
        customShareDistribution()
    }
}