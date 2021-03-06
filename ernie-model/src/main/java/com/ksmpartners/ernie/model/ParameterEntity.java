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
import java.util.Date;
import java.util.List;

/**
 * A JSONable class used to serialize report definition parameter metadata
 */
public class ParameterEntity extends ModelObject {

    private String paramName;
    private String dataType;
    private Boolean allowNull;
    private String defaultValue;

    public ParameterEntity() {}

    public ParameterEntity(String paramName, String dataType, Boolean allowNull, String defaultValue) {
        this.paramName = paramName;
        this.dataType = dataType;
        this.allowNull = allowNull;
        this.defaultValue = defaultValue;
    }

    /**
     * Return the name of the parameter. For instance, testParameter below:
     * <parameters>
     *  <scalar-parameter name="testParameter">
     *  ...
     */
    public String getParamName() {
        return paramName;
    }

    /**
     * Set the name of the parameter.
     */
    public void setParamName(String paramName) {
        this.paramName = paramName;
    }

    /**
     * Return the default value for the parameter. For instance, 1000 below:
     *  <expression name="defaultValue">1000</expression>
     */
    public String getDefaultValue() {
        return defaultValue;
    }

    /**
     * Set the default parameter value.
     */
    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    /**
     * Return the data type for the parameter. For instance, "decimal" below:
     * <property name="dataType">decimal</property>
     */
    public String getDataType() {
        return dataType;
    }

    /**
     * Set the data type for the parameter.
     */
    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    /**
     * Return a boolean indicated if null values are allowed for the parameter.
     */
    public Boolean getAllowNull() {
        return allowNull;
    }

    /**
     * Set the boolean indicated if null values are allowed for the parameter.
     */
    public void setAllowNull(Boolean allowNull) {
        this.allowNull = allowNull;
    }


}
