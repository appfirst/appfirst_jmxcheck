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
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.io.File;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.Assert;

/**
 * @author Bin Liu
 * 
 */
public class AFJMXCheckTest {
	private AFJMXCheck checkJMX;

	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception {
		checkJMX = new AFJMXCheck();
		checkJMX
				.initConnection("service:jmx:rmi:///jndi/rmi://localhost:3333/jmxrmi");

	}

	/**
	 * @throws java.lang.Exception
	 */
	@After
	public void tearDown() throws Exception {
		checkJMX.disconnect();
	}

	@Test
	public void testRunCheck() {
		String argString = "test classname -U service:jmx:rmi:///jndi/rmi://localhost:3333/jmxrmi -O java.lang:type=Memory -A NonHeapMemoryUsage -K max, -O java.lang:type=Memory -A HeapMemoryUsage -K used -T 1, -O java.lang:type=Memory -A NonHeapMemoryUsage -K committed, -O java.lang:type=Threading -A ThreadCount, -O java.lang:type=Threading -A TotalStartedThreadCount";
		String[] args = argString.split(" ");
		File file = new File(checkJMX.getCacheFileName());
		file.delete();
		checkJMX.runCheck(args);

		Assert.assertEquals(5, checkJMX.getResultList().size());
		AFJMXQueryResult result1 = checkJMX.getResultList().get(0);
		AFJMXQuery query1 = result1.getOriginalQuery();
		Assert.assertEquals(query1.getBeanName(), "java.lang:type=Memory");
		Assert.assertEquals(query1.getAttribute(), "NonHeapMemoryUsage");
		Assert.assertEquals(query1.getAttributeKey(), "max");
		Assert.assertEquals(query1.getValueType(), "current");

		AFJMXQueryResult result2 = checkJMX.getResultList().get(1);
		AFJMXQuery query2 = result2.getOriginalQuery();
		Assert.assertEquals(query2.getBeanName(), "java.lang:type=Memory");
		Assert.assertEquals(query2.getAttribute(), "HeapMemoryUsage");
		Assert.assertEquals(query2.getAttributeKey(), "used");
		Assert.assertEquals(query2.getValueType(), "cumulative");

		AFJMXQueryResult result3 = checkJMX.getResultList().get(2);
		AFJMXQuery query3 = result3.getOriginalQuery();
		Assert.assertEquals(query3.getBeanName(), "java.lang:type=Memory");
		Assert.assertEquals(query3.getAttribute(), "NonHeapMemoryUsage");
		Assert.assertEquals(query3.getAttributeKey(), "committed");
		Assert.assertEquals(query3.getValueType(), "current");

		AFJMXQueryResult result4 = checkJMX.getResultList().get(3);
		AFJMXQuery query4 = result4.getOriginalQuery();
		Assert.assertEquals(query4.getBeanName(), "java.lang:type=Threading");
		Assert.assertEquals(query4.getAttribute(), "ThreadCount");
		Assert.assertEquals(query4.getAttributeKey(), null);
		Assert.assertEquals(query4.getValueType(), "current");

		AFJMXQueryResult result5 = checkJMX.getResultList().get(4);
		AFJMXQuery query5 = result5.getOriginalQuery();
		Assert.assertEquals(query5.getBeanName(), "java.lang:type=Threading");
		Assert.assertEquals(query5.getAttribute(), "TotalStartedThreadCount");
		Assert.assertEquals(query5.getAttributeKey(), null);
		Assert.assertEquals(query5.getValueType(), "current");
		
		file = new File(checkJMX.getCacheFileName());
		Assert.assertTrue(file.exists());
		
		checkJMX.readCacheData();
		Assert.assertEquals(1, checkJMX.getCachedData().size());
		Assert.assertTrue(checkJMX.getCachedData().containsKey("java.lang:type=Memory.HeapMemoryUsage.used")); 
		
		Long previousValue = checkJMX.getCachedData().get("java.lang:type=Memory.HeapMemoryUsage.used");
		checkJMX.runCheck(args);
		Assert.assertEquals(1, checkJMX.getCachedData().size());
		Assert.assertTrue(checkJMX.getCachedData().containsKey("java.lang:type=Memory.HeapMemoryUsage.used"));
		result2 = checkJMX.getResultList().get(1);
		Assert.assertEquals(result2.getStatusValue() - 0L, result2.getCurrentStatusValue() - previousValue);
	}

	@Test
	public void testWriteCacheData() {
		File file = new File(checkJMX.getCacheFileName());
		boolean success = file.delete();

		if (!success)
			throw new IllegalArgumentException("Delete: deletion failed");

		String cacheString = "a 100\nb 200\nc 1";
		checkJMX.writeCacheData(cacheString);
		file = new File(checkJMX.getCacheFileName());
		Assert.assertTrue(file.exists());

		try {
			BufferedReader input = new BufferedReader(new FileReader(checkJMX
					.getCacheFileName()));
			try {
				String line1 = input.readLine();
				String line2 = input.readLine();
				String line3 = input.readLine();

				Assert.assertEquals(line1, "a 100");
				Assert.assertEquals(line2, "b 200");
				Assert.assertEquals(line3, "c 1");

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

	@Test
	public void testReadCacheData() {
		String cacheString = "a 100\nb 200\nc 1";
		try {
			Writer out = new OutputStreamWriter(new FileOutputStream(checkJMX
					.getCacheFileName()), "UTF-8");
			try {
				out.write(cacheString);
			} finally {
				out.close();
			}
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
		checkJMX.readCacheData();

		Assert.assertEquals("Write and Read cache data incorrectly", checkJMX
				.getCachedData().size(), 3);
		Assert.assertTrue(checkJMX.getCachedData().containsKey("a"));
		Assert.assertTrue(checkJMX.getCachedData().containsKey("b"));
		Assert.assertTrue(checkJMX.getCachedData().containsKey("c"));
		Assert.assertEquals("", Long.parseLong(checkJMX.getCachedData()
				.get("a").toString()), 100L);
		Assert.assertEquals("", Long.parseLong(checkJMX.getCachedData()
				.get("b").toString()), 200L);
		Assert.assertEquals("", Long.parseLong(checkJMX.getCachedData()
				.get("c").toString()), 1L);
	}

}
