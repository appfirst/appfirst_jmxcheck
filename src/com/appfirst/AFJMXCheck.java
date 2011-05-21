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

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

/**
 * This class is used to connect JMX and getting the performance data defined by
 * user's command line.
 * 
 * @author Bin Liu
 * 
 */
public class AFJMXCheck {
	private JMXConnector connector;
	private MBeanServerConnection connection;
	private final String argumentSequenceSeparator = ",";
	private final String cacheFileName = "AFJMXCheckData";
	private HashMap<String, Long> cachedData = new HashMap<String, Long>();

	/**
	 * Connect to JMX
	 * 
	 * @param url
	 *            JMXService address
	 * @throws IOException
	 *             if connection failed.
	 */
	public void initConnection(String url) throws IOException {
		JMXServiceURL jmxUrl = new JMXServiceURL(url);
		connector = JMXConnectorFactory.connect(jmxUrl);
		connection = connector.getMBeanServerConnection();
	}

	/**
	 * Disconnect from JMX
	 * 
	 * @throws IOException
	 *             if couldn't disconnect
	 */
	public void disconnect() throws IOException {
		if (connector != null) {
			connector.close();
			connector = null;
		}
	}

	/**
	 * Read the cache data from previous running result.
	 */
	private void readCacheData() {
		try {
			BufferedReader input = new BufferedReader(new FileReader(
					cacheFileName));
			try {
				String line = null;
				while ((line = input.readLine()) != null) {
					String[] values = line.split(" ");
					if (values.length < 2) {
						continue;
					}
					cachedData.put(values[0], Long.parseLong(values[1]));
				}
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				input.close();
			}
		} catch (FileNotFoundException e) {
			// File not found, just ignore
		} catch (IOException e) {
			// permission issue?
			e.printStackTrace();
		}
	}

	/**
	 * Write current cache data into a flat file.
	 * 
	 * @param cacheString
	 *            a list of parameter_name value separated by new line charater.
	 */
	private void writeCacheData(String cacheString) {
		try {
			Writer out = new OutputStreamWriter(new FileOutputStream(
					this.cacheFileName), "UTF-8");
			try {
				out.write(cacheString);
			} finally {
				out.close();
			}
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
	}

	public void runCheck(String[] args) {
		int length = args.length;
		int start = 2;
		ArrayList<AFJMXQueryResult> resultList = new ArrayList<AFJMXQueryResult>();
		readCacheData();
		String cacheString = "";
		while (start < length) {
			int end = start;
			while (end < length && args[end] != argumentSequenceSeparator
					&& !args[end].endsWith(argumentSequenceSeparator)) {// get
				// to
				// the
				// sequence
				// ending.
				end++;
			}
			AFJMXQuery query = new AFJMXQuery();
			for (int i = start; i <= end - 1; i += 2) {// parse parameters
				String paramName = args[i];
				String paramValue = args[i + 1].replace(
						this.argumentSequenceSeparator, "");
				if (paramName.equals("-O")) {
					query.setBeanName(paramValue);
				} else if (paramName.equals("-A")) {
					query.setAttribute(paramValue);
				} else if (paramName.equals("-I")) {
					query.setInfoAttribute(paramValue);
				} else if (paramName.equals("-J")) {
					query.setInfoKey(paramValue);
				} else if (paramName.equals("-K")) {
					query.setAttributeKey(paramValue);
				} else if (paramName.startsWith("-v")) {
					query.setVerbatim(Integer.parseInt(paramValue));
				} else if (paramName.equals("-w")) {
					query.setWarningThreshold(Double.parseDouble(paramValue));
				} else if (paramName.equals("-c")) {
					query.setCriticalThreshold(Double.parseDouble(paramValue));
				} else if (paramName.equals("-T")) {
					query.setValueType(Integer.parseInt(paramValue));
				}
			}
			AFJMXQueryResult result;
			try {
				result = query.getAttribute(connection);
				result.setPreviousStatusValue(this.cachedData);
				resultList.add(result);
				cacheString += result.toCacheString();
			} catch (Exception e) {
				e.printStackTrace();
			}
			start = end + 1;
			if (start < length
					&& args[start].equals(this.argumentSequenceSeparator)) {// if
				// sequence
				// ending
				// with
				// a
				// separator
				start++;
			}
		}

		this.summarize(resultList);
		writeCacheData(cacheString);
	}

	/**
	 * Export to nagios format. 
	 * @param list a list of JMXQueryResult
	 */
	private void summarize(ArrayList<AFJMXQueryResult> list) {
		int finalStatus = -1;
		String finalString = "";

		for (int cnt = 0; cnt < list.size(); cnt++) {
			AFJMXQueryResult result = list.get(cnt);
			if (result.getStatus() > finalStatus) {
				finalStatus = result.getStatus();
			}
			finalString += (result.toString() + " ");
		}

		if (finalStatus == AFJMXQueryResult.RETURN_OK) {
			finalString = String.format("%s | %s", AFJMXQueryResult.OK_STRING,
					finalString);
		} else if (finalStatus == AFJMXQueryResult.RETURN_WARNING) {
			finalString = String.format("%s | %s",
					AFJMXQueryResult.WARNING_STRING, finalString);
		} else if (finalStatus == AFJMXQueryResult.RETURN_CRITICAL) {
			finalString = String.format("%s | %s",
					AFJMXQueryResult.CRITICAL_STRING, finalString);
		} else {
			finalString = String.format("%s | %s",
					AFJMXQueryResult.UNKNOWN_STRING, finalString);
		}

		System.out.println(finalString);

	}

	public static void main(String[] args) {
		if (args[0].equals("-help")) { // print help
			printHelp(System.out);
		} else if (args[0].equals("-U")) {
			AFJMXCheck jmxCheck = new AFJMXCheck();
			try {
				jmxCheck.initConnection(args[1]);
			} catch (IOException ioe) {
				ioe.printStackTrace();
				System.exit(1);
			}
			jmxCheck.runCheck(args);
			try {
				jmxCheck.disconnect();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else {
			System.out.println("No url specified.");
		}
	}

	private static void printHelp(PrintStream out) {
		InputStream is = AFJMXCheck.class.getClassLoader().getResourceAsStream(
				"org/nagios/Help.txt");
		BufferedReader reader = new BufferedReader(new InputStreamReader(is));
		try {
			while (true) {
				String s = reader.readLine();
				if (s == null)
					break;
				out.println(s);
			}
		} catch (IOException e) {
			out.println(e);
		} finally {
			try {
				reader.close();
			} catch (IOException e) {
				out.println(e);
			}
		}
	}
}
