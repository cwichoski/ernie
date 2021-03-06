/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ksmpartners.ernie.server

import filter.SAMLConstants
import org.testng.annotations._
import net.liftweb.mockweb.{ MockWeb, WebSpec }
import bootstrap.liftweb.Boot
import net.liftweb.mocks.MockHttpServletRequest
import com.ksmpartners.ernie.server.PropertyNames._
import com.ksmpartners.ernie.model._
import org.testng.Assert
import net.liftweb.http._
import java.util.Properties
import java.io.{ FileInputStream, File }
import com.ksmpartners.ernie.util.Utility._
import org.slf4j.{ Logger, LoggerFactory }
import net.liftweb.json.JsonAST
import net.liftweb.json.JsonAST.{ JBool, JField, JObject }

import com.ksmpartners.ernie.util.MapperUtility._
import net.liftweb.http.StreamingResponse
import net.liftweb.json.JsonAST.JObject
import net.liftweb.http.ResponseWithReason
import net.liftweb.http.BadResponse
import net.liftweb.http.GoneResponse
import net.liftweb.json.JsonAST.JField
import net.liftweb.json.JsonAST.JBool
import scala.xml.NodeSeq
import scala.collection.JavaConversions.asJavaCollection
import com.ksmpartners.ernie.server.service.{ ServiceRegistry, ConflictResponse }

import com.ksmpartners.ernie.engine.PurgeResponse
import com.ksmpartners.ernie.engine.PurgeRequest
import scala.collection.JavaConversions

class JobTest extends WebSpec(() => Unit) with TestSetupUtilities {

  private val log: Logger = LoggerFactory.getLogger("com.ksmpartners.ernie.server.JobTest")

  @Test(enabled = false)
  private var testJobHTMLID: Long = -1L

  @Test(enabled = false)
  private var testJobCSVID: Long = -1L

  @AfterMethod
  def logMethodAfter(result: java.lang.reflect.Method) {
    log.debug("END test:" + result.getName)
  }

  @BeforeMethod
  def logMethodBefore(result: java.lang.reflect.Method) {
    log.debug("BEGIN test:" + result.getName)
  }

  @AfterClass(groups = Array("REST"))
  def finish() {

  }

  @Test
  def canGetJobs() {
    val mockReq = new MockReadAuthReq("/jobs")

    mockReq.headers += ("Accept" -> List(ModelObject.TYPE_FULL))

    MockWeb.testReq(mockReq) { req =>
      val resp = DispatchRestAPI(req)()
      Assert.assertTrue(resp.isDefined)
      Assert.assertTrue(resp.open_!.isInstanceOf[PlainTextResponse])
      Assert.assertEquals(resp.open_!.toResponse.code, 200)
      val body = resp.open_!.asInstanceOf[PlainTextResponse].text
      val respObj = mapper.readValue(body, classOf[JobsMapResponse])
      Assert.assertNotNull(respObj.getJobStatusMap)
    }
  }

  @Test
  def jobsListServiceReturnsJSON() {
    val mockReq = new MockReadAuthReq("/jobs")
    mockReq.headers += ("Accept" -> List(ModelObject.TYPE_FULL))
    MockWeb.testReq(mockReq) { req =>
      val resp = DispatchRestAPI(req)()
      Assert.assertTrue(resp.isDefined)
      Assert.assertTrue(resp.open_!.toResponse.headers.contains(("Content-Type", "application/vnd.ksmpartners.ernie+json")))
    }
  }

