package misk.s3

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.google.inject.Provides
import misk.cloud.aws.AwsRegion
import misk.inject.KAbstractModule
import javax.inject.Singleton

class RealS3Module constructor() : KAbstractModule() {
  override fun configure() {
    requireBinding<AWSCredentialsProvider>()
    requireBinding<AwsRegion>()
  }

  @Provides @Singleton
  fun provideS3(awsRegion: AwsRegion, awsCredentialsProvider: AWSCredentialsProvider): AmazonS3 {
    return AmazonS3ClientBuilder
        .standard()
        .withRegion(awsRegion.name)
        .withCredentials(awsCredentialsProvider)
        .build()
  }
}
