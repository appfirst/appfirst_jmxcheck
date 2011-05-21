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

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

/**
 * @author Bin Liu
 * 
 * The JMXQuery object including BeanName, Attribute Name etc. 
 */
public class AFJMXQuery {
	private Double warningThreshold = Double.MAX_VALUE;
	private Double criticalThreshold = Double.MAX_VALUE;
	private String attribute;
	private String infoAttribute;
	private String attributeKey;
	private String infoKey;
	private int verbatim;
	private String beanName;
	private String valueType = "current";
	public static String defaultValueType = "current";
	public static String cumulativeValueType = "cumulative";

	public String getName() {
		String ret = String.format("%s.%s", beanName, attribute);
		if (attributeKey != null) {
			ret = String.format("%s.%s", ret, attributeKey);
		}
		return ret;
	}

	public Double getWarningThreshold() {
		return warningThreshold;
	}

	public void setWarningThreshold(Double warningThreshold) {
		this.warningThreshold = warningThreshold;
	}

	public Double getCriticalThreshold() {
		return criticalThreshold;
	}

	public void setCriticalThreshold(Double criticalThreshold) {
		this.criticalThreshold = criticalThreshold;
	}

	public String getAttribute() {
		return attribute;
	}

	public void setAttribute(String attribute) {
		this.attribute = attribute;
	}

	public String getInfoAttribute() {
		return infoAttribute;
	}

	public void setInfoAttribute(String infoAttribute) {
		this.infoAttribute = infoAttribute;
	}

	public String getAttributeKey() {
		return attributeKey;
	}

	public void setAttributeKey(String attributeKey) {
		this.attributeKey = attributeKey;
	}

	public String getInfoKey() {
		return infoKey;
	}

	public void setInfoKey(String infoKey) {
		this.infoKey = infoKey;
	}

	public int getVerbatim() {
		return verbatim;
	}

	public void setVerbatim(int verbatim) {
		this.verbatim = verbatim;
	}

	public String getBeanName() {
		return beanName;
	}

	public void setBeanName(String beanName) {
		this.beanName = beanName;
	}

	public String getValueType() {
		return valueType;
	}

	public void setValueType(int valueType) {
		if (valueType > 0) {
			this.valueType = cumulativeValueType;
		} else if (valueType == 0) {
			this.valueType = defaultValueType;
		}
	}

	/**
	 * Get the JMX attribute from the connection. 
	 * @param connection JMX connection. 
	 * @return a {@link AFJMXQueryResult} 
	 * @throws Exception when couldn't get data
	 */
	public AFJMXQueryResult getAttribute(MBeanServerConnection connection)
			throws Exception {
		AFJMXQueryResult result = new AFJMXQueryResult(this);
		Object attr = connection.getAttribute(new ObjectName(this.beanName),
				this.attribute);
		result.setStatusData(attr);
		if (this.infoAttribute != null) {
			Object info_attr = this.infoAttribute.equals(attribute) ? attr
					: connection.getAttribute(new ObjectName(this.beanName),
							this.infoAttribute);
			result.setDetailStatus(info_attr);
		}
		return result;
	}
}