  @Test
  def canPostJob() {
    val mockReq = new MockWriteAuthReq("/jobs")
    mockReq.method = "POST"
    mockReq.headers += ("Accept" -> List(ModelObject.TYPE_FULL))

    val mockReportReq = new ReportRequest()
    mockReportReq.setDefId(testDef)
    mockReportReq.setRptType(ReportType.PDF)
    mockReq.body = DispatchRestAPI.serialize(mockReportReq).getBytes

    MockWeb.testReq(mockReq) { req =>
      val resp = DispatchRestAPI(req)()
      Assert.assertTrue(resp.isDefined)
      Assert.assertTrue(resp.open_!.isInstanceOf[PlainTextResponse])
      Assert.assertEquals(resp.open_!.toResponse.code, 201)
      val reportResponse: ReportResponse = DispatchRestAPI.deserialize(resp.open_!.asInstanceOf[PlainTextResponse].toResponse.data, classOf[ReportResponse])
      Assert.assertTrue(reportResponse.getJobId > -1L)
      Assert.assertTrue(resp.open_!.toResponse.headers.contains(("Location", req.hostAndPath + "/jobs/" + reportResponse.getJobId)))
    }
  }

  @Test
  def canPostJobAsRunUser() {
    val mockReq = new MockRunAuthReq("/jobs")
    mockReq.method = "POST"
    mockReq.headers += ("Accept" -> List(ModelObject.TYPE_FULL))

    val mockReportReq = new ReportRequest()
    mockReportReq.setDefId("test_def")
    mockReportReq.setRptType(ReportType.PDF)
    mockReq.body = DispatchRestAPI.serialize(mockReportReq).getBytes

    MockWeb.testReq(mockReq) { req =>
      val resp = DispatchRestAPI(req)()
      Assert.assertTrue(resp.isDefined)
      Assert.assertTrue(resp.open_!.isInstanceOf[PlainTextResponse])
      Assert.assertEquals(resp.open_!.toResponse.code, 201)
      val reportResponse: ReportResponse = DispatchRestAPI.deserialize(resp.open_!.asInstanceOf[PlainTextResponse].toResponse.data, classOf[ReportResponse])
      testJobID = reportResponse.getJobId()
      Assert.assertTrue(testJobID > -1L)
      Assert.assertTrue(resp.open_!.toResponse.headers.contains(("Location", req.hostAndPath + "/jobs/" + testJobID)))
    }
  }

  @Test
  def canPostJobHTML() {
    val mockReq = new MockWriteAuthReq("/jobs")
    mockReq.method = "POST"
    mockReq.headers += ("Accept" -> List(ModelObject.TYPE_FULL))

    val mockReportReq = new ReportRequest()
    mockReportReq.setDefId("test_def")
    mockReportReq.setRptType(ReportType.HTML)
    mockReq.body = DispatchRestAPI.serialize(mockReportReq).getBytes

    MockWeb.testReq(mockReq) { req =>
      val resp = DispatchRestAPI(req)()
      Assert.assertTrue(resp.isDefined)
      Assert.assertTrue(resp.open_!.isInstanceOf[PlainTextResponse])
      Assert.assertEquals(resp.open_!.toResponse.code, 201)

      val reportResponse: ReportResponse = DispatchRestAPI.deserialize(resp.open_!.asInstanceOf[PlainTextResponse].toResponse.data, classOf[ReportResponse])
      testJobHTMLID = reportResponse.getJobId()
      Assert.assertTrue(testJobHTMLID > -1L)
    }
  }

  @Test
  def canPostJobCSV() {
    val mockReq = new MockWriteAuthReq("/jobs")
    mockReq.method = "POST"
    mockReq.headers += ("Accept" -> List(ModelObject.TYPE_FULL))

    val mockReportReq = new ReportRequest()
    mockReportReq.setDefId("test_def")
    mockReportReq.setRptType(ReportType.CSV)
    mockReq.body = DispatchRestAPI.serialize(mockReportReq).getBytes

    MockWeb.testReq(mockReq) { req =>
      val resp = DispatchRestAPI(req)()
      Assert.assertTrue(resp.isDefined)
      Assert.assertTrue(resp.open_!.isInstanceOf[PlainTextResponse])
      Assert.assertEquals(resp.open_!.toResponse.code, 201)

      val reportResponse: ReportResponse = DispatchRestAPI.deserialize(resp.open_!.asInstanceOf[PlainTextResponse].toResponse.data, classOf[ReportResponse])
      testJobCSVID = reportResponse.getJobId()
      Assert.assertTrue(testJobCSVID > -1L)
    }
  }

