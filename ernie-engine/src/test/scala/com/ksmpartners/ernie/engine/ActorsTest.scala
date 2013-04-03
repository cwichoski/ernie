/**
 * This source code file is the intellectual property of KSM Technology Partners LLC.
 * The contents of this file may not be reproduced, published, or distributed in any
 * form, except as allowed in a license agreement between KSM Technology Partners LLC
 * and a licensee. Copyright 2012 KSM Technology Partners LLC.  All rights reserved.
 */

package com.ksmpartners.ernie.engine

import org.testng.annotations.{ AfterClass, BeforeClass, Test }
import java.io.{ FileInputStream, File, IOException, Closeable }
import report.{ MemoryReportManager, ReportGenerator }
import java.net.URL
import org.testng.Assert
import com.ksmpartners.ernie.model.{ ReportType, JobStatus }

class ActorsTest {

  private var reportManager: MemoryReportManager = null
  private var coordinator: Coordinator = null

  // TODO: Fix error when calling Platform.startup() twice
  //  @BeforeClass
  def setup() {
    reportManager = new MemoryReportManager
    val url: URL = Thread.currentThread().getContextClassLoader().getResource("test_def.rptdesign")
    val file = new File(url.getPath)
    var fis: FileInputStream = null
    try {
      fis = new FileInputStream(file)
      val byteArr = new Array[Byte](file.length().asInstanceOf[Int])
      fis.read(byteArr)
      reportManager.putDefinition("test_def", byteArr)
      coordinator = new Coordinator(reportManager)
      coordinator.start()
    } finally {
      try { fis.close() } catch { case e => }
    }
  }

  //  @AfterClass
  def shutdown() {
    val sResp = (coordinator !? ShutDownRequest()).asInstanceOf[ShutDownResponse]
  }

  //  @Test
  def canRequestReport() {
    val resp = (coordinator !? ReportRequest("test_def", ReportType.PDF)).asInstanceOf[ReportResponse]
    val statusResp = (coordinator !? StatusRequest(resp.jobId)).asInstanceOf[StatusResponse]
    Assert.assertNotSame(statusResp.jobStatus, JobStatus.NO_SUCH_JOB)
  }

  //  @Test
  def canRequestStatus() {
    val statusResp = (coordinator !? StatusRequest(0)).asInstanceOf[StatusResponse]
    Assert.assertEquals(statusResp.jobStatus, JobStatus.NO_SUCH_JOB)
  }

  //  @Test
  def canRequestJobMap() {
    val resp = (coordinator !? ReportRequest("test_def", ReportType.PDF)).asInstanceOf[ReportResponse]
    val jobMapResp = (coordinator !? JobsListRequest()).asInstanceOf[JobsListResponse]
    Assert.assertTrue(jobMapResp.jobsList.contains(resp.jobId.toString))
  }
}
