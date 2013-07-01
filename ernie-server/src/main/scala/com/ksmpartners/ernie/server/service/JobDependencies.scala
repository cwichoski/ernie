/**
 * This source code file is the intellectual property of KSM Technology Partners LLC.
 * The contents of this file may not be reproduced, published, or distributed in any
 * form, except as allowed in a license agreement between KSM Technology Partners LLC
 * and a licensee. Copyright 2012 KSM Technology Partners LLC.  All rights reserved.
 */

package com.ksmpartners.ernie.server.service

import net.liftweb.common.{ Empty, Box, Full }
import net.liftweb.http._
import com.ksmpartners.ernie.model
import com.ksmpartners.ernie.engine
import java.io.IOException
import java.util
import collection.JavaConversions._
import org.slf4j.{ LoggerFactory, Logger }
import com.ksmpartners.ernie.server.{ DispatchRestAPI, RestGenerator, JsonTranslator }
import com.ksmpartners.ernie.model._
import com.ksmpartners.ernie.server.service.JobDependencies.log
import net.liftweb.http.StreamingResponse
import net.liftweb.http.InternalServerErrorResponse
import net.liftweb.http.ResponseWithReason
import net.liftweb.http.OkResponse
import net.liftweb.http.InMemoryResponse
import net.liftweb.common.Full
import scala.Some
import com.ksmpartners.ernie.engine.PurgeResponse
import net.liftweb.http.BadResponse
import net.liftweb.http.GoneResponse
import com.ksmpartners.ernie.engine.PurgeRequest
import com.ksmpartners.ernie.server.filter.AuthUtil
import com.ksmpartners.ernie.server.RestGenerator._
import com.ksmpartners.ernie.api
import scala.collection.JavaConversions

/**
 * Dependencies for starting and interacting with jobs for the creation of reports
 */
trait JobDependencies extends RequiresAPI {

  /**
   * Resource for handling HTTP requests at /jobs
   */
  class JobsResource extends JsonTranslator {
    /**
     * Return a Box[ListResponse] containing a map of jobId to URI for that jobId
     */
    val getJobsListAction = Action("getJobsMap", getMap(_: Package), "Return a map of jobId to URI", "", "jobStatusMap",
      DispatchRestAPI.timeoutErnieError("Jobs list"))
    def getMap(p: Package): Box[LiftResponse] = getMap
    def getMap(): Box[LiftResponse] = getMap("/jobs")
    def getMap(uriPrefix: String): Box[LiftResponse] =
      apiCall(getJobsListAction, _ => ernie.getJobList, (list: List[String]) =>
        {
          val jobsMap: util.Map[String, String] = new util.HashMap
          list.foreach({ jobId =>
            jobsMap.put(jobId, uriPrefix + "/" + jobId)
          })
          getJsonResponse(new model.JobsMapResponse(jobsMap))
        })

    /**
     * Return a Box[ListResponse] containing a catalog of all JobEntities
     */
    val getJobsCatalogAction = Action("getJobsCatalog", getCatalog(_: Package)(None), "Return a catalog of all JobEntities", "", "JobsCatalogResponse", DispatchRestAPI.timeoutErnieError("Jobs catalog"))
    val getFailedCatalogAction = Action("getFailedJobsCatalog", getCatalog(_: Package)(Some("failed")), "Return a catalog of failed jobs' JobEntities", "", "JobsCatalogResponse", DispatchRestAPI.timeoutErnieError("Jobs catalog"))
    val getCompleteCatalogAction = Action("getCompleteJobsCatalog", getCatalog(_: Package)(Some("complete")), "Return a catalog of complete jobs' JobEntities", "", "JobsCatalogResponse", DispatchRestAPI.timeoutErnieError("Jobs catalog"))
    val getExpiredCatalogAction = Action("getExpiredJobsCatalog", getCatalog(_: Package)(Some("expired")), "Return a catalog of expired jobs' JobEntities", "", "JobsCatalogResponse", DispatchRestAPI.timeoutErnieError("Jobs catalog"))
    val getDeletedCatalogAction = Action("getDeletedJobsCatalog", getCatalog(_: Package)(Some("deleted")), "Return a catalog of deleted jobs' JobEntities", "", "JobsCatalogResponse", DispatchRestAPI.timeoutErnieError("Jobs catalog"))

