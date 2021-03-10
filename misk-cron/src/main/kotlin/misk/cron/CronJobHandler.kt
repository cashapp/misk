package misk.cron

import com.squareup.moshi.Moshi
import misk.jobqueue.Job
import misk.jobqueue.JobHandler
import misk.moshi.adapter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CronJobHandler @Inject constructor(moshi: Moshi) : JobHandler {
  @Inject private lateinit var cronManager: CronManager

  private val jobAdapter = moshi.adapter<CronJob>()

  override fun handleJob(job: Job) {
    val cronJob = jobAdapter.fromJson(job.body)!!

    cronManager.runJob(cronJob.name)
    job.acknowledge()
  }
}
