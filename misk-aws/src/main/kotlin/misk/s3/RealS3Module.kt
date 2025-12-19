package misk.s3

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.google.inject.Provides
import jakarta.inject.Singleton
import misk.cloud.aws.AwsRegion
import misk.inject.KAbstractModule

open class RealS3Module : KAbstractModule() {
  override fun configure() {
    requireBinding<AWSCredentialsProvider>()
    requireBinding<AwsRegion>()
  }

  @Provides
  @Singleton
  fun provideS3(awsRegion: AwsRegion, awsCredentialsProvider: AWSCredentialsProvider): AmazonS3 {
    val builder = AmazonS3ClientBuilder.standard().withRegion(awsRegion.name).withCredentials(awsCredentialsProvider)
    configureClient(builder)
    return builder.build()
  }

  open fun configureClient(builder: AmazonS3ClientBuilder) {}
}
