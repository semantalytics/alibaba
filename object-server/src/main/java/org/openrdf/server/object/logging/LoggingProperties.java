/*
 * Copyright (c) 2013 3 Round Stones Inc., Some Rights Reserved
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
package org.openrdf.server.object.logging;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Enumeration;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;


public class LoggingProperties extends NotificationBroadcasterSupport implements LoggingPropertiesMXBean {
	private final File file;
	private Formatter formatter;
	private Handler nh;

	public final class NotificationHandler extends Handler {

		@Override
		public void publish(LogRecord record) {
			String type = record.getLevel().toString();
			String source = record.getLoggerName();
			long sequenceNumber = record.getSequenceNumber();
			long timeStamp = record.getMillis();
			if (source.startsWith("javax.management")
					|| source.startsWith("sun.rmi"))
				return; // recursive
			String message = getFormatter().formatMessage(record);
			Notification note = new Notification(type, source, sequenceNumber,
					timeStamp, message);
			if (record.getThrown() != null) {
				try {
					StringWriter sw = new StringWriter();
					PrintWriter pw = new PrintWriter(sw);
					record.getThrown().printStackTrace(pw);
					pw.close();
					note.setUserData(sw.toString());
				} catch (Exception ex) {
				}
			}
			sendNotification(note);
		}

		@Override
		public void close() throws SecurityException {
			// no-op
		}

		@Override
		public void flush() {
			// no-op
		}

	}

	public LoggingProperties() {
		this(getDefaultLoggingPropertiesFile());
	}

	public LoggingProperties(File loggingProperties) {
		this.file = loggingProperties;
		formatter = new LogMessageFormatter();
		nh = new NotificationHandler();
		nh.setFormatter(formatter);
		nh.setLevel(Level.ALL);
	}

	public File getLoggingPropertiesFile() {
		return file;
	}

	@Override
	public void startNotifications(String loggerName) {
		Logger logger = LogManager.getLogManager().getLogger(loggerName);
		if (logger == null)
			throw new IllegalArgumentException("No such logger: " + loggerName);
		logger.removeHandler(nh);
		logger.addHandler(nh);
	}

	@Override
	public void stopNotifications() {
		Enumeration<String> names = LogManager.getLogManager().getLoggerNames();
		while (names.hasMoreElements()) {
			String name = names.nextElement();
			Logger.getLogger(name).removeHandler(nh);
		}
	}

	@Override
	public void logAll(String pkg) throws IOException {
		appendLoggingProperties("\n" + pkg + ".level=ALL\n");
	}

	@Override
	public void logInfo(String pkg) throws IOException {
		appendLoggingProperties("\n" + pkg + ".level=INFO\n");
	}

	@Override
	public void logWarn(String pkg) throws IOException {
		appendLoggingProperties("\n" + pkg + ".level=WARN\n");
	}

	@Override
	public synchronized String getLoggingProperties() throws IOException {
		if (!file.isFile())
			return null;
		StringWriter writer = new StringWriter();
		FileReader reader = new FileReader(file);
		try {
			int read;
			char[] cbuf = new char[1024];
			while ((read = reader.read(cbuf)) >= 0) {
				writer.write(cbuf, 0, read);
			}
			return writer.toString();
		} finally {
			reader.close();
		}
	}

	@Override
	public synchronized void setLoggingProperties(String properties) throws IOException {
		LogManager.getLogManager().readConfiguration(new ByteArrayInputStream(properties.getBytes()));
		file.getParentFile().mkdirs();
		FileWriter writer = new FileWriter(file);
		try {
			writer.write(properties);
		} finally {
			writer.close();
		}
	}

	private static File getDefaultLoggingPropertiesFile() {
		String file = System.getProperty("java.util.logging.config.file");
		if (file == null)
			return new File("etc/logging.properties");
		return new File(file);
	}

	private synchronized void appendLoggingProperties(String properties) throws IOException {
		file.getParentFile().mkdirs();
		FileWriter writer = new FileWriter(file, true);
		try {
			writer.write(properties);
		} finally {
			writer.close();
		}
		LogManager.getLogManager().readConfiguration(new FileInputStream(file));
	}

}
