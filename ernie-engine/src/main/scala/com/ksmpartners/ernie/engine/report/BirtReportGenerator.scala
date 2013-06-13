/**
 * This source code file is the intellectual property of KSM Technology Partners LLC.
 * The contents of this file may not be reproduced, published, or distributed in any
 * form, except as allowed in a license agreement between KSM Technology Partners LLC
 * and a licensee. Copyright 2012 KSM Technology Partners LLC.  All rights reserved.
 */

package com.ksmpartners.ernie.engine.report

import org.eclipse.birt.report.engine.api._
import org.eclipse.birt.core.framework.Platform
import org.slf4j.LoggerFactory
import java.io._
import com.ksmpartners.ernie.model.{ ParameterEntity, DefinitionEntity, ReportType }
import com.ksmpartners.ernie.engine.report.BirtReportGenerator._
import org.eclipse.birt.report.engine.emitter.csv.CSVRenderOption
import org.eclipse.birt.report.engine.api.IParameterDefn._
import com.ksmpartners.ernie.util.Utility._
import scala.collection._
import JavaConversions._
import org.joda.time.DateTime
import java.util.Date
import java.text.DecimalFormat
import java.sql.Time
import org.joda.time.format.{ DateTimeFormat, DateTimeFormatter }

/**
 * Class used to generate BIRT reports
 * <br><br>
 * This Class is not thread safe.
 */
class BirtReportGenerator(reportManager: ReportManager) extends ReportGenerator {

  def startup() { startEngine() }

  /**
   * Get the list of available definitions
   */
  def getAvailableRptDefs: List[String] = reportManager.getAllDefinitionIds

  /**
   * Method that runs the design file at the given location defId, and outputs the results to rptId
   * as a rptType
   */
  def runReport(defId: String, rptId: String, rptType: ReportType, retentionDate: Option[Int], userName: String): Unit = runReport(defId, rptId, rptType, retentionDate, Map.empty[String, String], userName)
  def runReport(defId: String, rptId: String, rptType: ReportType, retentionDate: Option[Int], reportParameters: Map[String, String], userName: String) {
    if (engine == null) throw new IllegalStateException("ReportGenerator was not started")
    log.debug("Generating report from definition {}", defId)
    try_(reportManager.getDefinitionContent(defId).get) { defInputStream =>
      {
        val entity: mutable.Map[String, Any] = new mutable.HashMap()
        entity += (ReportManager.rptId -> rptId)
        entity += (ReportManager.sourceDefId -> defId)
        entity += (ReportManager.reportType -> rptType)
        entity += (ReportManager.createdUser -> userName)
        entity += (ReportManager.retentionDate -> DateTime.now().plusDays(retentionDate getOrElse (reportManager.getDefaultRetentionDays)))

        var rptParams: Map[String, Any] = reportParameters
        //Ensure all parameter values are supported by the definition
        if (reportManager.getDefinition(defId).get.getEntity.getParams != null)
          reportParameters.foreach(param => reportManager.getDefinition(defId).get.getEntity.getParams.toList.find(p => p.getParamName == param._1) match {
            case Some(paramEntity) => rptParams += (paramEntity.getParamName -> stringToBirtParamData(param._2, paramEntity))
          })

        val entParams = reportManager.getDefinition(defId).get.getEntity.getParams

        if (entParams != null) {
          entParams.toList.foreach(param =>
            if (!rptParams.contains(param.getParamName)) {
              rptParams += (param.getParamName -> stringToBirtParamData(param.getDefaultValue, param))
            })
        }

        entity += (ReportManager.paramMap -> reportParameters)
        entity += (ReportManager.startDate -> DateTime.now)
        try_(reportManager.putReport(entity)) { rptOutputStream =>
          runReport(defInputStream, rptOutputStream, rptType, rptParams)
        }
        entity += (ReportManager.finishDate -> DateTime.now)
        if (reportManager.getReport(rptId).isDefined) try { reportManager.updateReportEntity(entity) }
      }
    }
  }