  @Test
  def reportsServiceReturnsJSON() {
    val mockReq = new MockWriteAuthReq("/jobs")
    mockReq.method = "POST"
    mockReq.headers += ("Accept" -> List(ModelObject.TYPE_FULL))

    val mockReportReq = new ReportRequest()
    mockReportReq.setDefId("test_def")
    mockReportReq.setRptType(ReportType.HTML)
    mockReq.body = DispatchRestAPI.serialize(mockReportReq).getBytes

    MockWeb.testReq(mockReq) { req =>
      val resp = DispatchRestAPI(req)()
      Assert.assertTrue(resp.isDefined)
      Assert.assertTrue(resp.open_!.toResponse.headers.contains(("Content-Type", "application/vnd.ksmpartners.ernie+json")))
    }
  }

  def cantDeleteInUseDef() {
    val mockReq = new MockWriteAuthReq("/defs/" + testDef)
    mockReq.method = "DELETE"
    mockReq.headers += ("Accept" -> List(ModelObject.TYPE_FULL))

    MockWeb.testReq(mockReq) { req =>
      val resp = DispatchRestAPI(req)()
      Assert.assertTrue(resp.isDefined)

      Assert.assertTrue(resp.open_!.isInstanceOf[ConflictResponse])
    }
  }

  @Test(dependsOnMethods = Array("canPostJob"))
  def canCompleteJob() {
    val mockReq = new MockReadAuthReq("/jobs/" + testJobID + "/status")

    mockReq.headers += ("Accept" -> List(ModelObject.TYPE_FULL))
    var triedDelInUseDef = false
    var jobRunning = true
    val end = System.currentTimeMillis + (1000 * 300)
    while (jobRunning && (System.currentTimeMillis < end)) {
      MockWeb.testReq(mockReq) { req =>
        val resp = DispatchRestAPI(req)()
        resp.map(r =>
          if (r.isInstanceOf[PlainTextResponse])
            if (DispatchRestAPI.deserialize(resp.open_!.asInstanceOf[PlainTextResponse].toResponse.data, classOf[StatusResponse]).getJobStatus == JobStatus.COMPLETE) {
            jobRunning = false
            Assert.assertTrue(DispatchRestAPI.deserialize(resp.open_!.asInstanceOf[PlainTextResponse].toResponse.data, classOf[StatusResponse]).getJobStatus == JobStatus.COMPLETE)
          } else {
            //   if (!triedDelInUseDef) cantDeleteInUseDef()
            triedDelInUseDef = true
          })
      }
    }
  }

  @Test(dependsOnMethods = Array("canPostJobHTML"))
  def canCompleteJobHTML() {
    val mockReq = new MockReadAuthReq("/jobs/" + testJobHTMLID + "/status")

    mockReq.headers += ("Accept" -> List(ModelObject.TYPE_FULL))
    var jobRunning = true
    val end = System.currentTimeMillis + (1000 * 300)
    while (jobRunning && (System.currentTimeMillis < end)) {
      MockWeb.testReq(mockReq) { req =>
        val resp = DispatchRestAPI(req)()
        resp.map(r =>
          if (r.isInstanceOf[PlainTextResponse])
            if (DispatchRestAPI.deserialize(resp.open_!.asInstanceOf[PlainTextResponse].toResponse.data, classOf[StatusResponse]).getJobStatus == JobStatus.COMPLETE) {
            jobRunning = false
            Assert.assertTrue(DispatchRestAPI.deserialize(resp.open_!.asInstanceOf[PlainTextResponse].toResponse.data, classOf[StatusResponse]).getJobStatus == JobStatus.COMPLETE)
          })
      }
    }

  }

