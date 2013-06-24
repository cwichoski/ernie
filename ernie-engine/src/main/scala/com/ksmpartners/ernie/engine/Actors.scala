/**
 * This source code file is the intellectual property of KSM Technology Partners LLC.
 * The contents of this file may not be reproduced, published, or distributed in any
 * form, except as allowed in a license agreement between KSM Technology Partners LLC
 * and a licensee. Copyright 2012 KSM Technology Partners LLC.  All rights reserved.
 */

package com.ksmpartners.ernie.engine

import collection._
import collection.JavaConversions._
import com.ksmpartners.ernie.model._
import com.ksmpartners.ernie.engine.report._
import org.slf4j.{ Logger, LoggerFactory }
import org.joda.time.{ Days, DateTime }
import com.ksmpartners.ernie.util.Utility._
import org.eclipse.birt.report.engine.api.UnsupportedFormatException
import com.ksmpartners.ernie.util.MapperUtility._
import scala.Some
import java.io.{ IOException, File, FileOutputStream }
import Coordinator._
import akka.actor._
import akka.actor.ActorDSL._
import scala.concurrent._
import scala.Some
import ExecutionContext.Implicits.{ global }
import akka.pattern.ask
import akka.util.Timeout
import akka.actor.Props
import scala.concurrent.duration._
import scala.concurrent.duration.DurationInt

object Coordinator {
  val log: Logger = LoggerFactory.getLogger("com.ksmpartners.ernie.engine.report.Coordinator")
}

trait ErnieCoordinator extends Actor {

}
/**
 * Actor for coordinating report generation.
 */
class Coordinator(_pathToJobEntities: Option[String], rptMgr: ReportManager, to: Option[FiniteDuration], wC: Int = 1) extends ErnieActions {
  this: ReportGeneratorFactory =>
  //private val log = LoggerFactory.getLogger(classOf[Coordinator])
  val year: FiniteDuration = 365 days
  private var workerCount = wC
  implicit val timeout = Timeout.durationToTimeout(to getOrElse year)
  var currentWorker = -1
  protected def worker: ActorRef = {
    currentWorker += 1
    if (currentWorker >= workerCount) currentWorker = 0
    context.children.toList(currentWorker)
  }
  private def spawnWorker() {
    context.actorOf(Props(new Worker(getReportGenerator(reportManager))))
    workerCount += 1
  }
  protected val jobIdToResultMap: mutable.HashMap[Long, JobEntity] = new mutable.HashMap[Long, JobEntity]() /* rptId */
  protected val reportManager = rptMgr
  protected val pathToJobEntities: Option[String] = _pathToJobEntities
  //val system = akka.actor.ActorSystem("system")

  private var noRestartingJobs = true
  //override def start(): Actor = {
  /* log.debug("in start()")
    super.start()
    worker.start()
    if (pathToJobEntities.isDefined) {
      val path = pathToJobEntities.get
      val jobDir = new java.io.File(path)
      if (!(jobDir.isDirectory && jobDir.canRead))
        throw new IOException("Input/output directories do not exist or do not have the correct read/write access. " +
          "Job Dir: " + jobDir)

      val files = (new java.io.File(path)).listFiles()
      if (files != null)
        files.filter({ _.isFile }).filter({ _.getName.endsWith("entity") }).foreach({ file =>
          try {
            val jobEnt = mapper.readValue(file, classOf[JobEntity])
            val jobId = file.getName.replaceFirst("[.][^.]+$", "").toLong
            jobIdToResultMap += (jobId -> jobEnt)
            if (((jobEnt.getJobStatus == JobStatus.IN_PROGRESS) || (jobEnt.getJobStatus == JobStatus.PENDING)) && (jobEnt.getRptEntity != null)) {
              jobEnt.setJobStatus(JobStatus.RESTARTING)
              noRestartingJobs = false
              updateJob(jobId, jobEnt)
            }
          } catch {
            case e: Exception => log.error("Caught exception while loading job entities: {}", e.getMessage)
          }
        })
    }             */
  //   this
  //}
  private def handleRestartingJobs() = if (!noRestartingJobs) {
    noRestartingJobs = true
    val restJobs = jobIdToResultMap.filter(p => p._2.getJobStatus == JobStatus.RESTARTING)
    restJobs.foreach(f => {
      val jobEnt = f._2
      val jobId = f._1
      worker ! JobRequest(jobEnt.getRptEntity.getSourceDefId, jobEnt.getRptEntity.getReportType, jobId, Some(Days.daysBetween(DateTime.now, jobEnt.getRptEntity.getRetentionDate).getDays),
        if (jobEnt.getRptEntity.getParams != null) immutable.Map(jobEnt.getRptEntity.getParams.toList: _*) else immutable.Map.empty[String, String], jobEnt.getRptEntity.getCreatedUser)
    })
  }
  private def checkExpired(jobId: Long) {
    reportManager.getReport(jobToRptId(jobId)).map(f => {
      val jobEnt = jobIdToResultMap.get(jobId).get
      if (f.getRetentionDate.isBeforeNow && (jobEnt.getJobStatus == JobStatus.COMPLETE)) {
        updateJob(jobId, {
          jobEnt.setJobStatus(JobStatus.EXPIRED)
          jobEnt
        })
      }
    })
  }