    def getCatalog(p: Package)(catalog: Option[String]): Box[LiftResponse] = getCatalog(catalog)
    def getCatalog(): Box[LiftResponse] = getCatalog(None)
    def getCatalog(cat: Option[String]): Box[LiftResponse] = {
      apiCall(getJobsCatalogAction, _ =>
        cat.getOrElse("").toLowerCase match {
          case "failed" => ernie.getJobCatalog(Some(JobCatalog.FAILED))
          case "complete" => ernie.getJobCatalog(Some(JobCatalog.COMPLETE))
          case "expired" => ernie.getJobCatalog(Some(JobCatalog.EXPIRED))
          case "deleted" => ernie.getJobCatalog(Some(JobCatalog.DELETED))
          case _ => ernie.getJobCatalog(None)
        }, (catalog: List[JobEntity]) => {
        val jobsCatalog: util.ArrayList[JobEntity] = new util.ArrayList
        catalog.foreach(f => jobsCatalog.add(f))
        getJsonResponse(new JobsCatalogResponse(jobsCatalog))
      })
    }

    /**
     * Sends the given ReportRequest to the Coordinator to be scheduled
     *
     * @return the jobId returned by the Coordinator associated with the request
     */
    val retentionDateExceedsMaximum = ErnieError(ResponseWithReason(BadResponse(), "Retention date exceeds maximum"), None)
    val retentionDateBeforeRequest = ErnieError(ResponseWithReason(BadResponse(), "Retention date before request time"), None)
    val noSuchDefinition = ErnieError(ResponseWithReason(BadResponse(), "No such definition ID"), None)
    val serverError = ErnieError(ResponseWithReason(InternalServerErrorResponse(), "Server error"), None)
    val invalidRequest = ErnieError(BadResponse(), None)
    val postJobAction = Action("postJob", post(_: Package), "Schedules the submitted job", "", "ReportResponse",
      DispatchRestAPI.timeoutErnieError("Job creation"),
      ErnieError(BadResponse(), Some(api.MissingArgumentException("No request body"))),
      retentionDateExceedsMaximum, retentionDateBeforeRequest, noSuchDefinition, serverError, invalidRequest)

    def post(body: Box[Array[Byte]], hostAndPath: String, userName: String): Box[LiftResponse] = {
      try {
        if (body.isEmpty) invalidRequest.send(Some("No request body"))
        else {
          val req = deserialize(body.open_!, classOf[model.ReportRequest])
          apiCall[(Long, JobStatus)](postJobAction, _ => ernie.createJob(req.getDefId, req.getRptType, if (req.getRetentionDays == 0) None else Some(req.getRetentionDays), if (req.getReportParameters == null) collection.immutable.Map.empty[String, String] else scala.collection.immutable.Map(req.getReportParameters.toList: _*), userName),
            resp => {
              if (resp._2 == JobStatus.FAILED_RETENTION_DATE_EXCEEDS_MAXIMUM) retentionDateExceedsMaximum.send
              else if (resp._2 == JobStatus.FAILED_RETENTION_DATE_PAST) retentionDateBeforeRequest.send
              else if (resp._2 == JobStatus.FAILED_NO_SUCH_DEFINITION) noSuchDefinition.send(Some("No such definition ID: " + req.getDefId))
              else getJsonResponse(new model.ReportResponse(resp._1, resp._2), 201, List(("Location", hostAndPath + "/jobs/" + resp._1)))
            })
        }
      } catch {
        case e: IOException => {
          log.error("Caught exception while handling request: {}", e.getMessage)
          invalidRequest.send(Some("Invalid request"))
        }
        case e: Exception => {
          log.error("Caught exception while handling request: {}", e.getMessage)
          serverError.send
        }
      }
    }