  @Test(dependsOnMethods = Array("canPostJobCSV"))
  def canCompleteJobCSV() {
    val mockReq = new MockReadAuthReq("/jobs/" + testJobCSVID + "/status")

    mockReq.headers += ("Accept" -> List(ModelObject.TYPE_FULL))
    var jobRunning = true
    val end = System.currentTimeMillis + (1000 * 300)
    while (jobRunning && (System.currentTimeMillis < end)) {
      MockWeb.testReq(mockReq) { req =>
        val resp = DispatchRestAPI(req)()
        resp.map(r =>
          if (r.isInstanceOf[PlainTextResponse])
            if (DispatchRestAPI.deserialize(resp.open_!.asInstanceOf[PlainTextResponse].toResponse.data, classOf[StatusResponse]).getJobStatus == JobStatus.COMPLETE) {
            jobRunning = false
            Assert.assertTrue(DispatchRestAPI.deserialize(resp.open_!.asInstanceOf[PlainTextResponse].toResponse.data, classOf[StatusResponse]).getJobStatus == JobStatus.COMPLETE)
          })
      }
    }

  }

  @Test(dependsOnMethods = Array("canCompleteJob"))
  def canGetOutputDownload() {
    val mockReq = new MockReadAuthReq("/jobs/" + testJobID + "/result")

    mockReq.headers += ("Accept" -> List("application/pdf"))

    MockWeb.testReq(mockReq) { req =>
      val respBox = DispatchRestAPI(req)()
      Assert.assertTrue(respBox.isDefined)
      Assert.assertTrue(respBox.open_!.isInstanceOf[StreamingResponse])

      val resultResp = respBox.open_!.asInstanceOf[StreamingResponse]
      Assert.assertEquals(resultResp.code, 200)
      Assert.assertTrue(resultResp.headers.contains(("Content-Type", "application/pdf")) && resultResp.headers.contains(("Content-Disposition", "attachment; filename=\"REPORT_" + testJobID + ".pdf\"")))
    }
  }

  @Test(dependsOnMethods = Array("canCompleteJobHTML"))
  def canGetHTMLOutputDownload() {
    val mockReq = new MockReadAuthReq("/jobs/" + testJobHTMLID + "/result")

    mockReq.headers += ("Accept" -> List("application/html"))

    MockWeb.testReq(mockReq) { req =>
      val respBox = DispatchRestAPI(req)()
      Assert.assertTrue(respBox.isDefined, "Response is not defined")
      Assert.assertTrue(respBox.open_!.isInstanceOf[StreamingResponse], "Response is not of type StreamingResponse")

      val resultResp = respBox.open_!.asInstanceOf[StreamingResponse]
      Assert.assertEquals(resultResp.code, 200, "Status code is not 200")
      Assert.assertTrue(resultResp.headers.contains(("Content-Type", "application/html")) && resultResp.headers.contains(("Content-Disposition", "attachment; filename=\"REPORT_" + testJobHTMLID + ".html\"")))
    }
  }

  @Test(dependsOnMethods = Array("canCompleteJobCSV"))
  def canGetCSVOutputDownload() {
    val mockReq = new MockReadAuthReq("/jobs/" + testJobCSVID + "/result")

    mockReq.headers += ("Accept" -> List("application/csv"))

    MockWeb.testReq(mockReq) { req =>
      val respBox = DispatchRestAPI(req)()
      Assert.assertTrue(respBox.isDefined)
      Assert.assertTrue(respBox.open_!.isInstanceOf[StreamingResponse])

      val resultResp = respBox.open_!.asInstanceOf[StreamingResponse]
      Assert.assertEquals(resultResp.code, 200)
      Assert.assertTrue(resultResp.headers.contains(("Content-Type", "application/csv")) && resultResp.headers.contains(("Content-Disposition", "attachment; filename=\"REPORT_" + testJobCSVID + ".csv\"")))
    }
  }

