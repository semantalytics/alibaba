/*
 * Portions Copyright (c) 2009-10 Zepheira LLC and James Leigh, Some
   Rights Reserved
 * Portions Copyright (c) 2010-11 Talis Inc, Some Rights Reserved 
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.openrdf.server.object.management;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.util.Date;
import java.util.GregorianCalendar;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;

public class JVMUsage implements JVMUsageMXBean {

	public String[] getJVMUsage() throws Exception {
		int i = 0;
		String[] result = new String[5];
		result[i++] = getOSUsage();
		result[i++] = getMemoryUsage();
		result[i++] = getRuntimeUsage();
		result[i++] = getClassUsage();
		result[i++] = getFileSystemUsage();
		return result;
	}

	private String getRuntimeUsage() throws DatatypeConfigurationException {
		StringWriter sw = new StringWriter();
		PrintWriter w = new PrintWriter(sw, true);
		RuntimeMXBean mx = ManagementFactory.getRuntimeMXBean();
		Date starttime = new Date(mx.getStartTime());
		GregorianCalendar gcal = new GregorianCalendar();
		gcal.setTime(starttime);
		DatatypeFactory df = DatatypeFactory.newInstance();
		String date = df.newXMLGregorianCalendar(gcal).toXMLFormat();
		w.print("VM:\t");
		w.print(System.getProperty("java.vendor"));
		w.print(" ");
		w.print(System.getProperty("java.vm.name"));
		w.print(" ");
		w.println(System.getProperty("java.version"));
		w.print("Server:\t");
		w.println(org.openrdf.server.object.Version.getInstance().getVersion());
		w.print("User:\t");
		w.println(System.getProperty("user.name"));
		w.println("VM start time:\t" + date);
		w.println("VM up time:\t" + mx.getUptime() + " ms");
		w.print("Available processors (cores):\t");
		w.println(Runtime.getRuntime().availableProcessors());
		// the input arguments passed to the Java virtual machine
		// which does not include the arguments to the main method.
		w.println("JVM arguments:\n" + mx.getInputArguments());
		return sw.toString();
	}

	private String getClassUsage() {
		StringWriter sw = new StringWriter();
		PrintWriter w = new PrintWriter(sw, true);
		ClassLoadingMXBean mx = ManagementFactory.getClassLoadingMXBean();
		w.println("Classes loaded:\t" + mx.getLoadedClassCount());
		w.println("Total loaded:\t" + mx.getTotalLoadedClassCount());
		RuntimeMXBean rmx = ManagementFactory.getRuntimeMXBean();
		w.println("Boot class path:\n" + rmx.getBootClassPath());
		w.println("Class path:\n" + rmx.getClassPath());
		return sw.toString();
	}

	private String getOSUsage() throws Exception {
		StringWriter sw = new StringWriter();
		PrintWriter w = new PrintWriter(sw, true);
		OperatingSystemMXBean mx = ManagementFactory.getOperatingSystemMXBean();
		w.print("OS:\t");
		w.print(System.getProperty("os.name"));
		w.print(" ");
		w.print(System.getProperty("os.version"));
		w.print(" (");
		w.print(System.getProperty("os.arch"));
		w.println(")");
		w.print("Name:\t");
		w.println(mx.getName());
		w.print("Arch:\t");
		w.println(mx.getArch());
		w.print("Version:\t");
		w.println(mx.getVersion());
		w.print("Load average:\t");
		w.println(mx.getSystemLoadAverage());
		return sw.toString();
	}

	private String getMemoryUsage() {
		StringWriter sw = new StringWriter();
		PrintWriter w = new PrintWriter(sw, true);
		MemoryMXBean mx = ManagementFactory.getMemoryMXBean();
		w.print("Memory used:\t");
		Runtime runtime = Runtime.getRuntime();
		long usedMemory = runtime.totalMemory() - runtime.freeMemory();
		long maxMemory = runtime.maxMemory();

		// Memory usage (percentage)
		w.print(usedMemory * 100 / maxMemory);
		w.println("%");

		// Memory usage in MB
		w.print("Used:\t");
		w.print((int) (usedMemory / 1024 / 1024));
		w.println("m");
		w.print("Allocated:\t");
		w.print((int) (maxMemory / 1024 / 1024));
		w.println("m");
		w.print("Pending finalization:\t");
		w.println(mx.getObjectPendingFinalizationCount());
		return sw.toString();
	}

	private String getFileSystemUsage() {
		StringWriter sw = new StringWriter();
		PrintWriter w = new PrintWriter(sw, true);
		/* Get a list of all filesystem roots on this system */
		File[] roots = File.listRoots();

		/* For each filesystem root, print some info */
		for (File root : roots) {
			w.print("File system root:\t");
			w.println(root.getAbsolutePath());
			w.print("Size:\t");
			w.print((int) (root.getTotalSpace() / 1024 / 1024));
			w.println("m");
			w.print("Free:\t");
			w.print((int) (root.getFreeSpace() / 1024 / 1024));
			w.println("m");
			w.print("Usable:\t");
			w.print((int) (root.getUsableSpace() / 1024 / 1024));
			w.println("m");
		}
		w.print("Current working directory:\t");
		w.println(new File(".").getAbsolutePath());
		return sw.toString();
	}

}