    def post(p: Package): Box[LiftResponse] = post(p.req)
    def post(body: Box[Array[Byte]], userName: String): Box[LiftResponse] = post(body, "", userName)
    def post(req: Req): Box[LiftResponse] = post(req.body, req.hostAndPath, AuthUtil.getUserName(req))
    val unexpectedErrorWithException = ErnieError(InternalServerErrorResponse(), Some(new Exception()))
    val purgeAction = Action("purgeExpired", purge(_: Package), "Purges expired jobs", "", "void", DispatchRestAPI.timeoutErnieError("Purge"), unexpectedErrorWithException)
    def purge(p: Package): Box[LiftResponse] = purge
    def purge(): Box[LiftResponse] = {
      apiCall[DeleteStatus](purgeAction, _ => ernie.purgeExpiredReports._1, purgeResp =>
        if (purgeResp == DeleteStatus.SUCCESS) {
          log.debug("Response: Ok Response.")
          Full(OkResponse())
        } else unexpectedErrorWithException.send)
    }
  }

  class JobEntityResource extends JsonTranslator {
    /**
     * Return a Box[ListResponse] containing a JobEntity
     */
    val jobNotFound = ErnieError(NotFoundResponse(), Some(api.NotFoundException("Job ID not found")))
    val getJobDetailAction = Action("getJobEntity", get(_: Package), "Return a JobEntity", "", "JobEntity", DispatchRestAPI.timeoutErnieError("Job detail"),
      jobNotFound)

    def get(p: Package): Box[LiftResponse] = if (p.params.length != 1) Full(ResponseWithReason(BadResponse(), "Invalid job ID")) else get(p.params(0).data.toString)
    def get(jobId: String): Box[LiftResponse] = {
      apiCall[Option[JobEntity]](getJobDetailAction, _ => ernie.getJobEntity(jobId.toLong), jobEnt =>
        if (jobEnt isDefined)
          getJsonResponse(jobEnt.get)
        else jobNotFound.send)
    }
  }

  /**
   * Resource for handling HTTP requests at /jobs/<JOB_ID>/status
   */
  class JobStatusResource extends JsonTranslator {
    /**
     * Return a Box[ListResponse] containing status for the given jobId
     */
    val jobGone = ErnieError(GoneResponse(), Some(api.NotFoundException("Job deleted")))
    val unexpectedError = ErnieError(InternalServerErrorResponse(), None)
    val getJobStatusAction: Action = Action("getJobStatus", get(_), "Return StatusResponse for given jobId", "", "StatusResponse", DispatchRestAPI.timeoutErnieError("Job status"),
      jobGone, unexpectedError)
    def get(p: Package): Box[LiftResponse] = if (p.params.length != 1) Full(ResponseWithReason(BadResponse(), "Invalid job ID")) else get(p.params(0).data.toString)

    def get(jobId: String): Box[LiftResponse] = {
      apiCall[JobStatus](getJobStatusAction, _ => ernie.getJobStatus(jobId.toLong), jobStatus =>
        if (jobStatus == JobStatus.DELETED) jobGone.send
        else getJsonResponse(new model.StatusResponse(jobStatus)))
    }
  }

  /**
   * Resource for handling HTTP requests at /jobs/<JOB_ID>/result
   */
  class JobResultsResource extends JsonTranslator {