  @Test(dependsOnMethods = Array("canCompleteJob"))
  def cantGetPDFOutputDownloadWithCSVAcceptHeader() {
    val mockReq = new MockReadAuthReq("/jobs/" + testJobID + "/result")
    mockReq.headers += ("Accept" -> List("application/csv"))

    MockWeb.testReq(mockReq) { req =>
      val resp = DispatchRestAPI(req)()
      Assert.assertTrue(resp.isDefined)
      Assert.assertEquals(resp.open_!.toResponse.code, 406)
    }
  }

  @Test(dependsOnMethods = Array("canCompleteJob"))
  def cantGetPDFOutputDownloadWithHTMLAcceptHeader() {
    val mockReq = new MockReadAuthReq("/jobs/" + testJobID + "/result")
    mockReq.headers += ("Accept" -> List("application/html"))

    MockWeb.testReq(mockReq) { req =>
      val resp = DispatchRestAPI(req)()
      Assert.assertTrue(resp.isDefined)
      Assert.assertEquals(resp.open_!.toResponse.code, 406)
    }
  }

  @Test(dependsOnMethods = Array("canCompleteJob"))
  def canGetJobStatus() {
    val mockReq = new MockReadAuthReq("/jobs/" + testJobID + "/status")

    mockReq.headers += ("Accept" -> List(ModelObject.TYPE_FULL))

    MockWeb.testReq(mockReq) { req =>
      val resp = DispatchRestAPI(req)()
      Assert.assertTrue(resp.isDefined)
      Assert.assertTrue(resp.open_!.isInstanceOf[PlainTextResponse])
      Assert.assertEquals(resp.open_!.toResponse.code, 200)
      val statusResponse: StatusResponse = DispatchRestAPI.deserialize(resp.open_!.asInstanceOf[PlainTextResponse].toResponse.data, classOf[StatusResponse])
      Assert.assertEquals(statusResponse.getJobStatus, JobStatus.COMPLETE)

    }
  }

  @Test(dependsOnMethods = Array("canPostJob"))
  def jobStatusServiceReturnsJSON() {
    val mockReq = new MockReadAuthReq("/jobs/" + testJobID + "/status")
    mockReq.headers += ("Accept" -> List(ModelObject.TYPE_FULL))
    MockWeb.testReq(mockReq) { req =>
      val resp = DispatchRestAPI(req)()
      Assert.assertTrue(resp.isDefined)
      Assert.assertTrue(resp.open_!.toResponse.headers.contains(("Content-Type", "application/vnd.ksmpartners.ernie+json")))
    }
  }

  @Test(dependsOnMethods = Array("canGetHTMLOutputDownload"))
  def deleteReportResultsReturnsJSON() {
    val mockReq = new MockWriteAuthReq("/jobs/" + testJobHTMLID + "/result")
    mockReq.method = "DELETE"
    mockReq.headers += ("Accept" -> List(ModelObject.TYPE_FULL))
    MockWeb.testReq(mockReq) { req =>
      val resp = DispatchRestAPI(req)()
      Assert.assertTrue(resp.isDefined)
      Assert.assertTrue(resp.open_!.toResponse.headers.contains(("Content-Type", "application/vnd.ksmpartners.ernie+json")))
    }
  }

