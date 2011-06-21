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
import java.util.Iterator;
import java.util.Map;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

/**
 * This class is used to connect JMX and getting the performance data defined by
 * user's command line.
 * 
 * This class is not thread-safe because it doesn't check concurrent access to
 * the cache data file. 
 * @author Bin Liu
 * 
 */
public class AFJMXCheck {
	private JMXConnector connector;
	private MBeanServerConnection connection;
	private final String argumentSequenceSeparator = "#";
	private String cacheFileName = "/usr/share/appfirst/plugins/AFJMXCheckData";
	private HashMap<String, Double> cachedData = new HashMap<String, Double>();
	private ArrayList<AFJMXQueryResult> resultList = new ArrayList<AFJMXQueryResult>();

	public String getCacheFileName() {
		return cacheFileName;
	}

	public void setCacheFileName(String filename) {
		this.cacheFileName = filename;
	}

	public MBeanServerConnection getConnection() {
		return connection;
	}

	public void setConnection(MBeanServerConnection connection) {
		this.connection = connection;
	}

	public HashMap<String, Double> getCachedData() {
		return cachedData;
	}

	public void setCachedData(HashMap<String, Double> cachedData) {
		this.cachedData = cachedData;
	}

	public ArrayList<AFJMXQueryResult> getResultList() {
		return resultList;
	}

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
	public void readCacheData() {
		this.cachedData.clear();
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
					cachedData.put(values[0], Double.parseDouble(values[1]));
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

		}
	}
	
	/**
	 * Create the cache string which'll be writen to the file
	 * from the current query result and the previous result.
	 *  
	 * @return the cached data string. 
	 */
	private String generateCacheString() {
		String cacheString = "";
		HashMap<String, Integer> map = new HashMap<String, Integer>();
		for (int cnt = 0; cnt < this.resultList.size(); cnt++) {// write all the new result
			String key = this.resultList.get(cnt).getOriginalQuery().getName();
			cacheString += this.resultList.get(cnt).toCacheString();
			map.put(key, 1);
		}

		Iterator it = cachedData.entrySet().iterator();
		while (it.hasNext()) {// write all the keys that not in the new result
								// back to cache file
			Map.Entry pairs = (Map.Entry) it.next();
			if (!map.containsKey(pairs.getKey())) {
				cacheString += (String.format("%s %s\n", pairs.getKey(), pairs
						.getValue()));
			}
		}
		return cacheString;
	}

	/**
	 * Write current cache data into a flat file.
	 * 
	 * @param the cached data string. 
	 */
	public void writeCacheData(String cacheString) {
		try {
			Writer out = new OutputStreamWriter(new FileOutputStream(
					this.cacheFileName), "UTF-8");
			try {
				out.write(cacheString);
			} finally {
				out.close();
			}
		} catch (IOException ioe) {

		}
	}

	/**
	 * 
	 * @param args
	 *            all the arguments in user's original input.
	 */
	public void runCheck(String[] args) {
		int length = args.length;
		int start = 2;

		readCacheData();
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
			for (int i = start; i <= end - 1; i += 2) {// parse and parameters
				String paramName = args[i];
				String paramValue = args[i + 1].replace(
						this.argumentSequenceSeparator, "");
				if (paramName.equals("-O")) {
					query.setBeanName(paramValue);
				} else if (paramName.equals("-A")) {// attribute key
					query.setAttribute(paramValue);
				} else if (paramName.equals("-I")) {
					query.setInfoAttribute(paramValue);
				} else if (paramName.equals("-J")) {
					query.setInfoKey(paramValue);
				} else if (paramName.equals("-K")) {
					query.setAttributeKey(paramValue);
				} else if (paramName.startsWith("-v")) {
					query.setVerbatim(Integer.parseInt(paramValue));
				} else if (paramName.equals("-w")) {// warning
					query.setWarningThreshold(Double.parseDouble(paramValue));
				} else if (paramName.equals("-c")) {// critical
					query.setCriticalThreshold(Double.parseDouble(paramValue));
				} else if (paramName.equals("-T")) {// value type
					query.setValueType(Integer.parseInt(paramValue));
				}
			}
			AFJMXQueryResult result = null;
			try {
				result = query.getAttribute(connection);
				result.setPreviousStatusValue(this.cachedData);
				resultList.add(result);
				// cacheString += result.toCacheString();
			} catch (Exception e) {
				result = new AFJMXQueryResult(query);
				result.setStatus(AFJMXQueryResult.RETURN_CRITICAL);
				resultList.add(result);
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
		writeCacheData(this.generateCacheString());
	}

	/**
	 * Export to nagios format.
	 * 
	 * @param list
	 *            a list of JMXQueryResult
	 */
	private void summarize(ArrayList<AFJMXQueryResult> list) {
		int finalStatus = -1;
		String finalStatusString = "";
		for (int cnt = 0; cnt < list.size(); cnt++) {// for each parameter
			AFJMXQueryResult result = list.get(cnt);
			// increase the status if any warning, critical.
			// 0 means OK, -1 is unknown.
			if (result.getStatus() > finalStatus) {
				finalStatus = result.getStatus();
			}
			finalStatusString += (result.toString() + " ");// concatenate all
			// the
			// status string
			// together
		}

		// generate the final status string.
		if (finalStatus == AFJMXQueryResult.RETURN_OK) {
			finalStatusString = String.format("%s | %s",
					AFJMXQueryResult.OK_STRING, finalStatusString);
		} else if (finalStatus == AFJMXQueryResult.RETURN_WARNING) {
			finalStatusString = String.format("%s | %s",
					AFJMXQueryResult.WARNING_STRING, finalStatusString);
		} else if (finalStatus == AFJMXQueryResult.RETURN_CRITICAL) {
			finalStatusString = String.format("%s | %s",
					AFJMXQueryResult.CRITICAL_STRING, finalStatusString);
		} else {
			finalStatusString = String.format("%s | %s",
					AFJMXQueryResult.UNKNOWN_STRING, finalStatusString);
		}

		System.out.println(finalStatusString); // print the status to be used by
		// collector
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
				"com/appfirst/Help.txt");
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