  def startReportGenerator() {
    val rptGen = getReportGenerator(reportManager)
    rptGen.startup
  }

  override def preStart() {
    log.debug("in start()")
    var i: Int = 0
    startReportGenerator
    for (i <- 1.to(workerCount))
      context.actorOf(Props(new Worker(getReportGenerator(reportManager))))
    if (pathToJobEntities.isDefined) {
      val path = pathToJobEntities.get
      val jobDir = new java.io.File(path)
      if (!(jobDir.isDirectory && jobDir.canRead))
        throw new IOException("Input/output directories do not exist or do not have the correct read/write access. " +
          "Job Dir: " + jobDir)

      val files = (new java.io.File(path)).listFiles()
      if (files != null)
        files.filter({ _.isFile }).filter({ _.getName.endsWith("entity") }).foreach({ file =>
          try {
            val jobEnt = mapper.readValue(file, classOf[JobEntity])
            val jobId = file.getName.replaceFirst("[.][^.]+$", "").toLong
            jobIdToResultMap += (jobId -> jobEnt)
            if (((jobEnt.getJobStatus == JobStatus.IN_PROGRESS) || (jobEnt.getJobStatus == JobStatus.PENDING)) && (jobEnt.getRptEntity != null)) {
              jobEnt.setJobStatus(JobStatus.RESTARTING)
              noRestartingJobs = false
              updateJob(jobId, jobEnt)
            }
          } catch {
            case e: Exception => log.error("Caught exception while loading job entities: {}", e.getMessage)
          }
        })
    }
    context.system.scheduler.scheduleOnce(5 seconds) {
      handleRestartingJobs
    }
  }

  override def receive = {
    case req @ ReportRequest(defId, rptType, retentionOption, reportParameters, userName) => reportRequest(req, sender)
    case req @ DeleteRequest(jobId) => deleteRequest(req, sender)
    case req @ DeleteDefinitionRequest(defId) => deleteDefinitionRequest(req, sender)
    case req @ PurgeRequest() => purgeRequest(req, sender)
    case req @ StatusRequest(jobId) => {
      checkExpired(jobId)
      sender ! StatusResponse(jobIdToResultMap.get(jobId).map(je => je.getJobStatus) getOrElse (JobStatus.NO_SUCH_JOB), req)
    }
    case req @ ReportDetailRequest(jobId) => {
      sender ! ReportDetailResponse(jobIdToResultMap.get(jobId).map(je => reportManager.getReport(jobToRptId(je.getJobId)).map(f => f.getEntity)) getOrElse None, req)
    }
    case req @ JobDetailRequest(jobId) => {
      sender ! JobDetailResponse(jobIdToResultMap.get(jobId), req)
    }
    case NewWorkerRequest() => spawnWorker()
    case req @ ResultRequest(jobId) => resultRequest(req, sender)
    case req @ JobsListRequest() => {
      val jobsList: Array[String] = jobIdToResultMap.keySet.map({ _.toString }).toArray
      sender ! JobsListResponse(jobsList, req)
    }
    case req @ JobsCatalogRequest(jobCatalog) => jobsCatalogRequest(req, sender)
    case resp @ JobResponse(jobStatus, rptId, req) => {
      jobResponse(resp, sender)
    }
    case ShutDownRequest() => {
      log.info("Shutting down")
      val resps = context.children.map(f => f ! (ShutDownRequest()))
      sender ! ShutDownResponse()
      context.stop(self)
    }
    case msg => log.info("Received unexpected message: {}", msg)
  }

