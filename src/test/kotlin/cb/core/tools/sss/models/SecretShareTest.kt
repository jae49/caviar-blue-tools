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

class SecretShareTest {

    private fun createTestMetadata(
        threshold: Int = 3,
        totalShares: Int = 5,
        secretSize: Int = 10
    ): ShareMetadata {
        return ShareMetadata(
            threshold = threshold,
            totalShares = totalShares,
            secretSize = secretSize,
            secretHash = ByteArray(32) { it.toByte() },
            shareSetId = "test-set-123"
        )
    }

    @Test
    fun `should create valid secret share`() {
        val metadata = createTestMetadata()
        val data = ByteArray(10) { it.toByte() }
        
        val share = SecretShare(
            index = 1,
            data = data,
            metadata = metadata
        )
        
        Assertions.assertEquals(1, share.index)
        Assertions.assertArrayEquals(data, share.data)
        Assertions.assertEquals(metadata, share.metadata)
    }

    @Test
    fun `should reject invalid index values`() {
        val metadata = createTestMetadata()
        val data = ByteArray(10)
        
        // Zero index
        assertThrows<IllegalArgumentException> {
            SecretShare(index = 0, data = data, metadata = metadata)
        }.also {
            Assertions.assertTrue(it.message!!.contains("Share index must be positive"))
        }
        
        // Negative index
        assertThrows<IllegalArgumentException> {
            SecretShare(index = -1, data = data, metadata = metadata)
        }.also {
            Assertions.assertTrue(it.message!!.contains("Share index must be positive"))
        }
        
        // Index exceeding maximum
        assertThrows<IllegalArgumentException> {
            SecretShare(index = 129, data = data, metadata = metadata)
        }.also {
            Assertions.assertTrue(it.message!!.contains("Share index (129) exceeds maximum allowed (128)"))
        }
    }

    @Test
    fun `should reject empty data`() {
        val metadata = createTestMetadata()
        
        assertThrows<IllegalArgumentException> {
            SecretShare(index = 1, data = ByteArray(0), metadata = metadata)
        }.also {
            Assertions.assertTrue(it.message!!.contains("Share data cannot be empty"))
        }
    }

    @Test
    fun `should reject data size mismatch`() {
        val metadata = createTestMetadata(secretSize = 10)
        
        assertThrows<IllegalArgumentException> {
            SecretShare(index = 1, data = ByteArray(5), metadata = metadata)
        }.also {
            Assertions.assertTrue(it.message!!.contains("Share data size (5) must match secret size (10)"))
        }
        
        assertThrows<IllegalArgumentException> {
            SecretShare(index = 1, data = ByteArray(15), metadata = metadata)
        }.also {
            Assertions.assertTrue(it.message!!.contains("Share data size (15) must match secret size (10)"))
        }
    }

    @Test
    fun `should validate share for configuration`() {
        val config = SSSConfig(threshold = 3, totalShares = 5, secretMaxSize = 100)
        val metadata = ShareMetadata(
            threshold = 3,
            totalShares = 5,
            secretSize = 10,
            secretHash = ByteArray(32)
        )
        val share = SecretShare(index = 3, data = ByteArray(10), metadata = metadata)
        
        Assertions.assertTrue(share.isValidFor(config))
        
        // Index out of range
        val share2 = SecretShare(index = 6, data = ByteArray(10), metadata = metadata)
        Assertions.assertFalse(share2.isValidFor(config))
        
        // Different threshold
        val metadata2 = metadata.copy(threshold = 4)
        val share3 = SecretShare(index = 3, data = ByteArray(10), metadata = metadata2)
        Assertions.assertFalse(share3.isValidFor(config))
        
        // Different total shares
        val metadata3 = metadata.copy(totalShares = 7)
        val share4 = SecretShare(index = 3, data = ByteArray(10), metadata = metadata3)
        Assertions.assertFalse(share4.isValidFor(config))
        
        // Secret size exceeds max
        val metadata4 = metadata.copy(secretSize = 200)
        val share5 = SecretShare(index = 3, data = ByteArray(200), metadata = metadata4)
        Assertions.assertFalse(share5.isValidFor(config))
    }

