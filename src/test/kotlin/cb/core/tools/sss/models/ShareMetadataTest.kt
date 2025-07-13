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

package cb.core.tools.sss.models

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Assertions
import java.time.Instant

class ShareMetadataTest {

    @Test
    fun `should create valid metadata`() {
        val secretHash = ByteArray(32) { it.toByte() }
        val metadata = ShareMetadata(
            threshold = 3,
            totalShares = 5,
            secretSize = 100,
            secretHash = secretHash
        )
        
        Assertions.assertEquals(3, metadata.threshold)
        Assertions.assertEquals(5, metadata.totalShares)
        Assertions.assertEquals(100, metadata.secretSize)
        Assertions.assertArrayEquals(secretHash, metadata.secretHash)
        Assertions.assertNotNull(metadata.timestamp)
        Assertions.assertNotNull(metadata.shareSetId)
        Assertions.assertTrue(metadata.shareSetId.isNotBlank())
    }

    @Test
    fun `should create metadata with custom values`() {
        val secretHash = ByteArray(32) { (it * 2).toByte() }
        val timestamp = Instant.now().minusSeconds(3600)
        val shareSetId = "custom-id-12345"
        
        val metadata = ShareMetadata(
            threshold = 10,
            totalShares = 20,
            secretSize = 1024,
            secretHash = secretHash,
            timestamp = timestamp,
            shareSetId = shareSetId
        )
        
        Assertions.assertEquals(10, metadata.threshold)
        Assertions.assertEquals(20, metadata.totalShares)
        Assertions.assertEquals(1024, metadata.secretSize)
        Assertions.assertArrayEquals(secretHash, metadata.secretHash)
        Assertions.assertEquals(timestamp, metadata.timestamp)
        Assertions.assertEquals(shareSetId, metadata.shareSetId)
    }

    @Test
    fun `should reject invalid threshold`() {
        val secretHash = ByteArray(32)
        
        assertThrows<IllegalArgumentException> {
            ShareMetadata(
                threshold = 0,
                totalShares = 5,
                secretSize = 100,
                secretHash = secretHash
            )
        }.also {
            Assertions.assertTrue(it.message!!.contains("Threshold must be positive"))
        }
    }

    @Test
    fun `should reject total shares less than threshold`() {
        val secretHash = ByteArray(32)
        
        assertThrows<IllegalArgumentException> {
            ShareMetadata(
                threshold = 5,
                totalShares = 3,
                secretSize = 100,
                secretHash = secretHash
            )
        }.also {
            Assertions.assertTrue(it.message!!.contains("Total shares (3) must be at least threshold (5)"))
        }
    }

    @Test
    fun `should reject invalid secret size`() {
        val secretHash = ByteArray(32)
        
        assertThrows<IllegalArgumentException> {
            ShareMetadata(
                threshold = 2,
                totalShares = 3,
                secretSize = 0,
                secretHash = secretHash
            )
        }.also {
            Assertions.assertTrue(it.message!!.contains("Secret size must be positive"))
        }
    }

    @Test
    fun `should reject invalid secret hash size`() {
        assertThrows<IllegalArgumentException> {
            ShareMetadata(
                threshold = 2,
                totalShares = 3,
                secretSize = 100,
                secretHash = ByteArray(16) // Should be 32 bytes for SHA-256
            )
        }.also {
            Assertions.assertTrue(it.message!!.contains("Secret hash must be SHA-256 (32 bytes)"))
        }
    }

    @Test
    fun `should reject blank share set ID`() {
        val secretHash = ByteArray(32)
        
        assertThrows<IllegalArgumentException> {
            ShareMetadata(
                threshold = 2,
                totalShares = 3,
                secretSize = 100,
                secretHash = secretHash,
                shareSetId = ""
            )
        }.also {
            Assertions.assertTrue(it.message!!.contains("Share set ID cannot be blank"))
        }
    }

    @Test
    fun `should check compatibility correctly`() {
        val secretHash = ByteArray(32) { it.toByte() }
        val shareSetId = "test-set-123"
        
        val metadata1 = ShareMetadata(
            threshold = 3,
            totalShares = 5,
            secretSize = 100,
            secretHash = secretHash,
            shareSetId = shareSetId
        )
        
        // Compatible metadata (same parameters)
        val metadata2 = ShareMetadata(
            threshold = 3,
            totalShares = 5,
            secretSize = 100,
            secretHash = secretHash.clone(),
            shareSetId = shareSetId
        )
        
        Assertions.assertTrue(metadata1.isCompatibleWith(metadata2))
        
        // Different threshold
        val metadata3 = metadata1.copy(threshold = 4)
        Assertions.assertFalse(metadata1.isCompatibleWith(metadata3))
        
        // Different total shares
        val metadata4 = metadata1.copy(totalShares = 6)
        Assertions.assertFalse(metadata1.isCompatibleWith(metadata4))
        
        // Different secret size
        val metadata5 = metadata1.copy(secretSize = 200)
        Assertions.assertFalse(metadata1.isCompatibleWith(metadata5))
        
        // Different secret hash
        val metadata6 = metadata1.copy(secretHash = ByteArray(32) { (it * 2).toByte() })
        Assertions.assertFalse(metadata1.isCompatibleWith(metadata6))
        
        // Different share set ID
        val metadata7 = metadata1.copy(shareSetId = "different-set")
        Assertions.assertFalse(metadata1.isCompatibleWith(metadata7))
    }

