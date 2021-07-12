package misk.cloud.gcp.storage

import com.google.api.services.storage.model.Bucket
import com.google.api.services.storage.model.BucketAccessControl
import com.google.api.services.storage.model.HmacKey
import com.google.api.services.storage.model.HmacKeyMetadata
import com.google.api.services.storage.model.Notification
import com.google.api.services.storage.model.ObjectAccessControl
import com.google.api.services.storage.model.Policy
import com.google.api.services.storage.model.ServiceAccount
import com.google.api.services.storage.model.StorageObject
import com.google.api.services.storage.model.TestIamPermissionsResponse
import com.google.cloud.Tuple
import com.google.cloud.storage.spi.v1.RpcBatch
import com.google.cloud.storage.spi.v1.StorageRpc

/**
 * Base for custom storage implementations. Most operations on custom storage are unsupported;
 * only those minimal methods requiring implementation are left abstract.
 */
abstract class BaseCustomStorageRpc : StorageRpc {
  override fun get(bucket: Bucket, options: Map<StorageRpc.Option, *>): Bucket? {
    throw UnsupportedOperationException()
  }

  override fun patch(bucket: Bucket, options: Map<StorageRpc.Option, *>): Bucket? {
    throw UnsupportedOperationException()
  }

  override fun patch(obj: StorageObject, options: Map<StorageRpc.Option, *>): StorageObject? {
    throw UnsupportedOperationException()
  }

  override fun delete(bucket: Bucket, options: Map<StorageRpc.Option, *>): Boolean {
    throw UnsupportedOperationException()
  }

  override fun create(bucket: Bucket, options: Map<StorageRpc.Option, *>): Bucket {
    throw UnsupportedOperationException()
  }

  override fun list(options: Map<StorageRpc.Option, *>?): Tuple<String, Iterable<Bucket>> {
    throw UnsupportedOperationException()
  }

  override fun createAcl(
    acl: BucketAccessControl?,
    options: Map<StorageRpc.Option, *>?
  ): BucketAccessControl {
    throw UnsupportedOperationException()
  }

  override fun createAcl(acl: ObjectAccessControl?): ObjectAccessControl {
    throw UnsupportedOperationException()
  }

  override fun getIamPolicy(bucket: String?, options: Map<StorageRpc.Option, *>?): Policy {
    throw UnsupportedOperationException()
  }

  override fun patchAcl(
    acl: BucketAccessControl?,
    options: Map<StorageRpc.Option, *>?
  ): BucketAccessControl {
    throw UnsupportedOperationException()
  }

  override fun patchAcl(acl: ObjectAccessControl?): ObjectAccessControl {
    throw UnsupportedOperationException()
  }

  override fun lockRetentionPolicy(bucket: Bucket?, options: Map<StorageRpc.Option, *>?): Bucket {
    throw UnsupportedOperationException()
  }

  override fun getServiceAccount(projectId: String?): ServiceAccount {
    throw UnsupportedOperationException()
  }

  override fun getAcl(
    bucket: String?,
    entity: String?,
    options: Map<StorageRpc.Option, *>?
  ): BucketAccessControl {
    throw UnsupportedOperationException()
  }

  override fun getAcl(
    bucket: String?,
    obj: String?,
    generation: Long?,
    entity: String?
  ): ObjectAccessControl {
    throw UnsupportedOperationException()
  }

  override fun listDefaultAcls(bucket: String?): MutableList<ObjectAccessControl> {
    throw UnsupportedOperationException()
  }

  override fun testIamPermissions(
    bucket: String,
    permissions: List<String>,
    options: Map<StorageRpc.Option, *>
  ): TestIamPermissionsResponse {
    throw UnsupportedOperationException()
  }

  override fun continueRewrite(
    previousResponse: StorageRpc.RewriteResponse
  ): StorageRpc.RewriteResponse {
    throw UnsupportedOperationException()
  }

  override fun createBatch(): RpcBatch {
    throw UnsupportedOperationException()
  }

  override fun createNotification(bucket: String?, notification: Notification?): Notification {
    throw UnsupportedOperationException()
  }

  override fun listAcls(
    bucket: String?,
    options: Map<StorageRpc.Option, *>?
  ): List<BucketAccessControl> {
    throw UnsupportedOperationException()
  }

  override fun listAcls(
    bucket: String?,
    obj: String?,
    generation: Long?
  ): List<ObjectAccessControl> {
    throw UnsupportedOperationException()
  }

  override fun getDefaultAcl(bucket: String?, entity: String?): ObjectAccessControl {
    throw UnsupportedOperationException()
  }

  override fun deleteAcl(
    bucket: String?,
    entity: String?,
    options: Map<StorageRpc.Option, *>?
  ): Boolean {
    throw UnsupportedOperationException()
  }

  override fun deleteAcl(
    bucket: String?,
    `object`: String?,
    generation: Long?,
    entity: String?
  ): Boolean {
    throw UnsupportedOperationException()
  }

  override fun compose(
    sources: Iterable<StorageObject>?,
    target: StorageObject?,
    targetOptions: Map<StorageRpc.Option, *>?
  ): StorageObject {
    throw UnsupportedOperationException()
  }

  override fun read(
    from: StorageObject?,
    options: Map<StorageRpc.Option, *>?,
    position: Long,
    bytes: Int
  ): Tuple<String, ByteArray> {
    throw UnsupportedOperationException()
  }

  override fun open(signedURL: String?): String {
    throw UnsupportedOperationException()
  }

  override fun setIamPolicy(
    bucket: String?,
    policy: Policy?,
    options: Map<StorageRpc.Option, *>?
  ): Policy {
    throw UnsupportedOperationException()
  }

  override fun deleteDefaultAcl(bucket: String?, entity: String?): Boolean {
    throw UnsupportedOperationException()
  }

  override fun patchDefaultAcl(acl: ObjectAccessControl?): ObjectAccessControl {
    throw UnsupportedOperationException()
  }

  override fun createDefaultAcl(acl: ObjectAccessControl?): ObjectAccessControl {
    throw UnsupportedOperationException()
  }

  override fun listNotifications(bucket: String?): List<Notification> {
    throw UnsupportedOperationException()
  }

  override fun deleteNotification(bucket: String?, notification: String?): Boolean {
    throw UnsupportedOperationException()
  }

  override fun getCurrentUploadOffset(p0: String?): Long {
    throw UnsupportedOperationException()
  }

  override fun createHmacKey(p0: String?, p1: MutableMap<StorageRpc.Option, *>?): HmacKey {
    throw UnsupportedOperationException()
  }

  override fun listHmacKeys(p0: MutableMap<StorageRpc.Option, *>?): Tuple<String, MutableIterable<HmacKeyMetadata>> {
    throw UnsupportedOperationException()
  }

  override fun updateHmacKey(
    p0: HmacKeyMetadata?,
    p1: MutableMap<StorageRpc.Option, *>?
  ): HmacKeyMetadata {
    throw UnsupportedOperationException()
  }

  override fun getHmacKey(p0: String?, p1: MutableMap<StorageRpc.Option, *>?): HmacKeyMetadata {
    throw UnsupportedOperationException()
  }

  override fun deleteHmacKey(p0: HmacKeyMetadata?, p1: MutableMap<StorageRpc.Option, *>?) {
    throw UnsupportedOperationException()
  }

  override fun writeWithResponse(
    p0: String?,
    p1: ByteArray?,
    p2: Int,
    p3: Long,
    p4: Int,
    p5: Boolean
  ): StorageObject {
    throw UnsupportedOperationException()
  }

}
