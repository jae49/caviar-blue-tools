# Shamir Secret Sharing Integration Examples

## Table of Contents
1. [Database Integration](#database-integration)
2. [Cloud Storage Integration](#cloud-storage-integration)
3. [Key Management Systems](#key-management-systems)
4. [Microservices Architecture](#microservices-architecture)
5. [Blockchain Integration](#blockchain-integration)
6. [Hardware Security Modules](#hardware-security-modules)
7. [Multi-Factor Authentication](#multi-factor-authentication)
8. [Disaster Recovery Systems](#disaster-recovery-systems)

## Database Integration

### PostgreSQL Share Storage

Store shares in a PostgreSQL database with proper access controls:

```kotlin
// Database schema
CREATE TABLE secret_shares (
    share_id UUID PRIMARY KEY,
    share_index INTEGER NOT NULL,
    share_data TEXT NOT NULL,
    metadata TEXT NOT NULL,
    secret_identifier UUID NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    accessed_at TIMESTAMP,
    owner_id UUID NOT NULL,
    UNIQUE(secret_identifier, share_index)
);

CREATE TABLE share_access_log (
    log_id UUID PRIMARY KEY,
    share_id UUID REFERENCES secret_shares(share_id),
    accessed_by UUID NOT NULL,
    access_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    access_result VARCHAR(50),
    ip_address INET
);

// Kotlin integration
class DatabaseShareRepository(private val dataSource: DataSource) {
    
    fun storeShare(share: SecretShare, secretId: UUID, ownerId: UUID): UUID {
        val shareId = UUID.randomUUID()
        
        dataSource.connection.use { conn ->
            conn.prepareStatement("""
                INSERT INTO secret_shares 
                (share_id, share_index, share_data, metadata, secret_identifier, owner_id)
                VALUES (?, ?, ?, ?, ?, ?)
            """).use { stmt ->
                stmt.setObject(1, shareId)
                stmt.setInt(2, share.index)
                stmt.setString(3, share.toBase64())
                stmt.setString(4, share.metadata.toBase64())
                stmt.setObject(5, secretId)
                stmt.setObject(6, ownerId)
                stmt.executeUpdate()
            }
        }
        
        return shareId
    }
    
    fun retrieveShares(secretId: UUID, userId: UUID): List<SecretShare> {
        val shares = mutableListOf<SecretShare>()
        
        dataSource.connection.use { conn ->
            // Log access attempt
            logAccess(conn, secretId, userId)
            
            conn.prepareStatement("""
                SELECT share_data 
                FROM secret_shares 
                WHERE secret_identifier = ? 
                AND owner_id = ?
                ORDER BY share_index
            """).use { stmt ->
                stmt.setObject(1, secretId)
                stmt.setObject(2, userId)
                
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    val shareData = rs.getString("share_data")
                    shares.add(SecretShare.fromBase64(shareData))
                }
            }
            
            // Update access timestamp
            updateAccessTime(conn, secretId)
        }
        
        return shares
    }
    
    private fun logAccess(conn: Connection, shareId: UUID, userId: UUID) {
        conn.prepareStatement("""
            INSERT INTO share_access_log 
            (log_id, share_id, accessed_by, access_result)
            VALUES (?, ?, ?, ?)
        """).use { stmt ->
            stmt.setObject(1, UUID.randomUUID())
            stmt.setObject(2, shareId)
            stmt.setObject(3, userId)
            stmt.setString(4, "SUCCESS")
            stmt.executeUpdate()
        }
    }
}
```

### MongoDB Document Storage

For NoSQL integration with MongoDB:

```kotlin
// MongoDB document structure
data class ShareDocument(
    @BsonId val id: ObjectId = ObjectId(),
    val shareIndex: Int,
    val shareData: String,
    val metadata: ShareMetadataDoc,
    val secretIdentifier: String,
    val tags: List<String> = emptyList(),
    val encryption: EncryptionInfo? = null,
    val createdAt: Instant = Instant.now(),
    val expiresAt: Instant? = null
)

data class ShareMetadataDoc(
    val threshold: Int,
    val totalShares: Int,
    val shareSetId: String,
    val checksum: String
)

class MongoShareRepository(private val mongoClient: MongoClient) {
    private val database = mongoClient.getDatabase("secret_sharing")
    private val collection = database.getCollection<ShareDocument>("shares")
    
    init {
        // Create indexes
        collection.createIndex(Indexes.ascending("secretIdentifier"))
        collection.createIndex(Indexes.ascending("expiresAt"))
        collection.createIndex(
            Indexes.compoundIndex(
                Indexes.ascending("secretIdentifier"),
                Indexes.ascending("shareIndex")
            )
        )
    }
    
    suspend fun storeShares(
        shares: List<SecretShare>, 
        secretId: String,
        tags: List<String> = emptyList(),
        ttl: Duration? = null
    ) {
        val documents = shares.map { share ->
            ShareDocument(
                shareIndex = share.index,
                shareData = share.toBase64(),
                metadata = ShareMetadataDoc(
                    threshold = share.metadata.threshold,
                    totalShares = share.metadata.totalShares,
                    shareSetId = share.metadata.shareSetId,
                    checksum = Base64.getEncoder().encodeToString(share.dataHash)
                ),
                secretIdentifier = secretId,
                tags = tags,
                expiresAt = ttl?.let { Instant.now().plus(it) }
            )
        }
        
        collection.insertMany(documents)
    }
    
    suspend fun retrieveShares(secretId: String, minShares: Int): List<SecretShare>? {
        val shares = collection
            .find(Filters.eq("secretIdentifier", secretId))
            .sort(Indexes.ascending("shareIndex"))
            .toList()
        
        if (shares.size < minShares) {
            return null
        }
        
        return shares.map { doc ->
            SecretShare.fromBase64(doc.shareData)
        }
    }
}
```

## Cloud Storage Integration

### AWS S3 Integration

Distribute shares across multiple S3 buckets in different regions:

```kotlin
class S3ShareDistribution(
    private val s3Clients: Map<String, S3Client>,
    private val sss: ShamirSecretSharing
) {
    
    data class S3ShareLocation(
        val region: String,
        val bucket: String,
        val key: String
    )
    
    fun distributeToS3(
        secret: ByteArray,
        config: SSSConfig,
        bucketMapping: Map<Int, String> // share index to region
    ): Map<Int, S3ShareLocation> {
        val splitResult = sss.split(secret, config).getOrThrow()
        val locations = mutableMapOf<Int, S3ShareLocation>()
        
        splitResult.shares.forEach { share ->
            val region = bucketMapping[share.index] 
                ?: throw IllegalArgumentException("No region for share ${share.index}")
            
            val s3Client = s3Clients[region]
                ?: throw IllegalArgumentException("No S3 client for region $region")
            
            val bucket = "shares-$region"
            val key = "shares/${share.metadata.shareSetId}/${share.index}.enc"
            
            // Encrypt share before uploading
            val encryptedShare = encryptShare(share)
            
            val putRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .serverSideEncryption(ServerSideEncryption.AES256)
                .metadata(mapOf(
                    "share-index" to share.index.toString(),
                    "threshold" to share.metadata.threshold.toString(),
                    "total-shares" to share.metadata.totalShares.toString()
                ))
                .build()
            
            s3Client.putObject(
                putRequest,
                RequestBody.fromBytes(encryptedShare)
            )
            
            locations[share.index] = S3ShareLocation(region, bucket, key)
        }
        
        return locations
    }
    
    fun reconstructFromS3(
        locations: List<S3ShareLocation>,
        decryptionKey: SecretKey
    ): ByteArray? {
        val shares = locations.mapNotNull { location ->
            try {
                val s3Client = s3Clients[location.region]
                val getRequest = GetObjectRequest.builder()
                    .bucket(location.bucket)
                    .key(location.key)
                    .build()
                
                val encryptedData = s3Client.getObject(getRequest).readAllBytes()
                val shareData = decryptShare(encryptedData, decryptionKey)
                SecretShare.fromBase64(String(shareData))
            } catch (e: Exception) {
                null // Share not available
            }
        }
        
        return sss.reconstruct(shares).getOrNull()
    }
}
```

### Azure Blob Storage

```kotlin
class AzureBlobShareStorage(
    private val blobServiceClients: Map<String, BlobServiceClient>
) {
    
    fun distributeShares(
        shares: List<SecretShare>,
        containerName: String = "secret-shares"
    ): List<ShareLocation> {
        val locations = mutableListOf<ShareLocation>()
        
        // Distribute shares across storage accounts
        shares.forEachIndexed { index, share ->
            val accountIndex = index % blobServiceClients.size
            val accountName = blobServiceClients.keys.elementAt(accountIndex)
            val client = blobServiceClients[accountName]!!
            
            val containerClient = client.getBlobContainerClient(containerName)
            if (!containerClient.exists()) {
                containerClient.create()
            }
            
            val blobName = "${share.metadata.shareSetId}/share_${share.index}.dat"
            val blobClient = containerClient.getBlobClient(blobName)
            
            // Upload with metadata
            val shareData = share.toBase64().toByteArray()
            blobClient.upload(
                BinaryData.fromBytes(shareData),
                true // overwrite
            )
            
            blobClient.setMetadata(mapOf(
                "shareIndex" to share.index.toString(),
                "threshold" to share.metadata.threshold.toString(),
                "checksum" to Base64.getEncoder().encodeToString(share.dataHash)
            ))
            
            locations.add(ShareLocation(accountName, containerName, blobName))
        }
        
        return locations
    }
}
```

## Key Management Systems

### HashiCorp Vault Integration

```kotlin
class VaultShareManager(
    private val vaultClient: Vault,
    private val sss: ShamirSecretSharing
) {
    
    fun protectVaultRootToken(rootToken: String): Map<String, String> {
        // Split root token into shares
        val config = SSSConfig(threshold = 3, totalShares = 5)
        val splitResult = sss.split(rootToken, config).getOrThrow()
        
        val shareLocations = mutableMapOf<String, String>()
        
        splitResult.shares.forEachIndexed { index, share ->
            val path = "secret/data/root-shares/share_$index"
            
            // Store in Vault with restricted policy
            vaultClient.logical()
                .write(
                    path,
                    mapOf(
                        "data" to mapOf(
                            "share" to share.toBase64(),
                            "index" to share.index,
                            "metadata" to share.metadata.toBase64()
                        )
                    )
                )
            
            // Create policy for this share
            val policy = """
                path "$path" {
                    capabilities = ["read"]
                    allowed_parameters = {
                        "data" = []
                    }
                    min_wrapping_ttl = "1h"
                    max_wrapping_ttl = "24h"
                }
            """.trimIndent()
            
            vaultClient.sys().createPolicy("share-$index-reader", policy)
            shareLocations["share_$index"] = path
        }
        
        return shareLocations
    }
    
    fun reconstructRootToken(
        sharePaths: List<String>,
        unwrapTokens: List<String>
    ): String? {
        val shares = sharePaths.zip(unwrapTokens).mapNotNull { (path, token) ->
            try {
                // Use unwrap token to read share
                val tempVault = Vault(vaultClient.address(), token)
                val response = tempVault.logical().read(path)
                val shareData = response.data["share"] as String
                SecretShare.fromBase64(shareData)
            } catch (e: Exception) {
                null
            }
        }
        
        return sss.reconstructString(shares).getOrNull()
    }
}
```

### AWS KMS Integration

```kotlin
class KMSShareProtection(
    private val kmsClient: KmsClient,
    private val sss: ShamirSecretSharing
) {
    
    fun protectDataKey(dataKey: ByteArray, keyAliases: List<String>): List<EncryptedShare> {
        require(keyAliases.size >= 5) { "Need at least 5 KMS keys" }
        
        val config = SSSConfig(
            threshold = 3,
            totalShares = keyAliases.size
        )
        
        val splitResult = sss.split(dataKey, config).getOrThrow()
        
        return splitResult.shares.zip(keyAliases).map { (share, alias) ->
            // Encrypt each share with different KMS key
            val encryptRequest = EncryptRequest.builder()
                .keyId(alias)
                .plaintext(SdkBytes.fromByteArray(share.toBase64().toByteArray()))
                .encryptionContext(mapOf(
                    "purpose" to "share_encryption",
                    "share_index" to share.index.toString()
                ))
                .build()
            
            val encryptResponse = kmsClient.encrypt(encryptRequest)
            
            EncryptedShare(
                index = share.index,
                encryptedData = encryptResponse.ciphertextBlob().asByteArray(),
                keyAlias = alias,
                metadata = share.metadata
            )
        }
    }
    
    fun reconstructDataKey(
        encryptedShares: List<EncryptedShare>
    ): ByteArray? {
        val decryptedShares = encryptedShares.mapNotNull { encShare ->
            try {
                val decryptRequest = DecryptRequest.builder()
                    .ciphertextBlob(SdkBytes.fromByteArray(encShare.encryptedData))
                    .keyId(encShare.keyAlias)
                    .encryptionContext(mapOf(
                        "purpose" to "share_encryption",
                        "share_index" to encShare.index.toString()
                    ))
                    .build()
                
                val decryptResponse = kmsClient.decrypt(decryptRequest)
                val shareData = String(decryptResponse.plaintext().asByteArray())
                SecretShare.fromBase64(shareData)
            } catch (e: Exception) {
                null
            }
        }
        
        return sss.reconstruct(decryptedShares).getOrNull()
    }
}

data class EncryptedShare(
    val index: Int,
    val encryptedData: ByteArray,
    val keyAlias: String,
    val metadata: ShareMetadata
)
```

## Microservices Architecture

### gRPC Service Implementation

```kotlin
// Protocol Buffers definition
syntax = "proto3";

service SecretSharingService {
    rpc SplitSecret(SplitRequest) returns (SplitResponse);
    rpc ReconstructSecret(ReconstructRequest) returns (ReconstructResponse);
    rpc ValidateShares(ValidateRequest) returns (ValidateResponse);
}

message SplitRequest {
    bytes secret = 1;
    int32 threshold = 2;
    int32 total_shares = 3;
    map<string, string> metadata = 4;
}

message SplitResponse {
    repeated Share shares = 1;
    string share_set_id = 2;
    string error = 3;
}

// Kotlin gRPC service implementation
class SecretSharingGrpcService(
    private val sss: ShamirSecretSharing
) : SecretSharingServiceGrpcKt.SecretSharingServiceCoroutineImplBase() {
    
    override suspend fun splitSecret(request: SplitRequest): SplitResponse {
        return try {
            val config = SSSConfig(
                threshold = request.threshold,
                totalShares = request.totalShares
            )
            
            val result = sss.split(request.secret.toByteArray(), config)
            
            when (result) {
                is SSSResult.Success -> {
                    SplitResponse.newBuilder().apply {
                        result.value.shares.forEach { share ->
                            addShares(
                                Share.newBuilder()
                                    .setIndex(share.index)
                                    .setData(ByteString.copyFrom(share.toBase64().toByteArray()))
                                    .build()
                            )
                        }
                        shareSetId = result.value.metadata.shareSetId
                    }.build()
                }
                is SSSResult.Failure -> {
                    SplitResponse.newBuilder()
                        .setError(result.message)
                        .build()
                }
                else -> {
                    SplitResponse.newBuilder()
                        .setError("Unexpected result type")
                        .build()
                }
            }
        } catch (e: Exception) {
            SplitResponse.newBuilder()
                .setError("Internal error: ${e.message}")
                .build()
        }
    }
    
    override suspend fun reconstructSecret(request: ReconstructRequest): ReconstructResponse {
        return try {
            val shares = request.sharesList.map { shareProto ->
                SecretShare.fromBase64(shareProto.data.toStringUtf8())
            }
            
            val result = sss.reconstruct(shares)
            
            when (result) {
                is SSSResult.Success -> {
                    ReconstructResponse.newBuilder()
                        .setSecret(ByteString.copyFrom(result.value))
                        .setSuccess(true)
                        .build()
                }
                is SSSResult.Failure -> {
                    ReconstructResponse.newBuilder()
                        .setSuccess(false)
                        .setError(result.message)
                        .build()
                }
                else -> {
                    ReconstructResponse.newBuilder()
                        .setSuccess(false)
                        .setError("Unexpected result type")
                        .build()
                }
            }
        } catch (e: Exception) {
            ReconstructResponse.newBuilder()
                .setSuccess(false)
                .setError("Internal error: ${e.message}")
                .build()
        }
    }
}
```

### REST API with Spring Boot

```kotlin
@RestController
@RequestMapping("/api/v1/secret-sharing")
class SecretSharingController(
    private val sss: ShamirSecretSharing,
    private val shareRepository: ShareRepository
) {
    
    @PostMapping("/split")
    fun splitSecret(@RequestBody request: SplitSecretRequest): ResponseEntity<SplitSecretResponse> {
        val config = SSSConfig(
            threshold = request.threshold,
            totalShares = request.totalShares
        )
        
        val secret = Base64.getDecoder().decode(request.secretBase64)
        val result = sss.split(secret, config)
        
        return when (result) {
            is SSSResult.Success -> {
                val shareIds = result.value.shares.map { share ->
                    val id = shareRepository.save(share, request.ownerId)
                    ShareInfo(
                        shareId = id,
                        shareIndex = share.index,
                        shareData = share.toBase64()
                    )
                }
                
                ResponseEntity.ok(
                    SplitSecretResponse(
                        success = true,
                        shares = shareIds,
                        metadata = ShareMetadataInfo(
                            shareSetId = result.value.metadata.shareSetId,
                            threshold = result.value.metadata.threshold,
                            totalShares = result.value.metadata.totalShares
                        )
                    )
                )
            }
            is SSSResult.Failure -> {
                ResponseEntity.badRequest().body(
                    SplitSecretResponse(
                        success = false,
                        error = result.message
                    )
                )
            }
            else -> {
                ResponseEntity.internalServerError().body(
                    SplitSecretResponse(
                        success = false,
                        error = "Unexpected error"
                    )
                )
            }
        }
    }
    
    @PostMapping("/reconstruct")
    fun reconstructSecret(
        @RequestBody request: ReconstructSecretRequest,
        @RequestHeader("Authorization") auth: String
    ): ResponseEntity<ReconstructSecretResponse> {
        // Verify user has access to shares
        val userId = extractUserId(auth)
        val shares = shareRepository.findByIdsAndOwner(request.shareIds, userId)
        
        if (shares.size < request.shareIds.size) {
            return ResponseEntity.status(403).body(
                ReconstructSecretResponse(
                    success = false,
                    error = "Access denied to one or more shares"
                )
            )
        }
        
        val result = sss.reconstruct(shares)
        
        return when (result) {
            is SSSResult.Success -> {
                ResponseEntity.ok(
                    ReconstructSecretResponse(
                        success = true,
                        secretBase64 = Base64.getEncoder().encodeToString(result.value)
                    )
                )
            }
            is SSSResult.Failure -> {
                ResponseEntity.badRequest().body(
                    ReconstructSecretResponse(
                        success = false,
                        error = result.message
                    )
                )
            }
            else -> {
                ResponseEntity.internalServerError().body(
                    ReconstructSecretResponse(
                        success = false,
                        error = "Reconstruction failed"
                    )
                )
            }
        }
    }
}

// Request/Response DTOs
data class SplitSecretRequest(
    val secretBase64: String,
    val threshold: Int,
    val totalShares: Int,
    val ownerId: String
)

data class SplitSecretResponse(
    val success: Boolean,
    val shares: List<ShareInfo>? = null,
    val metadata: ShareMetadataInfo? = null,
    val error: String? = null
)
```

## Blockchain Integration

### Ethereum Smart Contract

```solidity
// Solidity contract for share registry
pragma solidity ^0.8.0;

contract ShareRegistry {
    struct ShareMetadata {
        bytes32 shareSetId;
        uint8 threshold;
        uint8 totalShares;
        uint256 timestamp;
        address owner;
    }
    
    struct ShareLocation {
        string storageType; // "ipfs", "swarm", "filecoin"
        string location;    // Storage identifier
        bytes32 checksum;   // SHA-256 hash
    }
    
    mapping(bytes32 => ShareMetadata) public shareMetadata;
    mapping(bytes32 => mapping(uint8 => ShareLocation)) public shareLocations;
    mapping(address => bytes32[]) public ownerShares;
    
    event ShareSetCreated(bytes32 indexed shareSetId, address indexed owner);
    event ShareLocationStored(bytes32 indexed shareSetId, uint8 shareIndex);
    
    function registerShareSet(
        bytes32 _shareSetId,
        uint8 _threshold,
        uint8 _totalShares
    ) external {
        require(shareMetadata[_shareSetId].timestamp == 0, "Share set already exists");
        require(_threshold > 0 && _threshold <= _totalShares, "Invalid threshold");
        
        shareMetadata[_shareSetId] = ShareMetadata({
            shareSetId: _shareSetId,
            threshold: _threshold,
            totalShares: _totalShares,
            timestamp: block.timestamp,
            owner: msg.sender
        });
        
        ownerShares[msg.sender].push(_shareSetId);
        emit ShareSetCreated(_shareSetId, msg.sender);
    }
    
    function storeShareLocation(
        bytes32 _shareSetId,
        uint8 _shareIndex,
        string memory _storageType,
        string memory _location,
        bytes32 _checksum
    ) external {
        ShareMetadata memory metadata = shareMetadata[_shareSetId];
        require(metadata.owner == msg.sender, "Not the owner");
        require(_shareIndex > 0 && _shareIndex <= metadata.totalShares, "Invalid share index");
        
        shareLocations[_shareSetId][_shareIndex] = ShareLocation({
            storageType: _storageType,
            location: _location,
            checksum: _checksum
        });
        
        emit ShareLocationStored(_shareSetId, _shareIndex);
    }
}
```

### Kotlin Integration with Web3j

```kotlin
class BlockchainShareRegistry(
    private val web3j: Web3j,
    private val credentials: Credentials,
    private val contractAddress: String,
    private val sss: ShamirSecretSharing,
    private val ipfsClient: IPFSClient
) {
    
    private val shareRegistry = ShareRegistry.load(
        contractAddress,
        web3j,
        credentials,
        DefaultGasProvider()
    )
    
    fun registerAndDistributeShares(
        secret: ByteArray,
        config: SSSConfig
    ): TransactionReceipt {
        // Split secret
        val splitResult = sss.split(secret, config).getOrThrow()
        val shareSetId = splitResult.metadata.shareSetId.toByteArray()
        
        // Register on blockchain
        val receipt = shareRegistry.registerShareSet(
            shareSetId,
            config.threshold.toBigInteger(),
            config.totalShares.toBigInteger()
        ).send()
        
        // Upload shares to IPFS
        splitResult.shares.forEach { share ->
            val ipfsHash = ipfsClient.upload(share.toBase64().toByteArray())
            val checksum = MessageDigest.getInstance("SHA-256")
                .digest(share.data)
            
            shareRegistry.storeShareLocation(
                shareSetId,
                share.index.toBigInteger(),
                "ipfs",
                ipfsHash,
                checksum
            ).send()
        }
        
        return receipt
    }
    
    fun retrieveAndReconstruct(shareSetId: String): ByteArray? {
        val shareSetIdBytes = shareSetId.toByteArray()
        val metadata = shareRegistry.shareMetadata(shareSetIdBytes).send()
        
        val shares = mutableListOf<SecretShare>()
        
        for (i in 1..metadata.totalShares.toInt()) {
            val location = shareRegistry.shareLocations(shareSetIdBytes, i.toBigInteger()).send()
            
            if (location.storageType == "ipfs") {
                try {
                    val shareData = ipfsClient.retrieve(location.location)
                    val share = SecretShare.fromBase64(String(shareData))
                    
                    // Verify checksum
                    val checksum = MessageDigest.getInstance("SHA-256").digest(share.data)
                    if (checksum.contentEquals(location.checksum)) {
                        shares.add(share)
                    }
                } catch (e: Exception) {
                    // Share not available
                }
            }
            
            if (shares.size >= metadata.threshold.toInt()) {
                break
            }
        }
        
        return sss.reconstruct(shares).getOrNull()
    }
}
```

## Hardware Security Modules

### PKCS#11 HSM Integration

```kotlin
class HSMShareProtection(
    private val p11Provider: Provider,
    private val sss: ShamirSecretSharing
) {
    
    fun protectMasterKey(
        masterKeyHandle: Long,
        hsmSlots: List<Int>
    ): Map<Int, HSMShare> {
        val session = openSession()
        
        try {
            // Export master key (if allowed by HSM policy)
            val masterKeyBytes = exportKey(session, masterKeyHandle)
            
            // Split the key
            val config = SSSConfig(
                threshold = (hsmSlots.size / 2) + 1,
                totalShares = hsmSlots.size
            )
            val splitResult = sss.split(masterKeyBytes, config).getOrThrow()
            
            // Import shares into different HSM slots
            val hsmShares = mutableMapOf<Int, HSMShare>()
            
            splitResult.shares.zip(hsmSlots).forEach { (share, slot) ->
                val slotSession = openSession(slot)
                
                try {
                    // Generate wrapping key in target slot
                    val wrappingKey = generateAESKey(slotSession, 256)
                    
                    // Wrap share data
                    val wrappedShare = wrapData(slotSession, wrappingKey, share.toBase64().toByteArray())
                    
                    hsmShares[share.index] = HSMShare(
                        shareIndex = share.index,
                        slotId = slot,
                        wrappingKeyHandle = wrappingKey,
                        wrappedData = wrappedShare,
                        metadata = share.metadata
                    )
                } finally {
                    closeSession(slotSession)
                }
            }
            
            // Clear master key from memory
            Arrays.fill(masterKeyBytes, 0)
            
            return hsmShares
        } finally {
            closeSession(session)
        }
    }
    
    fun reconstructMasterKey(
        hsmShares: List<HSMShare>,
        targetSlot: Int
    ): Long? {
        val shares = hsmShares.mapNotNull { hsmShare ->
            val slotSession = openSession(hsmShare.slotId)
            
            try {
                // Unwrap share data
                val shareData = unwrapData(
                    slotSession,
                    hsmShare.wrappingKeyHandle,
                    hsmShare.wrappedData
                )
                
                SecretShare.fromBase64(String(shareData))
            } catch (e: Exception) {
                null
            } finally {
                closeSession(slotSession)
            }
        }
        
        // Reconstruct the key
        val masterKeyBytes = sss.reconstruct(shares).getOrNull() ?: return null
        
        // Import reconstructed key into target slot
        val targetSession = openSession(targetSlot)
        try {
            return importKey(targetSession, masterKeyBytes, "MASTER_KEY_RECONSTRUCTED")
        } finally {
            Arrays.fill(masterKeyBytes, 0)
            closeSession(targetSession)
        }
    }
}

data class HSMShare(
    val shareIndex: Int,
    val slotId: Int,
    val wrappingKeyHandle: Long,
    val wrappedData: ByteArray,
    val metadata: ShareMetadata
)
```

## Multi-Factor Authentication

### TOTP + SSS Integration

```kotlin
class MultiFactorSecretSharing(
    private val sss: ShamirSecretSharing,
    private val totpGenerator: TOTPGenerator
) {
    
    fun setupMultiFactorSharing(
        secret: ByteArray,
        userFactors: List<UserFactor>
    ): MultiFactorSetup {
        require(userFactors.size >= 5) { "Need at least 5 factors" }
        
        // Generate TOTP seeds for each factor
        val totpSeeds = userFactors.map { 
            SecureRandom().generateSeed(20) // 160-bit seed
        }
        
        // Split secret with higher threshold
        val config = SSSConfig(
            threshold = 3,
            totalShares = userFactors.size
        )
        val splitResult = sss.split(secret, config).getOrThrow()
        
        // Protect each share with TOTP
        val protectedShares = splitResult.shares.zip(userFactors).zip(totpSeeds)
            .map { (shareAndFactor, seed) ->
                val (share, factor) = shareAndFactor
                
                ProtectedShare(
                    factor = factor,
                    encryptedShare = encryptWithTOTP(share, seed),
                    totpSeed = seed,
                    shareIndex = share.index
                )
            }
        
        return MultiFactorSetup(
            protectedShares = protectedShares,
            metadata = splitResult.metadata,
            qrCodes = generateQRCodes(protectedShares)
        )
    }
    
    fun reconstructWithFactors(
        protectedShares: List<ProtectedShare>,
        totpCodes: Map<UserFactor, String>
    ): ByteArray? {
        val decryptedShares = protectedShares.mapNotNull { protectedShare ->
            val totpCode = totpCodes[protectedShare.factor] ?: return@mapNotNull null
            
            // Verify TOTP
            if (!totpGenerator.verify(totpCode, protectedShare.totpSeed)) {
                return@mapNotNull null
            }
            
            // Decrypt share
            try {
                decryptWithTOTP(protectedShare.encryptedShare, protectedShare.totpSeed)
            } catch (e: Exception) {
                null
            }
        }
        
        return sss.reconstruct(decryptedShares).getOrNull()
    }
    
    private fun encryptWithTOTP(share: SecretShare, totpSeed: ByteArray): ByteArray {
        // Derive encryption key from TOTP seed
        val key = deriveKey(totpSeed)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(share.toBase64().toByteArray())
        
        // Prepend IV to ciphertext
        return iv + ciphertext
    }
}

data class UserFactor(
    val type: FactorType,
    val identifier: String
)

enum class FactorType {
    PHONE_SMS,
    AUTHENTICATOR_APP,
    HARDWARE_TOKEN,
    BIOMETRIC,
    EMAIL
}
```

## Disaster Recovery Systems

### Geographically Distributed Recovery

```kotlin
class DisasterRecoverySystem(
    private val sss: ShamirSecretSharing,
    private val sites: List<RecoverySite>
) {
    
    fun setupDisasterRecovery(
        criticalData: ByteArray,
        recoveryPolicy: RecoveryPolicy
    ): DisasterRecoveryPlan {
        // Configure based on policy
        val config = when (recoveryPolicy.type) {
            PolicyType.HIGH_AVAILABILITY -> SSSConfig(
                threshold = 2,
                totalShares = sites.size
            )
            PolicyType.BALANCED -> SSSConfig(
                threshold = (sites.size / 2) + 1,
                totalShares = sites.size
            )
            PolicyType.HIGH_SECURITY -> SSSConfig(
                threshold = sites.size - 1,
                totalShares = sites.size
            )
        }
        
        val splitResult = sss.split(criticalData, config).getOrThrow()
        val distributions = mutableListOf<ShareDistribution>()
        
        // Distribute shares to sites
        splitResult.shares.zip(sites).forEach { (share, site) ->
            // Encrypt share for site
            val encryptedShare = site.encryptData(share.toBase64().toByteArray())
            
            // Store with redundancy
            val locations = when (site.type) {
                SiteType.PRIMARY -> {
                    listOf(
                        site.storePrimary(encryptedShare),
                        site.storeSecondary(encryptedShare)
                    )
                }
                SiteType.SECONDARY -> {
                    listOf(site.storePrimary(encryptedShare))
                }
                SiteType.COLD_BACKUP -> {
                    listOf(
                        site.storeOffline(encryptedShare),
                        site.storeArchive(encryptedShare)
                    )
                }
            }
            
            distributions.add(
                ShareDistribution(
                    site = site,
                    shareIndex = share.index,
                    locations = locations,
                    lastVerified = Instant.now()
                )
            )
        }
        
        return DisasterRecoveryPlan(
            planId = UUID.randomUUID(),
            metadata = splitResult.metadata,
            distributions = distributions,
            policy = recoveryPolicy,
            testSchedule = generateTestSchedule(recoveryPolicy)
        )
    }
    
    fun executeRecovery(
        plan: DisasterRecoveryPlan,
        availableSites: List<RecoverySite>
    ): RecoveryResult {
        val recoveryStart = Instant.now()
        val shareResults = mutableListOf<ShareRecoveryResult>()
        
        // Attempt to recover shares from available sites
        plan.distributions
            .filter { dist -> availableSites.contains(dist.site) }
            .forEach { distribution ->
                val result = tryRecoverShare(distribution)
                shareResults.add(result)
            }
        
        // Filter successful recoveries
        val recoveredShares = shareResults
            .filter { it.success }
            .mapNotNull { it.share }
        
        // Attempt reconstruction
        return if (recoveredShares.size >= plan.metadata.threshold) {
            val secret = sss.reconstruct(recoveredShares).getOrNull()
            
            RecoveryResult(
                success = secret != null,
                recoveredData = secret,
                sharesRecovered = recoveredShares.size,
                sharesNeeded = plan.metadata.threshold,
                duration = Duration.between(recoveryStart, Instant.now()),
                siteResults = shareResults
            )
        } else {
            RecoveryResult(
                success = false,
                recoveredData = null,
                sharesRecovered = recoveredShares.size,
                sharesNeeded = plan.metadata.threshold,
                duration = Duration.between(recoveryStart, Instant.now()),
                siteResults = shareResults
            )
        }
    }
    
    private fun tryRecoverShare(distribution: ShareDistribution): ShareRecoveryResult {
        for (location in distribution.locations) {
            try {
                val encryptedData = distribution.site.retrieveData(location)
                val shareData = distribution.site.decryptData(encryptedData)
                val share = SecretShare.fromBase64(String(shareData))
                
                return ShareRecoveryResult(
                    site = distribution.site,
                    shareIndex = distribution.shareIndex,
                    success = true,
                    share = share,
                    recoveryTime = Instant.now()
                )
            } catch (e: Exception) {
                // Try next location
            }
        }
        
        return ShareRecoveryResult(
            site = distribution.site,
            shareIndex = distribution.shareIndex,
            success = false,
            share = null,
            recoveryTime = Instant.now(),
            error = "All locations failed"
        )
    }
}

data class RecoverySite(
    val id: String,
    val name: String,
    val location: String,
    val type: SiteType,
    val encryptionKey: PublicKey
)

enum class SiteType {
    PRIMARY,
    SECONDARY,
    COLD_BACKUP
}
```

## Conclusion

These integration examples demonstrate how Shamir Secret Sharing can be integrated with various systems and architectures. Key principles across all integrations:

1. **Separation of Concerns**: Keep share distribution separate from share storage
2. **Defense in Depth**: Use multiple layers of protection (encryption, access control, etc.)
3. **Monitoring**: Track share access and recovery attempts
4. **Testing**: Regularly test recovery procedures
5. **Documentation**: Maintain clear documentation of share locations and recovery procedures

The flexibility of SSS allows it to enhance security in many different contexts while maintaining the core property that k shares are required for reconstruction.