    @Test
    fun `should validate secret correctly`() {
        val secret = "test secret".toByteArray()
        val secretHash = ShareMetadata.computeSecretHash(secret)
        
        val metadata = ShareMetadata(
            threshold = 2,
            totalShares = 3,
            secretSize = secret.size,
            secretHash = secretHash
        )
        
        // Valid secret
        Assertions.assertTrue(metadata.validateSecret(secret))
        
        // Wrong size
        Assertions.assertFalse(metadata.validateSecret("short".toByteArray()))
        Assertions.assertFalse(metadata.validateSecret("this is a longer secret".toByteArray()))
        
        // Wrong content (same size)
        Assertions.assertFalse(metadata.validateSecret("wrong secret".toByteArray()))
    }

    @Test
    fun `should serialize and deserialize correctly`() {
        val secretHash = ByteArray(32) { it.toByte() }
        val timestamp = Instant.now()
        val shareSetId = "test-${System.currentTimeMillis()}"
        
        val original = ShareMetadata(
            threshold = 7,
            totalShares = 12,
            secretSize = 512,
            secretHash = secretHash,
            timestamp = timestamp,
            shareSetId = shareSetId
        )
        
        val base64 = original.toBase64()
        Assertions.assertNotNull(base64)
        Assertions.assertTrue(base64.isNotBlank())
        
        val deserialized = ShareMetadata.fromBase64(base64)
        
        Assertions.assertEquals(original.threshold, deserialized.threshold)
        Assertions.assertEquals(original.totalShares, deserialized.totalShares)
        Assertions.assertEquals(original.secretSize, deserialized.secretSize)
        Assertions.assertArrayEquals(original.secretHash, deserialized.secretHash)
        Assertions.assertEquals(original.timestamp.toEpochMilli(), deserialized.timestamp.toEpochMilli())
        Assertions.assertEquals(original.shareSetId, deserialized.shareSetId)
    }

    @Test
    fun `should handle invalid base64 deserialization`() {
        assertThrows<IllegalArgumentException> {
            ShareMetadata.fromBase64("invalid-base64!")
        }
        
        assertThrows<IllegalArgumentException> {
            ShareMetadata.fromBase64("dmFsaWQgYmFzZTY0IGJ1dCB3cm9uZyBmb3JtYXQ=") // valid base64 but wrong format
        }.also {
            Assertions.assertTrue(it.message!!.contains("Invalid metadata format"))
        }
    }

    @Test
    fun `should compute secret hash consistently`() {
        val secret = "test secret".toByteArray()
        
        val hash1 = ShareMetadata.computeSecretHash(secret)
        val hash2 = ShareMetadata.computeSecretHash(secret)
        
        Assertions.assertEquals(32, hash1.size)
        Assertions.assertEquals(32, hash2.size)
        Assertions.assertArrayEquals(hash1, hash2)
        
        // Different secret should produce different hash
        val hash3 = ShareMetadata.computeSecretHash("different secret".toByteArray())
        Assertions.assertFalse(hash1.contentEquals(hash3))
    }

    @Test
    fun `should generate unique share set IDs`() {
        val ids = mutableSetOf<String>()
        
        repeat(100) {
            val id = ShareMetadata.generateShareSetId()
            Assertions.assertTrue(id.isNotBlank())
            Assertions.assertTrue(id.matches(Regex("\\d+-\\d+")))
            ids.add(id)
        }
        
        // All IDs should be unique
        Assertions.assertEquals(100, ids.size)
    }

    @Test
    fun `should create metadata from secret and config`() {
        val secret = "my secret data".toByteArray()
        val config = SSSConfig(threshold = 3, totalShares = 5)
        
        val metadata = ShareMetadata.create(secret, config)
        
        Assertions.assertEquals(config.threshold, metadata.threshold)
        Assertions.assertEquals(config.totalShares, metadata.totalShares)
        Assertions.assertEquals(secret.size, metadata.secretSize)
        Assertions.assertArrayEquals(ShareMetadata.computeSecretHash(secret), metadata.secretHash)
        Assertions.assertTrue(metadata.validateSecret(secret))
    }

    @Test
    fun `should implement equals and hashCode correctly`() {
        val secretHash = ByteArray(32) { it.toByte() }
        val timestamp = Instant.now()
        val shareSetId = "test-set"
        
        val metadata1 = ShareMetadata(
            threshold = 3,
            totalShares = 5,
            secretSize = 100,
            secretHash = secretHash,
            timestamp = timestamp,
            shareSetId = shareSetId
        )
        
        val metadata2 = ShareMetadata(
            threshold = 3,
            totalShares = 5,
            secretSize = 100,
            secretHash = secretHash.clone(),
            timestamp = timestamp,
            shareSetId = shareSetId
        )
        
        val metadata3 = ShareMetadata(
            threshold = 4, // Different
            totalShares = 5,
            secretSize = 100,
            secretHash = secretHash,
            timestamp = timestamp,
            shareSetId = shareSetId
        )
        
        Assertions.assertEquals(metadata1, metadata2)
        Assertions.assertEquals(metadata1.hashCode(), metadata2.hashCode())
        Assertions.assertNotEquals(metadata1, metadata3)
        
        // Different timestamp should not affect equality
        val metadata4 = metadata1.copy(timestamp = timestamp.plusSeconds(60))
        Assertions.assertEquals(metadata1, metadata4)
    }
}