  def stringToBirtParamData(data: String = null, param: ParameterEntity): Any = {
    if (((data == null) || (data == "")) && (!param.getAllowNull)) {
      throw new ParameterNullException(param.getParamName)
    } else try {
      param.getDataType match { //TODO: do not hardcode data type names. http://www.eclipse.org/birt/ref/rom/elements/ScalarParameter.html#Property-dataType
        case "boolean" => data.toBoolean
        case "date" => new java.sql.Date((DateTimeFormat.forPattern("yyyy-MM-dd").parseDateTime(data)).getMillis)
        case "dateTime" => new java.sql.Date(DateTime.parse(data).getMillis)
        case "decimal" => data.toDouble
        case "float" => data.toFloat
        case "integer" => data.toInt.asInstanceOf[Integer]
        case "string" => data
        case "time" => Time.valueOf(data)
        case "any" => data
        case _ => throw new UnsupportedDataTypeException(param.getParamName)
      }
    } catch {
      case e: UnsupportedDataTypeException => throw new UnsupportedDataTypeException(param.getParamName)
      case e: Exception => throw new ClassCastException()
    }
  }

  /**
   * Method that runs the .rtpdesign file in the input stream defInputStream, and outputs the results to
   * rptOutputStream as rptType
   */
  private def runReport(defInputStream: InputStream, rptOutputStream: OutputStream, rptType: ReportType): Unit = runReport(defInputStream, rptOutputStream, rptType, Map.empty[String, Any])
  private def runReport(defInputStream: InputStream, rptOutputStream: OutputStream, rptType: ReportType, rptParams: Map[String, Any]): Unit = {
    if (engine == null) throw new IllegalStateException("ReportGenerator was not started")
    val design = engine.openReportDesign(defInputStream)
    var renderOption: RenderOption = null
    rptType match {
      case ReportType.PDF => {
        renderOption = new PDFRenderOption
        renderOption.setOutputFormat("pdf")
      }
      case ReportType.CSV => {
        renderOption = new CSVRenderOption
        renderOption.setOutputFormat("csv")
      }
      case ReportType.HTML => {
        renderOption = new HTMLRenderOption
        renderOption.setOutputFormat("html")
      }
      case t => {
        log.error("Invalid report type: {}", t)
        throw new IllegalArgumentException("Invalid report type: " + t)
      }
    }
    renderOption.setOutputStream(rptOutputStream)
    BirtReportGenerator.runReport(design, renderOption, rptParams)
  }

  /**
   * Method to be called after all the reports have been run.
   */
  def shutdown() { shutdownEngine() }

}

class ParameterNullException(msg: String) extends Exception(msg);
class InvalidParameterValuesException(msg: String) extends Exception(msg);
class UnsupportedDataTypeException(msg: String) extends Exception(msg);

object BirtReportGenerator {

  protected[report] var engine: IReportEngine = null
  private val log = LoggerFactory.getLogger("c.k.e.e.report.BirtReportGenerator")

  /**
   * Method to be called before any reports can be generated
   */
  protected[report] def startEngine() {
    if (engine != null)
      return
    val ec = new EngineConfig
    Platform.startup(ec)

    val factory = Platform.createFactoryObject(IReportEngineFactory.EXTENSION_REPORT_ENGINE_FACTORY)
      .asInstanceOf[IReportEngineFactory]

    engine = factory.createReportEngine(ec)
    log.debug("BIRT Engine started.")
  }

  protected[report] def shutdownEngine() {
    if (engine == null)
      return
    engine.destroy()
    Platform.shutdown()
    engine = null
    log.debug("BIRT Engine shutdown.")
  }

  /**
   * Method that validates a report definition
   */
  def isValidDefinition(is: InputStream): Boolean = try {
    if (engine == null) {
      log.debug("Could not validate, engine not started.")
      return false
    }
    engine.openReportDesign(is)
    true
  } catch {
    case e: Exception =>
      log.debug("Caught exception while validating definition: {}", e.getMessage)
      false
    case e: Exception => {
      false
    }
  }

  /**
   * Method that creates and runs a BIRT task based on the given design and options
   */
  private def runReport(design: IReportRunnable, option: RenderOption, rptParams: Map[String, Any]) = synchronized {
    val task: IRunAndRenderTask = engine.createRunAndRenderTask(design)
    task.setRenderOption(option)
    task.setParameterValues(rptParams)
    rptParams.foreach(f => { task.setParameterValue(f._1, f._2); if (!task.validateParameters) throw new InvalidParameterValuesException(f._1) })

    task.run()
    task.close()
  }
}
