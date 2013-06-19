/**
 * This source code file is the intellectual property of KSM Technology Partners LLC.
 * The contents of this file may not be reproduced, published, or distributed in any
 * form, except as allowed in a license agreement between KSM Technology Partners LLC
 * and a licensee. Copyright 2012 KSM Technology Partners LLC.  All rights reserved.
 */

package com.ksmpartners.ernie.engine

import scala.collection.{ JavaConversions, mutable }
import com.ksmpartners.ernie.model._
import org.joda.time.DateTime
import com.ksmpartners.ernie.engine.report.ReportManager
import scala.actors.OutputChannel
import Coordinator._
import com.ksmpartners.ernie.util.Utility._
import com.ksmpartners.ernie.engine.DeleteRequest
import scala.Some
import com.ksmpartners.ernie.engine.DeleteDefinitionRequest
import com.ksmpartners.ernie.engine.DeleteDefinitionResponse
import com.ksmpartners.ernie.engine.ReportRequest
import com.ksmpartners.ernie.engine.PurgeResponse
import com.ksmpartners.ernie.engine.JobRequest
import com.ksmpartners.ernie.engine.DeleteResponse
import com.ksmpartners.ernie.engine.ReportResponse
import com.ksmpartners.ernie.engine.PurgeRequest
import com.ksmpartners.ernie.engine.DeleteRequest
import scala.Some
import com.ksmpartners.ernie.engine.DeleteDefinitionRequest
import com.ksmpartners.ernie.engine.DeleteDefinitionResponse
import com.ksmpartners.ernie.engine.ReportRequest
import com.ksmpartners.ernie.engine.PurgeResponse
import com.ksmpartners.ernie.engine.JobRequest
import com.ksmpartners.ernie.engine.ResultRequest
import com.ksmpartners.ernie.engine.ResultResponse
import com.ksmpartners.ernie.engine.JobsCatalogRequest
import com.ksmpartners.ernie.engine.DeleteResponse
import com.ksmpartners.ernie.engine.ReportResponse
import com.ksmpartners.ernie.engine.PurgeRequest
import com.ksmpartners.ernie.engine.JobsCatalogResponse

trait ErnieActions {
  protected val worker: Worker
  protected val jobIdToResultMap: mutable.HashMap[Long, JobEntity]
  protected var timeout: Long
  protected val reportManager: ReportManager
  protected val pathToJobEntities: String
  protected def generateJobId(): Long
  protected def updateJob(jobId: Long, jobEnt: JobEntity)

  def reportRequest(req: ReportRequest, sender: OutputChannel[Any]) {
    val reportParameters = req.reportParameters
    val retentionOption = req.retentionPeriod
    val defId = req.defId
    val userName = req.userName
    val rptType = req.rptType
    val jobId = generateJobId()
    if (!reportParameters.isEmpty) log.info(pathToJobEntities + "/" + jobId + "")
    if (reportManager.getDefinition(defId).isDefined) {
      val rptEntity = new ReportEntity()
      rptEntity.setSourceDefId(defId)
      rptEntity.setReportType(rptType)
      rptEntity.setRetentionDate(DateTime.now.plusDays(retentionOption.getOrElse(reportManager.getDefaultRetentionDays)))
      rptEntity.setParams(JavaConversions.asJavaMap(reportParameters))
      rptEntity.setCreatedDate(DateTime.now)
      rptEntity.setCreatedUser(userName)
      updateJob(jobId, new JobEntity(jobId, JobStatus.IN_PROGRESS, DateTime.now, null, rptEntity))
      reportManager.getDefinition(defId).map(m => if ((m.getEntity.getUnsupportedReportTypes != null) && m.getEntity.getUnsupportedReportTypes.contains(rptType)) {
        try {
          val jobEnt = jobIdToResultMap.get(jobId).map(je => { je.setJobStatus(JobStatus.FAILED_UNSUPPORTED_FORMAT); je }).get
          updateJob(jobId, jobEnt)
        } catch {
          case e: Exception => {
            log.error("Caught exception while running report: {}", e.getMessage)
          }
        }
        sender ! ReportResponse(jobId, JobStatus.FAILED_UNSUPPORTED_FORMAT, req)
      } else {
        val retentionDate = DateTime.now().plusDays(retentionOption getOrElse reportManager.getDefaultRetentionDays)
        if (retentionDate.isBefore(DateTime.now()) || retentionDate.isEqual(DateTime.now())) sender ! ReportResponse(jobId, JobStatus.FAILED_RETENTION_DATE_PAST, req)
        else if (retentionDate.isAfter(DateTime.now().plusDays(reportManager.getMaximumRetentionDays))) sender ! ReportResponse(jobId, JobStatus.FAILED_RETENTION_DATE_EXCEEDS_MAXIMUM, req)
        else {
          sender ! ReportResponse(jobId, JobStatus.IN_PROGRESS, req)
          worker ! JobRequest(defId, rptType, jobId, retentionOption, reportParameters, rptEntity.getCreatedUser)
        }
      })
    } else {
      updateJob(jobId, new JobEntity(jobId, JobStatus.FAILED_NO_SUCH_DEFINITION, DateTime.now, null, null))
      sender ! ReportResponse(jobId, JobStatus.FAILED_NO_SUCH_DEFINITION, req)
    }
  }

