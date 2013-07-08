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

package com.ksmpartners.ernie.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.ksmpartners.ernie.util.ISODateDeserializer;
import com.ksmpartners.ernie.util.ISODateSerializer;
import org.joda.time.DateTime;

import java.util.List;

/**
 * A JSONable class used to serialize definition metadata
 */
public class DefinitionEntity extends ModelObject {

    private DateTime createdDate;
    private String defId;
    private String createdUser;
    private List<String> paramNames;
    private List<ParameterEntity> params;
    private String defDescription;
    private List<ReportType> unsupportedReportTypes;

    public DefinitionEntity() {}

    public DefinitionEntity(DateTime createdDate, String defId, String createdUser, List<String> paramNames, String defDescription, List<ReportType> unsupportedReportTypes, List<ParameterEntity> params) {
        this.createdDate = createdDate;
        this.defId = defId;
        this.createdUser = createdUser;
        this.paramNames = paramNames;
        this.defDescription = defDescription;
        this.unsupportedReportTypes = unsupportedReportTypes;
        this.params = params;
    }

    @JsonSerialize(using = ISODateSerializer.class)
    public DateTime getCreatedDate() {
        return createdDate;
    }

    @JsonDeserialize(using = ISODateDeserializer.class)
    public void setCreatedDate(DateTime createdDate) {
        this.createdDate = createdDate;
    }

    public String getDefId() {
        return defId;
    }

    public void setDefId(String defId) {
        this.defId = defId;
    }

    public String getCreatedUser() {
        return createdUser;
    }

    public void setCreatedUser(String createdUser) {
        this.createdUser = createdUser;
    }

    public List<String> getParamNames() {
        return paramNames;
    }

    public void setParamNames(List<String> paramNames) {
        this.paramNames = paramNames;
    }

    public String getDefDescription() {
        return defDescription;
    }

    public void setDefDescription(String defDescription) {
        this.defDescription = defDescription;
    }

    public List<ReportType> getUnsupportedReportTypes() {
        return unsupportedReportTypes;
    }

    public void setUnsupportedReportTypes(List<ReportType> unsupportedReportTypes) {
        this.unsupportedReportTypes = unsupportedReportTypes;
    }

    public List<ParameterEntity> getParams() {
        return params;
    }

    public void setParams(List<ParameterEntity> params) {
        this.params = params;
    }
}
