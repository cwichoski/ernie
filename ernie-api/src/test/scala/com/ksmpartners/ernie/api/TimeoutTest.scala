/**
 * This source code file is the intellectual property of KSM Technology Partners LLC.
 * The contents of this file may not be reproduced, published, or distributed in any
 * form, except as allowed in a license agreement between KSM Technology Partners LLC
 * and a licensee. Copyright 2012 KSM Technology Partners LLC.  All rights reserved.
 *
 */

package com.ksmpartners.ernie.api

import com.ksmpartners.ernie.util.TestLogger
import com.ksmpartners.ernie.api.service.{ RequiresReportManager, RequiresCoordinator, DefinitionDependencies }
import scala.concurrent.duration._
import com.ksmpartners.ernie.util.Utility._
import org.testng.annotations._
import org.testng.Assert
import java.io.{ ByteArrayInputStream, File }
import com.ksmpartners.ernie.model.{ DefinitionEntity, DeleteStatus, ReportType }
import org.slf4j.{ LoggerFactory, Logger }
import com.ksmpartners.ernie.engine._
import com.ksmpartners.ernie.engine.report.BirtReportGeneratorFactory
import akka.actor.{ ActorSystem, ActorDSL }
import akka.pattern.AskTimeoutException
import java.util.concurrent.TimeoutException
import com.ksmpartners.ernie.api.ErnieBuilder._

class TestCoordinator extends ErnieCoordinator {

  override def receive() = {
    case _ =>
  }

}

@Test(groups = Array("timeout"))
class TimeoutTest {

  private val ernie = {
    val e = ErnieEngine(ernieBuilder withMemoryReportManager () timeoutAfter (1 nanosecond) build ()).start
    e.setCoordinator(ActorDSL.actor(ActorSystem("timeout-test-system"))(new TestCoordinator))
    e
  }

  private val log: Logger = LoggerFactory.getLogger("com.ksmpartners.ernie.api.APITest")

  @BeforeClass
  def startup() {
  }

  @AfterClass
  def shutdown() {

  }

  private var defId = ""
  private var jobId = -1L

  @Test
  def checkTimeout() {
    try {
      ernie.getJobStatus(5L)
      Assert.assertTrue(false)
    } catch {
      case t: Throwable => Assert.assertEquals(t.getClass, classOf[TimeoutException])
    }
  }

  /*

  @Test(groups = Array("tTSetup"))
  def createJob() {
    val resp = ernie.createJob("test", ReportType.PDF, None, null, "test")
    checkTimeout(resp.error)
  }

  @Test(dependsOnGroups = Array("tTSetup"), groups = Array("tTMain"))
  def getStatus(): Option[com.ksmpartners.ernie.model.JobStatus] = {
    checkTimeout(ernie.getJobStatus(1L).error)
    None
  }

  @Test(dependsOnGroups = Array("tTSetup"), groups = Array("tTMain"))
  def getJobEntity() {
    checkTimeout(ernie.getJobEntity(1L).error)
  }

  @Test(dependsOnGroups = Array("tTSetup"), groups = Array("tTMain"))
  def getJobCatalog() {
    checkTimeout(ernie.getJobCatalog(None)._2)
  }

  @Test(dependsOnGroups = Array("tTSetup"), groups = Array("tTMain"))
  def getJobList() {
    checkTimeout(ernie.getJobList._2)
  }

  @Test(dependsOnGroups = Array("tTMain"), groups = Array("tTCompleted"))
  def getRptEnt() {
    checkTimeout(ernie.getReportEntity(1L).error)
  }

  @Test(dependsOnGroups = Array("tTMain"), groups = Array("tTCompleted"))
  def getOutputStream() {
    checkTimeout(ernie.getReportOutput(1L).error)
  }

  @Test(dependsOnGroups = Array("tTMain"), groups = Array("tTCompleted"))
  def getOutput() {
    checkTimeout(ernie.getReportOutput(1L).error)
  }

  @Test(dependsOnGroups = Array("tTCompleted"), groups = Array("tTCleanUp"))
  def deleteOutput() {
    checkTimeout(ernie.deleteReportOutput(1L)._2)
  }

  @Test(dependsOnGroups = Array("tTCompleted"), groups = Array("tTCleanUp"))
  def deleteDef() {
    checkTimeout(ernie.deleteDefinition("test")._2)
  }

  @Test(dependsOnGroups = Array("tTCompleted"), groups = Array("tTCleanUp"))
  def purge() {
    checkTimeout(ernie.purgeExpiredReports().error)
  }        */

}