  def deleteRequest(req: DeleteRequest, sender: OutputChannel[Any]) {
    val jobId = req.jobId
    if (jobIdToResultMap.contains(jobId)) {
      if ((jobIdToResultMap.get(jobId).map(je => je.getJobStatus).getOrElse(null) == JobStatus.COMPLETE) &&
        (jobIdToResultMap.get(jobId).map(je => je.getRptId).map(f => f != "").getOrElse(false))) {
        try {
          reportManager.deleteReport(jobIdToResultMap.get(jobId).map(je => je.getRptId).get)
          updateJob(jobId, jobIdToResultMap.get(jobId).map(je => { je.setJobStatus(JobStatus.DELETED); je }).get)
          sender ! DeleteResponse(jobIdToResultMap.get(jobId).map(je => je.getJobStatus match {
            case JobStatus.DELETED => DeleteStatus.SUCCESS
            case _ => DeleteStatus.FAILED
          }).get, req)
        } catch {
          case e: Exception => sender ! DeleteResponse(jobIdToResultMap.get(jobId).map(je => je.getJobStatus match {
            case JobStatus.DELETED => DeleteStatus.SUCCESS
            case _ => DeleteStatus.FAILED
          }).getOrElse(DeleteStatus.FAILED), req)
        }
      } else sender ! DeleteResponse(jobIdToResultMap.get(jobId).map(je => je.getJobStatus match {
        case JobStatus.DELETED => DeleteStatus.SUCCESS
        case _ => DeleteStatus.FAILED
      }).getOrElse(DeleteStatus.FAILED), req) // TODO: Send back "jobIdToResultMap.get(jobId).get._1" because the status could be PENDING or FAILED
    } else sender ! DeleteResponse(DeleteStatus.NOT_FOUND, req) //no such job
  }

  def deleteDefinitionRequest(req: DeleteDefinitionRequest, sender: OutputChannel[Any]) {
    val defId = req.defId
    if (jobIdToResultMap.find(p => {
      val defIdOpt = if (p._2.getRptEntity != null) Some(p._2.getRptEntity.getSourceDefId)
      else reportManager.getReport(p._2.getRptId).map(r => r.getSourceDefId)
      if (defIdOpt.isDefined) (defIdOpt.get == defId) && ((p._2.getJobStatus == JobStatus.IN_PROGRESS) || (p._2.getJobStatus == JobStatus.PENDING))
      else false
    }).isDefined) {
      sender ! DeleteDefinitionResponse(DeleteStatus.FAILED_IN_USE, req)
    } else try {
      reportManager.deleteDefinition(defId)
      sender ! DeleteDefinitionResponse(DeleteStatus.SUCCESS, req)
    } catch { case _ => sender ! DeleteDefinitionResponse(DeleteStatus.FAILED, req) }
  }

