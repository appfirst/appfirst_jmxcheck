/*
 * Copyright 2009-2011 AppFirst, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.appfirst;
import java.util.HashMap;

import javax.management.openmbean.CompositeDataSupport;

/**
 * @author Bin Liu
 * Mapping a JMXQuery result with a nagios parameter. 
 */
public class AFJMXQueryResult {
	public static final int RETURN_OK = 0; // The plugin was able to check the
	// service and it appeared to be
	// functioning properly
	public static final String OK_STRING = "JMX OK";
	public static final int RETURN_WARNING = 1; // The plugin was able to check
	// the service, but it
	// appeared to be above some
	// "warning" threshold or
	// did not appear to be
	// working properly
	public static final String WARNING_STRING = "JMX WARNING";
	public static final int RETURN_CRITICAL = 2; // The plugin detected that
	// either the service was
	// not running or it was
	// above some "critical"
	// threshold
	public static final String CRITICAL_STRING = "JMX CRITICAL";
	public static final int RETURN_UNKNOWN = 3; // Invalid command line
	// arguments were supplied
	// to the plugin or
	// low-level failures
	// internal to the plugin
	// (such as unable to fork,
	// or open a tcp socket)
	// that prevent it from
	// performing the specified
	// operation. Higher-level
	// errors (such as name
	// resolution errors, socket
	// timeouts, etc) are
	// outside of the control of
	// plugins and should
	// generally NOT be reported
	// as UNKNOWN states.
	public static final String UNKNOWN_STRING = "JMX UNKNOWN";
	private Object detailStatus;
	private Object statusData;
	private AFJMXQuery originalQuery;
	private String statusString = UNKNOWN_STRING;
	private Double statusValue;
	private Double currentStatusValue;
	private Double previousStatusValue = 0.0;
	private String stringValue;
	
	
	public AFJMXQuery getOriginalQuery() {
		return originalQuery;
	}
	
	public Double getPreviousStatusValue() {
		return previousStatusValue;
	}
	
	public Double getStatusValue() {
		return statusValue;
	}

	public void setPreviousStatusValue(HashMap<String, Double> valueMap) {
		if (valueMap.containsKey(this.originalQuery.getName())) {
			previousStatusValue = valueMap.get(this.originalQuery.getName());
			this.recalculateStatusValue();
		}
	}

	private int status = 3;
	
	public int getStatus() {
		return status;
	}

	public void setStatus(int status) {
		this.status = status;
	}

	public Object getDetailStatus() {
		return detailStatus;
	}

	public void setDetailStatus(Object detailStatus) {
		this.detailStatus = detailStatus;
	}

	public Object getStatusData() {
		return statusData;
	}
	
	public Double getCurrentStatusValue() {
		return currentStatusValue;
	}
	
	/**
	 * Status can be OK, Warning or Critical. 
	 */
	private void setStatusString() {
		if (statusValue == null) {
			this.statusString = AFJMXQueryResult.OK_STRING;
			this.status = 0;
		}
		else if (statusValue < this.originalQuery.getWarningThreshold().longValue()) {
			this.statusString = AFJMXQueryResult.OK_STRING;
			this.status = 0;
		} else if (statusValue < this.originalQuery.getCriticalThreshold().longValue()){
			this.statusString = AFJMXQueryResult.WARNING_STRING;
			this.status = 1;
		} else {
			this.statusString = AFJMXQueryResult.CRITICAL_STRING;
			this.status = 2;
		}
	}

	/**
	 * Parsing status data basing on the JMXQuery parameter. 
	 * @param data status data in object form.  
	 */
	public void setStatusData(Object data) {
		this.statusData = data;
		if (statusData instanceof CompositeDataSupport) {
			CompositeDataSupport cds = (CompositeDataSupport) statusData;
			if (this.originalQuery.getAttribute() == null) {
				statusValue = -1.0; // no attribute key
			} else {
				statusValue = Double.parseDouble(cds.get(
						this.originalQuery.getAttributeKey()).toString());
			}
		} else if (statusData instanceof String) {
		}
		else {
			statusValue = Double.parseDouble(statusData.toString());
		}
		currentStatusValue = statusValue;
		setStatusString();
	}
	
	/**
	 * Recalculate status value based on its previous value. 
	 */
	private void recalculateStatusValue() {
		if (this.originalQuery.getValueType() == AFJMXQuery.cumulativeValueType) {
			statusValue = currentStatusValue - previousStatusValue;
			if (statusValue < 0) {
				statusValue = 0.0;
			}
			previousStatusValue = currentStatusValue;
		}
	}

	/**
	 * Constructor. 
	 * @param query a JMXQuery object. 
	 */
	public AFJMXQueryResult(AFJMXQuery query) {
		originalQuery = query;
	}
	
	
	/**
	 * Export to name value mapping, without status string. 
	 * @return status string. 
	 */
	public String toString() {
		String ret = "";
		if (statusValue != null) {
			ret = String.format("%s=%f", this.originalQuery.getName(),
					this.statusValue);
		} else {
			ret = String.format("%s=%s", this.originalQuery.getName(),
					statusData);
			
		}
		return ret;
	}
	
	/**
	 * To cache data format. 
	 * @return cache data string. 
	 */
	public String toCacheString() {
		if (this.originalQuery.getValueType() == AFJMXQuery.cumulativeValueType) {
			return String.format("%s %f\n", this.originalQuery.getName(), this.currentStatusValue);
		} else {
			return "";
		}		
	}
}
