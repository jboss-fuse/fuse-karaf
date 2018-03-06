/**
 *  Copyright 2005-2017 Red Hat, Inc.
 *
 *  Red Hat licenses this file to you under the Apache License, version
 *  2.0 (the "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied.  See the License for the specific language governing
 *  permissions and limitations under the License.
 */
package org.apache.camel.salesforce.dto;

import java.util.List;

import com.thoughtworks.xstream.annotations.XStreamImplicit;
import org.apache.camel.component.salesforce.api.dto.AbstractQueryRecordsBase;

/**
 * Salesforce QueryRecords DTO for type Cheese__c
 */
public class QueryRecordsCheese__c extends AbstractQueryRecordsBase {

    @XStreamImplicit
    private List<Cheese__c> records;

    public List<Cheese__c> getRecords() {
        return records;
    }

    public void setRecords(List<Cheese__c> records) {
        this.records = records;
    }
}