  @Test
  def jobStatusReportsFailureForUnsupportedOutput() {
    var mockReq: MockHttpServletRequest = new MockWriteAuthReq("/jobs")
    mockReq.method = "POST"
    mockReq.headers += ("Accept" -> List(ModelObject.TYPE_FULL))

    val mockReportReq = new ReportRequest()
    mockReportReq.setDefId("test_def_nocsv")
    mockReportReq.setRptType(ReportType.CSV)
    mockReq.body = DispatchRestAPI.serialize(mockReportReq).getBytes
    var noCSVJob = -1L
    MockWeb.testReq(mockReq) { req =>
      val resp = DispatchRestAPI(req)()
      Assert.assertTrue(resp.isDefined)
      Assert.assertTrue(resp.open_!.isInstanceOf[PlainTextResponse])
      val reportResponse: ReportResponse = DispatchRestAPI.deserialize(resp.open_!.asInstanceOf[PlainTextResponse].toResponse.data, classOf[ReportResponse])
      noCSVJob = reportResponse.getJobId()
      Assert.assertEquals(resp.open_!.toResponse.code, 201)
    }
    mockReq = new MockReadAuthReq("/jobs/" + noCSVJob + "/status")

    mockReq.headers += ("Accept" -> List(ModelObject.TYPE_FULL))

    MockWeb.testReq(mockReq) { req =>
      val resp = DispatchRestAPI(req)()
      Assert.assertTrue(resp.isDefined)
      Assert.assertTrue(resp.open_!.isInstanceOf[PlainTextResponse])
      Assert.assertEquals(resp.open_!.toResponse.code, 200)
      val statusResponse: StatusResponse = DispatchRestAPI.deserialize(resp.open_!.asInstanceOf[PlainTextResponse].toResponse.data, classOf[StatusResponse])
      Assert.assertEquals(statusResponse.getJobStatus, JobStatus.FAILED_UNSUPPORTED_FORMAT)
    }
  }

  @Test
  def canPostJobWithParams() {
    val mockReq = new MockWriteAuthReq("/jobs")
    mockReq.method = "POST"
    mockReq.headers += ("Accept" -> List(ModelObject.TYPE_FULL))

    val mockReportReq = new ReportRequest()
    mockReportReq.setDefId(testDef)
    mockReportReq.setRptType(ReportType.PDF)
    val rptParams: java.util.HashMap[String, String] = new java.util.HashMap[String, String]()
    rptParams.put("MinQuantityInStock", "500")
    mockReportReq.setReportParameters(rptParams)
    mockReq.body = DispatchRestAPI.serialize(mockReportReq).getBytes

    MockWeb.testReq(mockReq) { req =>
      val resp = DispatchRestAPI(req)()

      Assert.assertTrue(resp.isDefined)
      Assert.assertTrue(resp.open_!.isInstanceOf[PlainTextResponse])
      Assert.assertEquals(resp.open_!.toResponse.code, 201)
      val reportResponse: ReportResponse = DispatchRestAPI.deserialize(resp.open_!.asInstanceOf[PlainTextResponse].toResponse.data, classOf[ReportResponse])
      val testParamsJobID = reportResponse.getJobId()
      Assert.assertTrue(testParamsJobID > -1L)
      Assert.assertTrue(resp.open_!.toResponse.headers.contains(("Location", req.hostAndPath + "/jobs/" + testParamsJobID)))
    }
  }

  @Test(dependsOnMethods = Array("canCompleteJob"))
  def canGetReportDetail() {
    val mockReq = new MockReadAuthReq("/jobs/" + testJobID + "/result/detail")

    mockReq.headers += ("Accept" -> List(ModelObject.TYPE_FULL))

    MockWeb.testReq(mockReq) { req =>
      val resp = DispatchRestAPI(req)()
      Assert.assertTrue(resp.isDefined)
      Assert.assertTrue(resp.open_!.isInstanceOf[PlainTextResponse])
      Assert.assertEquals(resp.open_!.toResponse.code, 200)
      val rptDetailResponse: ReportEntity = DispatchRestAPI.deserialize(resp.open_!.asInstanceOf[PlainTextResponse].toResponse.data, classOf[ReportEntity])
      Assert.assertEquals(rptDetailResponse.getRptId, jobToRptId(testJobID))
      Assert.assertEquals(rptDetailResponse.getCreatedUser, "mockRunUser")
    }
  }