  def purgeRequest(req: PurgeRequest, sender: OutputChannel[Any]) {
    var purgedReports: List[String] = Nil
    var deleteStatus = DeleteStatus.SUCCESS
    jobIdToResultMap.foreach(f => if ((f._2 != null)) {
      val rptOpt = reportManager.getReport(jobToRptId(f._1))
      if (((f._2.getJobStatus == JobStatus.COMPLETE) || (f._2.getJobStatus == JobStatus.EXPIRED)) && (rptOpt isDefined)) {
        val rptId = rptOpt.get.getRptId

        if (reportManager.getReport(rptId).isDefined) {
          if (reportManager.getReport(rptId).get.getRetentionDate.isBeforeNow) {
            purgedReports ::= rptId
            try {
              reportManager.deleteReport(rptId)
              updateJob(f._1, jobIdToResultMap.get(f._1).map(je => { je.setJobStatus(JobStatus.DELETED); je }).get)
            } catch {
              case e: NoSuchElementException => {
                log.error("Caught exception while purging reports: {}", e.getMessage)
                deleteStatus = DeleteStatus.FAILED
              }
              case e: Exception => {
                log.error("Caught exception while purging reports: {}", e.getMessage + "\n" + e.getStackTraceString)
                deleteStatus = DeleteStatus.FAILED
              }
            }
          }
        }
      }
    })
    sender ! PurgeResponse(deleteStatus, purgedReports, req)
  }

  def resultRequest(req: ResultRequest, sender: OutputChannel[Any]) {
    val jobId = req.jobId
    val rptId = jobIdToResultMap.get(jobId).map(je => je.getRptId) orElse (None)
    sender ! ResultResponse(rptId match {
      case Some("") => None
      case Some(null) => None
      case None => None
      case Some(string) => Some(string)
      case _ => None
    }, req)
  }

  def jobsCatalogRequest(req: JobsCatalogRequest, sender: OutputChannel[Any]) {
    val jobCatalog = req.jobCatalog
    val jobsList: List[JobEntity] = if (jobCatalog.isDefined) jobCatalog.getOrElse(null) match {
      case JobCatalog.FAILED => jobIdToResultMap.filter(f => f._2.getJobStatus match {
        case JobStatus.FAILED => true
        case JobStatus.FAILED_INVALID_PARAMETER_VALUES => true
        case JobStatus.FAILED_NO_SUCH_DEFINITION => true
        case JobStatus.FAILED_PARAMETER_NULL => true
        case JobStatus.FAILED_RETENTION_DATE_EXCEEDS_MAXIMUM => true
        case JobStatus.FAILED_RETENTION_DATE_PAST => true
        case JobStatus.FAILED_UNSUPPORTED_FORMAT => true
        case JobStatus.FAILED_UNSUPPORTED_PARAMETER_TYPE => true
        case _ => false
      }).map(f => f._2).toList
      case JobCatalog.COMPLETE => jobIdToResultMap.filter(f => f._2.getJobStatus == JobStatus.COMPLETE).map(f => f._2).toList
      case JobCatalog.DELETED => jobIdToResultMap.filter(f => f._2.getJobStatus == JobStatus.DELETED).map(f => f._2).toList
      case JobCatalog.IN_PROGRESS => jobIdToResultMap.filter(f => f._2.getJobStatus == JobStatus.IN_PROGRESS).map(f => f._2).toList
      case JobCatalog.EXPIRED => jobIdToResultMap.filter(f => {
        val rptOpt = reportManager.getReport(f._2.getRptId)
        rptOpt.map(rpt => DateTime.now.isAfter(rpt.getRetentionDate)).getOrElse(false)
      }).map(f => f._2).toList
      case _ => jobIdToResultMap.map(f => f._2).toList
    }
    else jobIdToResultMap.map(f => f._2).toList
    sender ! JobsCatalogResponse(jobsList, req)
  }

  def jobResponse(resp: JobResponse, sender: OutputChannel[Any]) = {
    val req = resp.req
    val jobStatus = resp.jobStatus
    val rptId = resp.rptId
    log.info("Got notify for jobId {} with status {}", req.jobId, jobStatus)
    try {
      updateJob(req.jobId, jobIdToResultMap.get(req.jobId).map(je => { je.setJobStatus(jobStatus); je.setRptId(rptId.getOrElse(null)); je.setRptEntity(null); je }).get)
    } catch {
      case e: Exception => {
        log.error("Caught exception while running report: {}", e.getMessage)
      }
    }
    if (jobStatus == JobStatus.FAILED_UNSUPPORTED_FORMAT) reportManager.getDefinition(req.defId).map(defn =>
      {
        val entity = defn.getEntity
        val unsupportedRptTypes = entity.getUnsupportedReportTypes
        unsupportedRptTypes.add(req.rptType)
        entity.setUnsupportedReportTypes(unsupportedRptTypes)
        reportManager.updateDefinitionEntity(rptId.getOrElse(""), entity)
      })
  }

}
