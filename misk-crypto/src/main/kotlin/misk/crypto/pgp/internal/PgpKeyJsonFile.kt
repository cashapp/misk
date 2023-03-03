package misk.crypto.pgp.internal

data class PgpKeyJsonFile(
    val region: String,
    val encrypted_private_key: String,
    val public_key: String,
    val pgp: PgpKeyJsonFileMetadata,
    val aws_kms_key_id: String
)
