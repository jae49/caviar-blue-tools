package cb.core.tools.erasure.models

data class Shard(
    val index: Int,
    val data: ByteArray,
    val metadata: ShardMetadata
) {
    val isDataShard: Boolean = index < metadata.config.dataShards
    val isParityShard: Boolean = !isDataShard

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Shard

        if (index != other.index) return false
        if (!data.contentEquals(other.data)) return false
        if (metadata != other.metadata) return false

        return true
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + data.contentHashCode()
        result = 31 * result + metadata.hashCode()
        return result
    }
}

data class ShardMetadata(
    val originalSize: Long,
    val config: EncodingConfig,
    val checksum: String,
    val timestamp: Long = System.currentTimeMillis(),
    val chunkIndex: Int? = null
)