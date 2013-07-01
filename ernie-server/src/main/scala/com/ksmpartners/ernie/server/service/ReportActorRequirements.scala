/**
 * This source code file is the intellectual property of KSM Technology Partners LLC.
 * The contents of this file may not be reproduced, published, or distributed in any
 * form, except as allowed in a license agreement between KSM Technology Partners LLC
 * and a licensee. Copyright 2012 KSM Technology Partners LLC.  All rights reserved.
 */

package com.ksmpartners.ernie.server.service

import com.ksmpartners.ernie.engine.Coordinator
import com.ksmpartners.ernie.engine.report.ReportManager
import com.ksmpartners.ernie.api.ErnieControl

/**
 * Trait that indicates a requirement on a Coordinator
 */
trait RequiresAPI {
  protected val ernie: ErnieControl
}

