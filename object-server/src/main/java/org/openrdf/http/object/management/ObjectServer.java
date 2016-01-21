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
package org.openrdf.http.object.management;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.http.HttpInetConnection;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.nio.NHttpConnection;
import org.openrdf.OpenRDFException;
import org.openrdf.http.object.Version;
import org.openrdf.http.object.client.HttpClientFactory;
import org.openrdf.http.object.helpers.Exchange;
import org.openrdf.http.object.helpers.ObjectContext;
import org.openrdf.http.object.util.PrefixMap;
import org.openrdf.model.Model;
import org.openrdf.model.Resource;
import org.openrdf.model.impl.LinkedHashModel;
import org.openrdf.model.vocabulary.RDF;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.config.RepositoryConfig;
import org.openrdf.repository.config.RepositoryConfigSchema;
import org.openrdf.repository.manager.RepositoryProvider;
import org.openrdf.repository.object.ObjectRepository;
import org.openrdf.repository.object.managers.helpers.DirUtil;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParser;
import org.openrdf.rio.Rio;
import org.openrdf.rio.helpers.StatementCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ObjectServer implements ObjectServerMBean {
	private final Logger logger = LoggerFactory.getLogger(ObjectServer.class);
	private final PrefixMap<ObjectRepositoryManager> managers = new PrefixMap<ObjectRepositoryManager>();
	private File serverCacheDir;
	private String serverName = ObjectServer.class.getSimpleName();
	private int[] ports = new int[0];
	private int[] sslPorts = new int[0];
	private int timeout;
	private volatile boolean starting;
	private volatile boolean stopping;
	WebServer server;

	public ObjectServer(File... dataDir) throws OpenRDFException,
			IOException {
		this(ObjectServer.class.getClassLoader(), dataDir);
	}

	public ObjectServer(ClassLoader cl, File... dataDir) throws OpenRDFException,
			IOException {
		for (int i=0;i<dataDir.length;i++) {
			dataDir[i].mkdirs();
			ObjectRepositoryManager manager = new ObjectRepositoryManager(dataDir[i], cl);
			this.managers.put(manager.getLocation().toExternalForm(), manager);
		}
		serverCacheDir = DirUtil.createTempDir("object-server-cache");
		DirUtil.deleteOnExit(serverCacheDir);
		File clientCacheDir = DirUtil.createTempDir("http-client-cache");
		DirUtil.deleteOnExit(clientCacheDir);
		HttpClientFactory.setCacheDirectory(clientCacheDir);
	}

	public ObjectServer(File cacheDir, ClassLoader cl, File... dataDir)
			throws OpenRDFException, IOException {
		File libDir = new File(cacheDir, "lib");
		for (int i=0;i<dataDir.length;i++) {
			dataDir[i].mkdirs();
			ObjectRepositoryManager manager = new ObjectRepositoryManager(dataDir[i], libDir, cl);
			this.managers.put(manager.getLocation().toExternalForm(), manager);
		}
		serverCacheDir = new File(cacheDir, "server");
		HttpClientFactory.setCacheDirectory(new File(cacheDir, "client"));
	}

	public String toString() {
		try {
			StringBuilder sb = new StringBuilder();
			for (ObjectRepositoryManager manager : managers.values()) {
				sb.append(manager.getLocation().toString()).append(' ');
			}
			return sb.toString();
		} catch (MalformedURLException e) {
			logger.warn(e.toString(), e);
			return managers.values().toString();
		}
	}

	@Override
	public String getServerName() throws IOException {
		String name = this.serverName;
		if (name == null || name.length() == 0)
			return Version.getInstance().getVersion();
		return name;
	}

	@Override
	public synchronized void setServerName(String name) throws IOException {
		if (name == null || name.length() == 0
				|| name.equals(Version.getInstance().getVersion())) {
			this.serverName = null;
		} else {
			this.serverName = name;
		}
		if (server != null) {
			server.setName(getServerName());
		}
	}

	public String getPorts() throws IOException {
		int[] ports = this.ports;
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < ports.length; i++) {
			sb.append(ports[i]);
			if (i < ports.length - 1) {
				sb.append(' ');
			}
		}
		return sb.toString();
	}

	public synchronized void setPorts(String portStr) throws IOException {
		this.ports = parsePorts(portStr);
		if (server != null) {
			server.listen(this.ports, this.sslPorts);
		}
	}

	public String getSSLPorts() throws IOException {
		int[] ports = this.sslPorts;
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < ports.length; i++) {
			sb.append(ports[i]);
			if (i < ports.length - 1) {
				sb.append(' ');
			}
		}
		return sb.toString();
	}

	public synchronized void setSSLPorts(String portStr) throws IOException {
		this.sslPorts = parsePorts(portStr);
		if (server != null) {
			server.listen(this.ports, this.sslPorts);
		}
	}

	public int getTimeout() {
		return timeout;
	}

	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}

	public boolean isCaching() {
		return this.serverCacheDir != null;
	}

	public void setCaching(boolean caching) throws IOException {
		if (caching && this.serverCacheDir == null) {
			serverCacheDir = DirUtil.createTempDir("object-server-cache");
			DirUtil.deleteOnExit(serverCacheDir);
			File clientCacheDir = DirUtil.createTempDir("http-client-cache");
			DirUtil.deleteOnExit(clientCacheDir);
			HttpClientFactory.setCacheDirectory(clientCacheDir);
			logger.info("Enabling server side caching");
		} else if (!caching && this.serverCacheDir != null) {
			this.serverCacheDir = null;
			HttpClientFactory.setCacheDirectory(null);
			logger.info("Disabling server side caching");
		}
	}

	public boolean isStartingInProgress() {
		return starting;
	}

	public boolean isStoppingInProgress() {
		return stopping;
	}

	public boolean isCompilingInProgress() {
		for (ObjectRepositoryManager manager : managers.values()) {
			if (manager.isCompiling())
				return true;
		}
		return false;
	}

	public boolean isRunning() {
		return server != null && server.isRunning() && !stopping;
	}

	public boolean isShutDown() {
		for (ObjectRepositoryManager manager : managers.values()) {
			if (manager.isInitialized())
				return false;
		}
		return true;
	}

	@Override
	public String getStatus() {
		return server.getStatus();
	}

	@Override
	public void poke() {
		if (server != null) {
			server.poke();
		}
	}

	@Override
	public void resetCache() {
		server.resetCache();
	}

	@Override
	public synchronized void recompileSchema() throws IOException, OpenRDFException {
		try {
			for (ObjectRepositoryManager manager : managers.values()) {
				manager.recompileSchema();
			}
		} finally {
			notifyAll();
		}
	}

	@Override
	public synchronized void resetConnections() throws IOException {
		server.resetConnections();
	}

	@Override
	public synchronized ConnectionBean[] getConnections() {
		NHttpConnection[] connections = server.getOpenConnections();
		ConnectionBean[] beans = new ConnectionBean[connections.length];
		for (int i = 0; i < connections.length; i++) {
			ConnectionBean bean = new ConnectionBean();
			NHttpConnection conn = connections[i];
			beans[i] = bean;
			switch (conn.getStatus()) {
			case NHttpConnection.ACTIVE:
				if (conn.isOpen()) {
					bean.setStatus("OPEN");
				} else if (conn.isStale()) {
					bean.setStatus("STALE");
				} else {
					bean.setStatus("ACTIVE");
				}
				break;
			case NHttpConnection.CLOSING:
				bean.setStatus("CLOSING");
				break;
			case NHttpConnection.CLOSED:
				bean.setStatus("CLOSED");
				break;
			}
			if (conn instanceof HttpInetConnection) {
				HttpInetConnection inet = (HttpInetConnection) conn;
				InetAddress ra = inet.getRemoteAddress();
				int rp = inet.getRemotePort();
				InetAddress la = inet.getLocalAddress();
				int lp = inet.getLocalPort();
				if (ra != null && la != null) {
					InetSocketAddress remote = new InetSocketAddress(ra, rp);
					InetSocketAddress local = new InetSocketAddress(la, lp);
					bean.setStatus(bean.getStatus() + " " + remote + "->"
							+ local);
				}
			}
			HttpRequest req = conn.getHttpRequest();
			if (req != null) {
				bean.setRequest(req.getRequestLine().toString());
			}
			HttpResponse resp = conn.getHttpResponse();
			if (resp != null) {
				bean.setResponse(resp.getStatusLine().toString() + " "
						+ resp.getEntity());
			}
			ObjectContext ctx = ObjectContext.adapt(conn.getContext());
			Exchange[] array = ctx.getPendingExchange();
			if (array != null) {
				String[] pending = new String[array.length];
				for (int j = 0; j < pending.length; j++) {
					pending[j] = array[j].toString();
					if (array[j].isReadingRequest()) {
						bean.setConsuming(array[j].toString());
					}
				}
				bean.setPending(pending);
			} else {
				bean.setPending(new String[0]);
			}
		}
		return beans;
	}

	@Override
	public void connectionDumpToFile(String outputFile) throws IOException {
		PrintWriter writer = new PrintWriter(new FileWriter(outputFile, true));
		try {
			writer.println("status,request,consuming,response,pending");
			for (ConnectionBean connection : getConnections()) {
				writer.print(toString(connection.getStatus()));
				writer.print(",");
				writer.print(toString(connection.getRequest()));
				writer.print(",");
				writer.print(toString(connection.getConsuming()));
				writer.print(",");
				writer.print(toString(connection.getResponse()));
				writer.print(",");
				String[] pending = connection.getPending();
				if (pending != null) {
					for (String p : pending) {
						writer.print(toString(p));
						writer.print(",");
					}
				}
				writer.println();
			}
			writer.println();
			writer.println();
		} finally {
			writer.close();
		}
		logger.info("Connection dump: {}", outputFile);
	}

	public RepositoryConnection openSchemaConnection(String location)
			throws OpenRDFException {
		ObjectRepositoryManager manager = managers.getClosest(location);
		if (manager == null)
			throw new IllegalArgumentException("Unknown repository location: " + location);
		return manager.openSchemaConnection();
	}

	public synchronized String[] getRepositoryLocations()
			throws OpenRDFException {
		Set<String> result = new TreeSet<String>();
		for (ObjectRepositoryManager manager : managers.values()) {
			for (String id : manager.getRepositoryIDs()) {
				result.add(manager.getRepositoryLocation(id).toExternalForm());
			}
		}
		return result.toArray(new String[result.size()]);
	}

	public RepositoryMXBean getRepositoryMXBean(String location)
			throws OpenRDFException {
		String id = RepositoryProvider.getRepositoryIdOfRepository(location);
		return new RepositoryMXBeanImpl(getManagerFor(location), id);
	}

	public ObjectRepository getRepository(String location)
			throws OpenRDFException {
		String id = RepositoryProvider.getRepositoryIdOfRepository(location);
		return getManagerFor(location).getObjectRepository(id);
	}

	public String[] getRepositoryPrefixes(String location) throws OpenRDFException {
		String id = RepositoryProvider.getRepositoryIdOfRepository(location);
		return getManagerFor(location).getRepositoryPrefixes(id);
	}

	public synchronized void addRepositoryPrefix(String location, String prefix)
			throws OpenRDFException {
		String id = RepositoryProvider.getRepositoryIdOfRepository(location);
		ObjectRepositoryManager manager = getManagerFor(location);
		manager.addRepositoryPrefix(id, prefix);
		if (server != null) {
			startRepository(manager, id);
		}
		notifyAll();
	}

	public synchronized void setRepositoryPrefixes(String location, String[] prefixes)
			throws OpenRDFException {
		String id = RepositoryProvider.getRepositoryIdOfRepository(location);
		ObjectRepositoryManager manager = getManagerFor(location);
		if (server != null) {
			String[] existing = manager.getRepositoryPrefixes(id);
			List<String> removed = new ArrayList<String>(
					Arrays.asList(existing));
			removed.removeAll(Arrays.asList(prefixes));
			for (String rem : removed) {
				server.removeRepository(rem);
			}
		}
		manager.setRepositoryPrefixes(id, prefixes);
		if (server != null) {
			startRepository(manager, id);
		}
		notifyAll();
	}

	public synchronized String addRepository(String location, String base,
			String configString) throws OpenRDFException, IOException {
		ObjectRepositoryManager manager = managers.getClosest(location);
		if (manager == null)
			throw new IllegalArgumentException("Unknown repository location: " + location);
		Model graph = parseTurtleGraph(manager, configString, base);
		Resource node = graph.filter(null, RDF.TYPE,
				RepositoryConfigSchema.REPOSITORY).subjectResource();
		RepositoryConfig config = RepositoryConfig.create(graph, node);
		String id = config.getID();
		if (manager.isRepositoryPresent(id) && server != null) {
			String[] existing = manager.getRepositoryPrefixes(id);
			List<String> removed = new ArrayList<String>(
					Arrays.asList(existing));
			manager.addRepository(config);
			removed.removeAll(Arrays.asList(manager.getRepositoryPrefixes(id)));
			for (String rem : removed) {
				server.removeRepository(rem);
			}
		} else {
			manager.addRepository(config);
		}
		if (server != null) {
			startRepository(manager, id);
		}
		notifyAll();
		return manager.getRepositoryLocation(id).toExternalForm();
	}

	public synchronized boolean removeRepository(String location)
			throws OpenRDFException {
		String id = RepositoryProvider.getRepositoryIdOfRepository(location);
		ObjectRepositoryManager manager = managers.getClosest(location);
		if (manager == null || !manager.isRepositoryPresent(id))
			return false;
		stopRepository(manager, id);
		notifyAll();
		return manager.removeRepository(id);
	}

	public synchronized void init() throws OpenRDFException, IOException {
		try {
			logger.debug("Initializing {}", this);
			for (ObjectRepositoryManager manager : managers.values()) {
				if (!manager.isCompiled()) {
					recompileSchema();
				}
			}
			if (server != null) {
				server.destroy();
			}
			server = createServer(this.serverCacheDir, this.timeout,
					this.ports, this.sslPorts);
			for (ObjectRepositoryManager manager : managers.values()) {
				for (String id : manager.getRepositoryIDs()) {
					startRepository(manager, id);
				}
			}
		} catch (IOException e) {
			logger.error(e.toString(), e);
			throw e;
		} catch (OpenRDFException e) {
			logger.error(e.toString(), e);
			throw e;
		} finally {
			notifyAll();
		}
	}

	public synchronized void start() throws IOException, OpenRDFException {
		if (server == null)
			return;
		try {
			logger.debug("Starting {}", this);
			stopping = false;
			starting = true;
			if (getPorts().length() == 0 && getSSLPorts().length() == 0) {
				logger.info("{} is not bound to any port", getServerName());
			} else {
				logger.info("{} is binding to port {}", getServerName(),
						getPorts() + ' ' + getSSLPorts());
			}
			server.start();
			System.gc();
			System.runFinalization();
			Thread.yield();
			for (int i = 100; i < 10000 && !isRunning(); i*=2) {
				Thread.sleep(i); // give workers a chance to start
			}
			long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
			logger.info("{} started after {} seconds", getServerName(),
					uptime / 1000.0);
		} catch (IOException e) {
			logger.error(e.toString(), e);
			throw e;
		} catch (InterruptedException cause) {
			logger.error(cause.toString(), cause);
			InterruptedIOException e = new InterruptedIOException(cause.getMessage());
			e.initCause(cause);
			throw e;
		} finally {
			starting = false;
			notifyAll();
		}
	}

	public synchronized void stop() throws IOException, OpenRDFException {
		logger.debug("Stopping {}", this);
		stopping = true;
		try {
			if (server != null) {
				server.stop();
			}
		} finally {
			notifyAll();
		}
	}

	public synchronized void destroy() throws IOException, OpenRDFException {
		try {
			stop();
			if (server != null) {
				server.destroy();
				server = null;
			}
		} finally {
			for (ObjectRepositoryManager manager : managers.values()) {
				manager.shutDown();
			}
			stopping = false;
			logger.debug("Destroyed {}", this);
			notifyAll();
		}
	}

	public synchronized void restart() throws IOException, OpenRDFException {
		if (server != null) {
			server.stop();
		}
		resetConnections();
		if (server != null) {
			server.destroy();
			server = null;
		}
		for (ObjectRepositoryManager manager : managers.values()) {
			manager.refresh();
		}
		recompileSchema();
		server = createServer(this.serverCacheDir, this.timeout,
				this.ports, this.sslPorts);
		start();
	}

	private ObjectRepositoryManager getManagerFor(String location) throws OpenRDFException {
		ObjectRepositoryManager manager = managers.getClosest(location);
		if (manager == null)
			throw new IllegalArgumentException("Unknown repository location: " + location);
		String id = RepositoryProvider.getRepositoryIdOfRepository(location);
		if (!manager.isRepositoryPresent(id))
			throw new IllegalArgumentException("Unknown repository location: " + location);
		return manager;
	}

	private WebServer createServer(File serverCacheDir, int timeout,
			int[] ports, int[] sslPorts) throws OpenRDFException, IOException {
		if (serverCacheDir == null) {
			WebServer server = new WebServer(timeout);
			server.setName(getServerName());
			server.listen(ports, sslPorts);
			return server;
		} else {
			serverCacheDir.mkdirs();
			WebServer server = new WebServer(serverCacheDir, timeout);
			server.setName(getServerName());
			server.listen(ports, sslPorts);
			return server;
		}
	}

	private synchronized void startRepository(ObjectRepositoryManager manager,
			String id) throws OpenRDFException {
		String[] prefixes = manager.getRepositoryPrefixes(id);
		if (prefixes != null && prefixes.length > 0) {
			URL url = manager.getRepositoryLocation(id);
			ObjectRepository obj = manager.getObjectRepository(id);
			for (String prefix : prefixes) {
				logger.info("Serving {} from {}", prefix, url);
				server.addRepository(prefix, obj);
			}
		}
	}

	private synchronized void stopRepository(ObjectRepositoryManager manager,
			String id) throws OpenRDFException {
		if (manager.isRepositoryPresent(id)) {
			for (String prefix : getRepositoryPrefixes(id)) {
				URL url = manager.getRepositoryLocation(id);
				logger.info("Stop serving {}", prefix, url);
				server.removeRepository(prefix);
			}
		}
	}

	private Model parseTurtleGraph(ObjectRepositoryManager manager, String configString,
			String base) throws IOException, OpenRDFException {
		Model graph = new LinkedHashModel();
		RDFParser rdfParser = Rio.createParser(RDFFormat.TURTLE);
		final RepositoryConnection con = manager.openSchemaConnection();
		try {
			rdfParser.setRDFHandler(new StatementCollector(graph) {
				public void handleNamespace(String prefix, String uri)
						throws RDFHandlerException {
					try {
						con.setNamespace(prefix, uri);
					} catch (RepositoryException e) {
						throw new RDFHandlerException(e);
					}
				}
			});
			rdfParser.parse(new StringReader(configString), base);
		} finally {
			con.close();
		}
		return graph;
	}

	private int[] parsePorts(String portStr) {
		int[] ports = new int[0];
		if (portStr != null && portStr.length() > 0) {
			List<String> values = new ArrayList<String>(Arrays.asList(portStr
					.split("\\D+")));
			values.removeAll(Collections.singleton(""));
			ports = new int[values.size()];
			for (int i = 0; i < ports.length; i++) {
				ports[i] = Integer.parseInt(values.get(i));
			}
		}
		return ports;
	}

	private String toString(String string) {
		if (string == null)
			return "";
		return string;
	}

}
