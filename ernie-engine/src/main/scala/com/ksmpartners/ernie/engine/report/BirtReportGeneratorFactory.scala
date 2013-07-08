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
 *
 *
 */

package com.ksmpartners.ernie.engine.report

/**
 * Trait that contains factory methods for obtaining BirtReportGenerators
 */
trait BirtReportGeneratorFactory extends ReportGeneratorFactory {

  var rptGen: Option[BirtReportGenerator] = None

  /**
   * Return a new BirtReportGenerator with the given reportManager
   */
  def getReportGenerator(reportManager: ReportManager): ReportGenerator = rptGen getOrElse {
    rptGen = Some(new BirtReportGenerator(reportManager))
    rptGen.get
  }

}