    @Test
    fun `should check compatibility between shares`() {
        val metadata = createTestMetadata()
        val share1 = SecretShare(index = 1, data = ByteArray(10), metadata = metadata)
        val share2 = SecretShare(index = 2, data = ByteArray(10), metadata = metadata)
        
        Assertions.assertTrue(share1.isCompatibleWith(share2))
        
        // Same index (not compatible for reconstruction)
        val share3 = SecretShare(index = 1, data = ByteArray(10), metadata = metadata)
        Assertions.assertFalse(share1.isCompatibleWith(share3))
        
        // Different metadata
        val metadata2 = createTestMetadata(threshold = 4)
        val share4 = SecretShare(index = 3, data = ByteArray(10), metadata = metadata2)
        Assertions.assertFalse(share1.isCompatibleWith(share4))
    }

    @Test
    fun `should serialize and deserialize to base64`() {
        val metadata = createTestMetadata()
        val data = ByteArray(10) { (it * 3).toByte() }
        val original = SecretShare(index = 7, data = data, metadata = metadata)
        
        val base64 = original.toBase64()
        Assertions.assertTrue(base64.startsWith("${SecretShare.SHARE_HEADER}_${SecretShare.SHARE_VERSION}_"))
        
        val deserialized = SecretShare.fromBase64(base64)
        
        Assertions.assertEquals(original.index, deserialized.index)
        Assertions.assertArrayEquals(original.data, deserialized.data)
        Assertions.assertEquals(original.metadata, deserialized.metadata)
    }

    @Test
    fun `should handle invalid base64 deserialization`() {
        // Invalid format
        assertThrows<IllegalArgumentException> {
            SecretShare.fromBase64("invalid-format")
        }.also {
            Assertions.assertTrue(it.message!!.contains("Invalid share format"))
        }
        
        // Wrong header
        assertThrows<IllegalArgumentException> {
            SecretShare.fromBase64("XXX_1.0_1_metadata_data")
        }.also {
            Assertions.assertTrue(it.message!!.contains("Invalid share format"))
        }
        
        // Wrong version
        assertThrows<IllegalArgumentException> {
            SecretShare.fromBase64("SSS_2.0_1_metadata_data")
        }.also {
            Assertions.assertTrue(it.message!!.contains("Unsupported share version: 2.0"))
        }
        
        // Invalid index
        assertThrows<IllegalArgumentException> {
            SecretShare.fromBase64("SSS_1.0_abc_metadata_data")
        }.also {
            Assertions.assertTrue(it.message!!.contains("Invalid share format"))
        }
    }

    @Test
    fun `should implement equals and hashCode correctly`() {
        val metadata = createTestMetadata()
        val data = ByteArray(10) { it.toByte() }
        
        val share1 = SecretShare(index = 1, data = data, metadata = metadata)
        val share2 = SecretShare(index = 1, data = data.clone(), metadata = metadata)
        val share3 = SecretShare(index = 2, data = data, metadata = metadata)
        
        Assertions.assertEquals(share1, share2)
        Assertions.assertEquals(share1.hashCode(), share2.hashCode())
        Assertions.assertNotEquals(share1, share3)
        
        // Different data
        val share4 = SecretShare(index = 1, data = ByteArray(10) { (it * 2).toByte() }, metadata = metadata)
        Assertions.assertNotEquals(share1, share4)
    }

    @Test
    fun `should provide meaningful toString`() {
        val metadata = createTestMetadata(secretSize = 100)
        val share = SecretShare(index = 5, data = ByteArray(100), metadata = metadata)
        
        val str = share.toString()
        Assertions.assertTrue(str.contains("index=5"))
        Assertions.assertTrue(str.contains("dataSize=100"))
        Assertions.assertTrue(str.contains("metadata="))
    }

    @Test
    fun `should handle maximum allowed index`() {
        val metadata = createTestMetadata()
        val data = ByteArray(10)
        
        // Maximum allowed index
        val maxShare = SecretShare(index = 128, data = data, metadata = metadata)
        Assertions.assertEquals(128, maxShare.index)
        
        // Just over maximum
        assertThrows<IllegalArgumentException> {
            SecretShare(index = 129, data = data, metadata = metadata)
        }
    }

    @Test
    fun `should correctly encode and decode share with special characters`() {
        val metadata = ShareMetadata(
            threshold = 2,
            totalShares = 3,
            secretSize = 20,
            secretHash = ByteArray(32) { it.toByte() },
            shareSetId = "test-123-!@#$%^&*()"
        )
        
        // Data with all possible byte values
        val data = ByteArray(20) { (it * 13 % 256).toByte() }
        
        val share = SecretShare(index = 1, data = data, metadata = metadata)
        val encoded = share.toBase64()
        val decoded = SecretShare.fromBase64(encoded)
        
        Assertions.assertEquals(share, decoded)
    }
}