  @Test(dependsOnMethods = Array("canCompleteJob"))
  def canGetJobDetail() {
    val mockReq = new MockReadAuthReq("/jobs/" + testJobID)

    mockReq.headers += ("Accept" -> List(ModelObject.TYPE_FULL))

    MockWeb.testReq(mockReq) { req =>
      val resp = DispatchRestAPI(req)()
      Assert.assertTrue(resp.isDefined)
      Assert.assertTrue(resp.open_!.isInstanceOf[PlainTextResponse])
      Assert.assertEquals(resp.open_!.toResponse.code, 200)
      val jobDetailResponse: JobEntity = DispatchRestAPI.deserialize(resp.open_!.asInstanceOf[PlainTextResponse].toResponse.data, classOf[JobEntity])
      Assert.assertEquals(jobDetailResponse.getJobStatus, JobStatus.COMPLETE)
    }
  }

  @Test(dependsOnMethods = Array("canGetCSVOutputDownload"))
  def canGetJobsCatalog() {
    val mockReq = new MockReadAuthReq("/jobs/catalog")

    mockReq.headers += ("Accept" -> List(ModelObject.TYPE_FULL))

    MockWeb.testReq(mockReq) { req =>
      val resp = DispatchRestAPI(req)()
      Assert.assertTrue(resp.isDefined)
      Assert.assertTrue(resp.open_!.isInstanceOf[PlainTextResponse])
      Assert.assertEquals(resp.open_!.toResponse.code, 200)
      val jobCatalogResp: JobsCatalogResponse = DispatchRestAPI.deserialize(resp.open_!.asInstanceOf[PlainTextResponse].toResponse.data, classOf[JobsCatalogResponse])
      Assert.assertTrue(jobCatalogResp.getJobsCatalog.size > 0)
    }
  }

  @Test(dependsOnMethods = Array("canGetCSVOutputDownload"))
  def canGetCompleteJobsCatalog() {
    val mockReq = new MockReadAuthReq("/jobs/complete")

    mockReq.headers += ("Accept" -> List(ModelObject.TYPE_FULL))

    MockWeb.testReq(mockReq) { req =>
      val resp = DispatchRestAPI(req)()
      Assert.assertTrue(resp.isDefined)
      Assert.assertTrue(resp.open_!.isInstanceOf[PlainTextResponse])
      Assert.assertEquals(resp.open_!.toResponse.code, 200)
      val jobCatalogResp: JobsCatalogResponse = DispatchRestAPI.deserialize(resp.open_!.asInstanceOf[PlainTextResponse].toResponse.data, classOf[JobsCatalogResponse])
      Assert.assertTrue(jobCatalogResp.getJobsCatalog.size > 0)
    }
  }

  @Test(dependsOnMethods = Array("canGetCSVOutputDownload"))
  def canGetExpiredJobsCatalog() {
    val mockReq = new MockReadAuthReq("/jobs/expired")

    mockReq.headers += ("Accept" -> List(ModelObject.TYPE_FULL))

    MockWeb.testReq(mockReq) { req =>
      val resp = DispatchRestAPI(req)()
      Assert.assertTrue(resp.isDefined)
      Assert.assertTrue(resp.open_!.isInstanceOf[PlainTextResponse])
      Assert.assertEquals(resp.open_!.toResponse.code, 200)
      val jobCatalogResp: JobsCatalogResponse = DispatchRestAPI.deserialize(resp.open_!.asInstanceOf[PlainTextResponse].toResponse.data, classOf[JobsCatalogResponse])
      Assert.assertTrue(jobCatalogResp.getJobsCatalog != null)
    }
  }

  @Test(dependsOnMethods = Array("canGetCSVOutputDownload"))
  def canGetFailedJobsCatalog() {
    val mockReq = new MockReadAuthReq("/jobs/failed")

    mockReq.headers += ("Accept" -> List(ModelObject.TYPE_FULL))

    MockWeb.testReq(mockReq) { req =>
      val resp = DispatchRestAPI(req)()
      Assert.assertTrue(resp.isDefined)
      Assert.assertTrue(resp.open_!.isInstanceOf[PlainTextResponse])
      Assert.assertEquals(resp.open_!.toResponse.code, 200)
    }
  }

}