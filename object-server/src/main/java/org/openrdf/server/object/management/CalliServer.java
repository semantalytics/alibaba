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
package org.openrdf.server.object.management;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import org.openrdf.OpenRDFException;
import org.openrdf.repository.manager.LocalRepositoryManager;
import org.openrdf.server.object.Version;
import org.openrdf.server.object.client.HttpClientFactory;
import org.openrdf.server.object.io.FileUtil;
import org.openrdf.server.object.logging.LoggingProperties;
import org.openrdf.server.object.repository.CalliRepository;
import org.openrdf.server.object.server.WebServer;
import org.openrdf.server.object.util.CallimachusConf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CalliServer implements CalliServerMXBean {
	private static final ThreadFactory THREADFACTORY = new ThreadFactory() {
		public Thread newThread(Runnable r) {
			String name = CalliServer.class.getSimpleName() + "-"
					+ Integer.toHexString(r.hashCode());
			Thread t = new Thread(r, name);
			t.setDaemon(true);
			return t;
		}
	};

	public static interface ServerListener {
		void repositoryInitialized(String repositoryID, CalliRepository repository);

		void repositoryShutDown(String repositoryID);

		void webServiceStarted(WebServer server);

		void webServiceStopping(WebServer server);
	}

	private final Logger logger = LoggerFactory.getLogger(CalliServer.class);
	private final ExecutorService executor = Executors
			.newSingleThreadScheduledExecutor(THREADFACTORY);
	private final CallimachusConf conf;
	private final ServerListener listener;
	private final File serverCacheDir;
	private volatile int starting;
	private volatile boolean running;
	private volatile boolean stopping;
	private int processing;
	private Exception exception;
	WebServer server;
	private final LocalRepositoryManager manager;
	private final Map<String, CalliRepository> repositories = new LinkedHashMap<String, CalliRepository>();

	public CalliServer(CallimachusConf conf, LocalRepositoryManager manager,
			ServerListener listener) throws OpenRDFException, IOException {
		this.conf = conf;
		this.listener = listener;
		this.manager = manager;
		String tmpDirStr = System.getProperty("java.io.tmpdir");
		if (tmpDirStr == null) {
			tmpDirStr = "tmp";
		}
		File tmpDir = new File(tmpDirStr);
		if (!tmpDir.exists()) {
			tmpDir.mkdirs();
		}
		File cacheDir = File.createTempFile("cache", "", tmpDir);
		cacheDir.delete();
		cacheDir.mkdirs();
		FileUtil.deleteOnExit(cacheDir);
		File in = new File(cacheDir, "client");
		HttpClientFactory.setCacheDirectory(in);
		serverCacheDir = new File(cacheDir, "server");
	}

	public String toString() {
		return manager.getBaseDir().toString();
	}

	public boolean isRunning() {
		return running;
	}

	public synchronized void init() throws OpenRDFException, IOException {
		if (isWebServiceRunning()) {
			stopWebServiceNow();
		}
		try {
			server = createServer();
		} catch (IOException e) {
			logger.error(e.toString(), e);
		} catch (OpenRDFException e) {
			logger.error(e.toString(), e);
		} catch (GeneralSecurityException e) {
			logger.error(e.toString(), e);
		} finally {
			if (server == null) {
				shutDownRepositories();
			}
		}
	}

	public synchronized void start() throws IOException, OpenRDFException {
		running = true;
		notifyAll();
		if (server != null) {
			try {
				logger.info("Callimachus is binding to {}", toString());
				server.start();
				System.gc();
				Thread.yield();
				long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
				logger.info("Callimachus started after {} seconds", uptime / 1000.0);
				if (listener != null) {
					listener.webServiceStarted(server);
				}
			} catch (IOException e) {
				logger.error(e.toString(), e);
			}
		}
	}

	public synchronized void stop() throws IOException, OpenRDFException {
		running = false;
		if (isWebServiceRunning()) {
			stopWebServiceNow();
		}
		notifyAll();
	}

	public synchronized void destroy() throws IOException, OpenRDFException {
		stop();
		shutDownRepositories();
		manager.shutDown();
		notifyAll();
	}

	@Override
	public String getServerName() throws IOException {
		String name = conf.getServerName();
		if (name == null || name.length() == 0)
			return Version.getInstance().getVersion();
		return name;
	}

	@Override
	public synchronized void setServerName(String name) throws IOException {
		if (name == null || name.length() == 0 || name.equals(Version.getInstance().getVersion())) {
			conf.setServerName(null);
		} else {
			conf.setServerName(name);
		}
		if (server != null) {
			server.setName(getServerName());
		}
	}

	public String getPorts() throws IOException {
		int[] ports = getPortArray();
		StringBuilder sb = new StringBuilder();
		for (int i=0; i<ports.length; i++) {
			sb.append(ports[i]);
			if (i <ports.length - 1) {
				sb.append(' ');
			}
		}
		return sb.toString();
	}

	public synchronized void setPorts(String portStr) throws IOException {
		int[] ports = new int[0];
		if (portStr != null && portStr.trim().length() > 0) {
			String[] values = portStr.trim().split("\\s+");
			ports = new int[values.length];
			for (int i = 0; i < values.length; i++) {
				ports[i] = Integer.parseInt(values[i]);
			}
		}
		conf.setPorts(ports);
		if (server != null) {
			server.listen(getPortArray(), getSSLPortArray());
		}
	}

	public String getSSLPorts() throws IOException {
		int[] ports = getSSLPortArray();
		StringBuilder sb = new StringBuilder();
		for (int i=0; i<ports.length; i++) {
			sb.append(ports[i]);
			if (i <ports.length - 1) {
				sb.append(' ');
			}
		}
		return sb.toString();
	}

	public synchronized void setSSLPorts(String portStr) throws IOException {
		int[] ports = new int[0];
		if (portStr != null && portStr.trim().length() > 0) {
			String[] values = portStr.trim().split("\\s+");
			ports = new int[values.length];
			for (int i = 0; i < values.length; i++) {
				ports[i] = Integer.parseInt(values[i]);
			}
		}
		conf.setSslPorts(ports);
		if (server != null) {
			server.listen(getPortArray(), getSSLPortArray());
		}
	}

	public boolean isStartingInProgress() {
		return starting > 0;
	}

	public boolean isStoppingInProgress() {
		return stopping;
	}

	public synchronized boolean isWebServiceRunning() {
		return server != null && server.isRunning();
	}

	public synchronized void startWebService() throws Exception {
		if (isWebServiceRunning())
			return;
		final int start = ++starting;
		submit(new Callable<Void>() {
			public Void call() throws Exception {
				startWebServiceNow(start);
				return null;
			}
		});
	}

	public void restartWebService() throws Exception {
		final int start = ++starting;
		submit(new Callable<Void>() {
			public Void call() throws Exception {
				stopWebServiceNow();
				startWebServiceNow(start);
				return null;
			}
		});
	}

	public void stopWebService() throws Exception {
		if (stopping || !isWebServiceRunning())
			return;
		submit(new Callable<Void>() {
			public Void call() throws Exception {
				stopWebServiceNow();
				return null;
			}
		});
	}

	@Override
	public Map<String, String> getLoggingProperties() throws IOException {
		return LoggingProperties.getInstance().getLoggingProperties();
	}

	@Override
	public void setLoggingProperties(Map<String, String> lines)
			throws IOException {
		LoggingProperties.getInstance().setLoggingProperties(lines);
	}

	public synchronized void checkForErrors() throws Exception {
		try {
			if (exception != null)
				throw exception;
		} finally {
			exception = null;
		}
	}

	public synchronized boolean isSetupInProgress() {
		return processing > 0;
	}

	public synchronized String[] getWebappOrigins() throws IOException {
		return conf.getWebappOrigins();
	}

	synchronized void saveError(Exception exc) {
		exception = exc;
	}

	synchronized void begin() {
		processing++;
	}

	synchronized void end() {
		processing--;
		notifyAll();
	}

	protected void submit(final Callable<Void> task)
			throws Exception {
		checkForErrors();
		executor.submit(new Runnable() {
			public void run() {
				begin();
				try {
					task.call();
				} catch (Exception exc) {
					saveError(exc);
				} finally {
					end();
				}
			}
		});
		Thread.yield();
	}

	synchronized void startWebServiceNow(int start) {
		if (start != starting)
			return;
		try {
			if (isWebServiceRunning()) {
				stopWebServiceNow();
			} else {
				shutDownRepositories();
			}
			try {
				if (getPortArray().length == 0 && getSSLPortArray().length == 0)
					throw new IllegalStateException("No TCP port defined for server");
				if (server == null) {
					server = createServer();
				}
			} finally {
				if (server == null) {
					shutDownRepositories();
				}
			}
			server.start();
			if (listener != null) {
				listener.webServiceStarted(server);
			}
		} catch (IOException e) {
			logger.error(e.toString(), e);
		} catch (OpenRDFException e) {
			logger.error(e.toString(), e);
		} catch (GeneralSecurityException e) {
			logger.error(e.toString(), e);
		} finally {
			starting = 0;
			notifyAll();
		}
	}

	synchronized boolean stopWebServiceNow() throws OpenRDFException {
		stopping = true;
		try {
			if (server == null) {
				shutDownRepositories();
				return false;
			} else {
				if (listener != null) {
					listener.webServiceStopping(server);
				}
				server.stop();
				HttpClientFactory instance = HttpClientFactory.getInstance();
				if (instance != null) {
					instance.removeProxy(server);
				}
				shutDownRepositories();
				server.destroy();
				return true;
			}
		} catch (IOException e) {
			logger.error(e.toString(), e);
			return false;
		} finally {
			stopping = false;
			notifyAll();
			server = null;
			shutDownRepositories();
		}
	}

	private synchronized WebServer createServer() throws OpenRDFException,
			IOException, NoSuchAlgorithmException {
		WebServer server = new WebServer(serverCacheDir);
		server.setName(getServerName());
		server.listen(getPortArray(), getSSLPortArray());
		return server;
	}

	synchronized void refreshRepository(String repositoryID) throws OpenRDFException {
		CalliRepository repository = repositories.get(repositoryID);
		if (repository != null) {
			repository.getDatasourceManager().shutDown();
			if (repository.isInitialized()) {
				repository.shutDown();
			}
		}
		repositories.remove(repositoryID);
	}

	private synchronized void shutDownRepositories() throws OpenRDFException {
		for (Map.Entry<String, CalliRepository> e : repositories.entrySet()) {
			e.getValue().getDatasourceManager().shutDown();
			e.getValue().shutDown();
			if (listener != null) {
				listener.repositoryShutDown(e.getKey());
			}
		}
		repositories.clear();
		if (!manager.getInitializedRepositoryIDs().isEmpty()) {
			manager.refresh();
		}
	}

	private int[] getPortArray() throws IOException {
		return conf.getPorts();
	}

	private int[] getSSLPortArray() throws IOException {
		return conf.getSslPorts();
	}

}