  protected def updateJob(jobId: Long, jobEnt: JobEntity) {
    jobIdToResultMap += (jobId -> jobEnt)
    pathToJobEntities.map(path => {
      val jobEntFile = new File(path, jobId + ".entity")
      jobEntFile.delete
      jobEntFile.createNewFile
      try_(new FileOutputStream(jobEntFile, false)) { fos =>
        mapper.writeValue(fos, jobEnt)
      }
    })
  }

  private var currJobId = System.currentTimeMillis

  def setTimeout(t: Timeout) {
    //  timeout = t
  }

  protected def generateJobId(): Long = {
    currJobId += 1
    currJobId
  }

}

/**
 * Actor that is paired with a Coordinator, and executes report requests.
 */
class Worker(rptGenerator: ReportGenerator) extends Actor {

  private val log = LoggerFactory.getLogger(classOf[Worker])

  override def receive = {
    case req @ JobRequest(defId, rptType, jobId, retentionOption, reportParameters, userName) => jobRequest(req, sender)

    case ShutDownRequest() => {
      stopRptGenerator()
      sender ! ShutDownResponse()
      context.stop(self)
    }
    case msg => log.info("Received unexpected message: {}", msg)
  }

  override def preStart() = {
    log.debug("in start()")
    startRptGenerator()
  }

  private def jobRequest(req: JobRequest, sender: ActorRef) {
    sender ! JobResponse(JobStatus.IN_PROGRESS, None, req)
    var resultStatus = JobStatus.COMPLETE
    var rptId: Option[String] = None
    try {
      rptId = Some(runReport(req.defId, req.jobId, req.rptType, req.retentionPeriod, req.reportParameters, req.userName))
    } catch {
      case ex: Exception => resultStatus = handleReportException(ex)
    }
    sender ! JobResponse(resultStatus, rptId, req)
  }

  private def handleReportException(ex: Exception): JobStatus = ex match {
    case ex: ParameterNullException => {
      log.error("Caught ParameterNullException exception while generating report: {}", ex.getMessage)
      JobStatus.FAILED_PARAMETER_NULL
    }
    case ex: InvalidParameterValuesException => {
      log.error("Caught InvalidParameterValuesException exception while generating report: {}", ex.getMessage)
      JobStatus.FAILED_INVALID_PARAMETER_VALUES
    }
    case ex: UnsupportedDataTypeException => {
      log.error("Caught UnsupportedDataTypeException exception while generating report: {}", ex.getMessage)
      JobStatus.FAILED_UNSUPPORTED_PARAMETER_TYPE
    }
    case ex: ReportManager.RetentionDateAfterMaximumException => {
      log.error("Caught RetentionDateAfterMaximumException exception while generating report: {}", ex.getMessage)
      JobStatus.FAILED_RETENTION_DATE_EXCEEDS_MAXIMUM
    }
    case ex: ReportManager.RetentionDateInThePastException => {
      log.error("Caught RetentionDateInThePastException exception while generating report: {}", ex.getMessage)
      JobStatus.FAILED_RETENTION_DATE_PAST
    }
    case ex: UnsupportedFormatException => {
      log.error("Caught UnsupportedFormatException exception while generating report: {}", ex.getMessage)
      JobStatus.FAILED_UNSUPPORTED_FORMAT
    }
    case ex: ClassCastException => {
      log.error("Caught ClassCastException exception while generating report: {}", ex.getMessage)
      JobStatus.FAILED_INVALID_PARAMETER_VALUES
    }
    case ex: Exception => {
      log.error("Caught " + ex.getClass + " exception while generating report: {}", ex.getMessage)
      log.error(ex.getStackTraceString)
      JobStatus.FAILED
    }
  }

  private def runReport(defId: String, jobId: Long, rptType: ReportType, retentionOption: Option[Int], reportParameters: immutable.Map[String, String], userName: String): String = {
    log.info("Running report {} for jobId {}...", defId, jobId)
    val rptId = jobToRptId(jobId)
    rptGenerator.runReport(defId, rptId, rptType, retentionOption, reportParameters, userName)
    log.info("Done running report {} for jobId {}...", defId, jobId)
    rptId
  }

  private def startRptGenerator() {
    rptGenerator.startup()
  }

  private def stopRptGenerator() {
    rptGenerator.shutdown()
  }

}