    /**
     * Return a Box[StreamingResponse] containing the result content for the given jobId
     */
    val jobNotFound = ErnieError(NotFoundResponse(), Some(api.NotFoundException("Job ID not found")))
    val notAcceptable = ErnieError(ResponseWithReason(NotAcceptableResponse(), "Resource does not serve specified Accept type"), None)
    val unexpectedError = ErnieError(InternalServerErrorResponse(), None)
    val getJobResultAction = Action("getJobResult", get(_), "Returns a stream containing the result content for the given Job ID", "", "byte",
      DispatchRestAPI.timeoutErnieError("Job result"),
      ErnieError(GoneResponse(), Some(api.ReportOutputException(Some(JobStatus.DELETED), "Job deleted"))),
      jobNotFound,
      ErnieError(GoneResponse(), Some(api.ReportOutputException(Some(JobStatus.EXPIRED), "Report expired"))),
      ErnieError(BadResponse(), Some(api.ReportOutputException(None, "Job incomplete"))),
      notAcceptable, unexpectedError)
    def get(p: Package): Box[LiftResponse] = if (p.params.length != 1) Full(ResponseWithReason(BadResponse(), "Invalid job id")) else get(p.params(0).data.toString, Full(p.req))
    def get(jobId: String): Box[LiftResponse] = get(jobId, Empty)
    /**
     * Return a Box[StreamingResponse] containing the result content for the given jobId
     * Overloaded function to include the web service request details to ensure correct Accept
     */
    def get(jobId: String, req: Box[Req]): Box[LiftResponse] = {
      apiCall[(Option[java.io.InputStream], Option[ReportEntity])](getJobResultAction, _ => (ernie.getReportOutput(jobId.toLong), ernie.getReportEntity(jobId.toLong)), rptOutput =>
        {
          if (rptOutput._1.isDefined && rptOutput._2.isDefined) {
            val fileStream = rptOutput._1.get
            val rptEnt = rptOutput._2.get
            val header: List[(String, String)] =
              ("Content-Type" -> ("application/" + rptEnt.getReportType.toString.toLowerCase)) ::
                ("Content-Length" -> fileStream.available.toString) ::
                ("Content-Disposition" -> ("attachment; filename=\"" + rptEnt.getRptId + "." + rptEnt.getReportType.toString.toLowerCase + "\"")) :: Nil
            if (!req.isEmpty && !req.open_!.headers.contains(("Accept", header(0)._2))) {
              fileStream.close
              notAcceptable.send(Some("Resource only serves " + rptEnt.getReportType.toString.toLowerCase))
            } else {
              log.debug("Response: Streaming Response.")
              Full(StreamingResponse(
                fileStream,
                () => { fileStream.close() }, // On end method.
                fileStream.available,
                header, Nil, 200))
            }
          } else jobNotFound.send
        })
    }

    /**
     * Retrieves details for output from a given jobId
     */
    val invalidId = ErnieError(BadResponse(), None)
    val getDetailAction = Action("getResultDetail", getDetail(_), "Retrieves details for output from a given jobId", "", "ReportEntity",
      DispatchRestAPI.timeoutErnieError("Job detail"),
      ErnieError(NotFoundResponse(), Some(api.NotFoundException("Job ID not found"))),
      ErnieError(InternalServerErrorResponse(), None),
      invalidId)
    def getDetail(p: Package): Box[LiftResponse] = if (p.params.length != 1) invalidId.send(Some("Job ID Invalid")) else getDetail(p.params(0).data.toString, Full(p.req))
    def getDetail(jobId: String, req: Box[Req]): Box[LiftResponse] = {
      apiCall[Option[ReportEntity]](getDetailAction, _ => ernie.getReportEntity(jobId.toLong), rptEnt =>
        if (rptEnt.isDefined) {
          log.debug("Response: Report Entity")
          getJsonResponse(rptEnt.get)
        } else unexpectedError.send)
    }

    /**
     * Purges the report output for a given jobId
     */
    val jobInUse = ErnieError(ResponseWithReason(ConflictResponse(), "Job result in use"), None)
    val deleteFailed = ErnieError(ResponseWithReason(InternalServerErrorResponse(), "Job deletion failed"), None)
    val deleteReportAction = Action("deleteReport", del(_), "Purges report output for a given Job ID", "", "DeleteResponse",
      DispatchRestAPI.timeoutErnieError("Report delete"),
      jobNotFound, jobInUse, deleteFailed)
    def del(p: Package): Box[LiftResponse] = if (p.params.length != 1) Full(ResponseWithReason(BadResponse(), "Invalid job id")) else del(p.params(0).data.toString)
    def del(jobId: String): Box[LiftResponse] = {
      apiCall[DeleteStatus](deleteReportAction, _ => ernie.deleteReportOutput(jobId.toLong), deleteStatus =>
        if (deleteStatus == DeleteStatus.SUCCESS) getJsonResponse(new model.DeleteResponse(deleteStatus))
        else if (deleteStatus == DeleteStatus.NOT_FOUND) jobNotFound.send
        else if (deleteStatus == DeleteStatus.FAILED_IN_USE) jobInUse.send
        else deleteFailed.send)
    }
  }

}

object JobDependencies {
  val log: Logger = LoggerFactory.getLogger("com.ksmpartners.ernie.server.service.JobDependencies")
}

