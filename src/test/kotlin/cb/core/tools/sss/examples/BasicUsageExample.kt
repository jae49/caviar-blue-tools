package cb.core.tools.sss.examples

import cb.core.tools.sss.ShamirSecretSharing
import cb.core.tools.sss.models.*

/**
 * Basic usage examples for Shamir Secret Sharing.
 * 
 * These examples demonstrate common use cases and patterns for
 * splitting and reconstructing secrets.
 */
object BasicUsageExample {
    
    /**
     * Example 1: Simple string secret sharing
     */
    fun simpleStringSharing() {
        println("=== Example 1: Simple String Sharing ===")
        
        // Create SSS instance
        val sss = ShamirSecretSharing()
        
        // Define configuration: 3 of 5 shares needed
        val config = SSSConfig(threshold = 3, totalShares = 5)
        
        // The secret to protect
        val secret = "My super secret password!"
        
        // Split the secret
        val splitResult = sss.split(secret, config)
        
        when (splitResult) {
            is SSSResult.Success -> {
                val shares = splitResult.value.shares
                val metadata = splitResult.value.metadata
                
                println("Secret split successfully!")
                println("Share set ID: ${metadata.shareSetId}")
                println("Created ${shares.size} shares, need ${metadata.threshold} to reconstruct")
                
                // Print share information (in practice, distribute these securely)
                shares.forEach { share ->
                    println("Share ${share.index}: ${share.toBase64().take(20)}...")
                }
                
                // Demonstrate reconstruction with minimum shares
                println("\nReconstructing with shares 1, 3, and 5...")
                val selectedShares = shares.filter { it.index in listOf(1, 3, 5) }
                
                val reconstructResult = sss.reconstructString(selectedShares)
                when (reconstructResult) {
                    is SSSResult.Success -> {
                        println("Reconstructed secret: ${reconstructResult.value}")
                        println("Success! Secret matches: ${reconstructResult.value == secret}")
                    }
                    is SSSResult.Failure -> {
                        println("Reconstruction failed: ${reconstructResult.message}")
                    }
                    else -> println("Unexpected result")
                }
            }
            is SSSResult.Failure -> {
                println("Failed to split secret: ${splitResult.message}")
            }
            else -> println("Unexpected result")
        }
    }
    
    /**
     * Example 2: Binary data (encryption key) sharing
     */
    fun encryptionKeySharing() {
        println("\n=== Example 2: Encryption Key Sharing ===")
        
        val sss = ShamirSecretSharing()
        
        // Simulate a 256-bit AES encryption key
        val encryptionKey = ByteArray(32) { i -> (i * 17 + 13).toByte() }
        println("Original key (hex): ${encryptionKey.toHexString()}")
        
        // Use 2 of 3 configuration for key escrow
        val config = SSSConfig(threshold = 2, totalShares = 3)
        
        val splitResult = sss.split(encryptionKey, config)
        
        when (splitResult) {
            is SSSResult.Success -> {
                val shares = splitResult.value.shares
                
                // Simulate storing shares with different parties
                val adminShare = shares[0]
                val securityShare = shares[1]
                val backupShare = shares[2]
                
                println("Key split into 3 shares, any 2 can reconstruct")
                
                // Simulate key recovery scenario
                println("\nSimulating key recovery with admin and backup shares...")
                val recoveryShares = listOf(adminShare, backupShare)
                
                val recoveredKey = sss.reconstruct(recoveryShares).getOrNull()
                if (recoveredKey != null) {
                    println("Recovered key (hex): ${recoveredKey.toHexString()}")
                    println("Keys match: ${encryptionKey.contentEquals(recoveredKey)}")
                } else {
                    println("Key recovery failed!")
                }
            }
            is SSSResult.Failure -> {
                println("Failed to split key: ${splitResult.message}")
            }
            else -> println("Unexpected result")
        }
    }
    
    /**
     * Example 3: Share validation and error handling
     */
    fun shareValidationExample() {
        println("\n=== Example 3: Share Validation ===")
        
        val sss = ShamirSecretSharing()
        val config = SSSConfig(threshold = 3, totalShares = 5)
        
        // Create two different secrets
        val secret1 = "First secret"
        val secret2 = "Second secret"
        
        val shares1 = sss.split(secret1, config).getOrThrow().shares
        val shares2 = sss.split(secret2, config).getOrThrow().shares
        
        // Test 1: Insufficient shares
        println("Test 1: Trying to reconstruct with only 2 shares (need 3)...")
        val insufficientShares = shares1.take(2)
        val result1 = sss.reconstructString(insufficientShares)
        
        when (result1) {
            is SSSResult.Failure -> {
                println("Expected failure: ${result1.message}")
                println("Error type: ${result1.error}")
            }
            else -> println("Unexpected success!")
        }
        
        // Test 2: Mixed shares from different secrets
        println("\nTest 2: Trying to mix shares from different secrets...")
        val mixedShares = listOf(shares1[0], shares1[1], shares2[2])
        val result2 = sss.reconstructString(mixedShares)
        
        when (result2) {
            is SSSResult.Failure -> {
                println("Expected failure: ${result2.message}")
                println("Error type: ${result2.error}")
            }
            else -> println("Unexpected success!")
        }
        
        // Test 3: Validate shares before reconstruction
        println("\nTest 3: Validating shares before reconstruction...")
        val validationResult = sss.validateShares(shares1.take(3))
        
        when (validationResult) {
            is SSSResult.Success -> {
                println("Shares validated successfully!")
                val reconstructed = sss.reconstructString(shares1.take(3)).getOrThrow()
                println("Reconstructed: $reconstructed")
            }
            is SSSResult.Failure -> {
                println("Validation failed: ${validationResult.message}")
            }
            else -> println("Unexpected result")
        }
    }
    
    /**
     * Example 4: Working with share metadata
     */
    fun metadataExample() {
        println("\n=== Example 4: Working with Metadata ===")
        
        val sss = ShamirSecretSharing()
        val config = SSSConfig(threshold = 2, totalShares = 4)
        val secret = "Metadata example secret"
        
        val splitResult = sss.split(secret, config).getOrThrow()
        val shares = splitResult.shares
        val metadata = splitResult.metadata
        
        println("Share metadata:")
        println("  Share set ID: ${metadata.shareSetId}")
        println("  Threshold: ${metadata.threshold}")
        println("  Total shares: ${metadata.totalShares}")
        println("  Secret size: ${metadata.secretSize} bytes")
        println("  Created at: ${metadata.timestamp}")
        
        // Use metadata for validation during reconstruction
        val selectedShares = shares.take(2)
        
        println("\nReconstructing with metadata validation...")
        val result = sss.reconstruct(selectedShares, metadata)
        
        when (result) {
            is SSSResult.Success -> {
                val reconstructed = String(result.value)
                println("Reconstruction successful: $reconstructed")
                
                // The metadata ensures the reconstructed secret matches the original
                if (metadata.validateSecret(result.value)) {
                    println("Metadata validation passed!")
                }
            }
            is SSSResult.Failure -> {
                println("Reconstruction failed: ${result.message}")
            }
            else -> println("Unexpected result")
        }
    }
    
    /**
     * Example 5: Share serialization and storage
     */
    fun serializationExample() {
        println("\n=== Example 5: Share Serialization ===")
        
        val sss = ShamirSecretSharing()
        val config = SSSConfig(threshold = 2, totalShares = 3)
        val secret = "Serialization test"
        
        val shares = sss.split(secret, config).getOrThrow().shares
        
        // Serialize shares to Base64 strings
        val serializedShares = shares.map { share ->
            val serialized = share.toBase64()
            println("Share ${share.index} serialized (${serialized.length} chars)")
            serialized
        }
        
        // Simulate storage and retrieval
        println("\nSimulating storage and retrieval...")
        
        // Later, deserialize shares
        val deserializedShares = serializedShares.mapNotNull { serialized ->
            try {
                val share = SecretShare.fromBase64(serialized)
                println("Deserialized share ${share.index}")
                share
            } catch (e: Exception) {
                println("Failed to deserialize: ${e.message}")
                null
            }
        }
        
        // Reconstruct from deserialized shares
        val reconstructed = sss.reconstructString(deserializedShares.take(2))
        
        when (reconstructed) {
            is SSSResult.Success -> {
                println("Reconstructed from deserialized shares: ${reconstructed.value}")
            }
            is SSSResult.Failure -> {
                println("Reconstruction failed: ${reconstructed.message}")
            }
            else -> println("Unexpected result")
        }
    }
    
    /**
     * Example 6: Different threshold configurations
     */
    fun thresholdConfigurationExamples() {
        println("\n=== Example 6: Threshold Configurations ===")
        
        val sss = ShamirSecretSharing()
        val secret = "Configuration example"
        
        // High security: Almost all shares needed
        println("High Security (4 of 5):")
        val highSecurity = SSSConfig(threshold = 4, totalShares = 5)
        val result1 = sss.split(secret, highSecurity)
        println("  Redundancy: ${highSecurity.redundancy} share(s) can be lost")
        
        // Balanced: Majority needed
        println("\nBalanced (3 of 5):")
        val balanced = SSSConfig(threshold = 3, totalShares = 5)
        val result2 = sss.split(secret, balanced)
        println("  Redundancy: ${balanced.redundancy} share(s) can be lost")
        
        // High availability: Few shares needed
        println("\nHigh Availability (2 of 5):")
        val highAvailability = SSSConfig(threshold = 2, totalShares = 5)
        val result3 = sss.split(secret, highAvailability)
        println("  Redundancy: ${highAvailability.redundancy} share(s) can be lost")
        
        // Special cases
        println("\nSpecial Configurations:")
        
        // All shares required
        val allRequired = SSSConfig.createAllRequired(5)
        println("  All required (5 of 5): No redundancy, maximum security")
        
        // Any single share works (not recommended)
        val anySingle = SSSConfig(threshold = 1, totalShares = 5)
        println("  Any single (1 of 5): Maximum redundancy, minimal security")
        println("  Is trivial: ${anySingle.isTrivial}")
    }
    
    // Utility function to convert ByteArray to hex string
    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Run all examples
     */
    @JvmStatic
    fun main(args: Array<String>) {
        simpleStringSharing()
        encryptionKeySharing()
        shareValidationExample()
        metadataExample()
        serializationExample()
        thresholdConfigurationExamples()
    }
}