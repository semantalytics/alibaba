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
import java.io.PrintWriter;
import java.io.StringReader;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.http.HttpInetConnection;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.nio.NHttpConnection;
import org.openrdf.OpenRDFException;
import org.openrdf.http.object.Version;
import org.openrdf.http.object.helpers.Exchange;
import org.openrdf.http.object.helpers.ObjectContext;
import org.openrdf.http.object.io.DirUtil;
import org.openrdf.model.Model;
import org.openrdf.model.Resource;
import org.openrdf.model.impl.LinkedHashModel;
import org.openrdf.model.vocabulary.RDF;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.config.RepositoryConfig;
import org.openrdf.repository.config.RepositoryConfigException;
import org.openrdf.repository.config.RepositoryConfigSchema;
import org.openrdf.repository.object.ObjectRepository;
import org.openrdf.repository.sail.config.RepositoryResolver;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParser;
import org.openrdf.rio.Rio;
import org.openrdf.rio.helpers.StatementCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ObjectServer implements ObjectServerMXBean, RepositoryResolver {
	private final Logger logger = LoggerFactory.getLogger(ObjectServer.class);
	private final ObjectRepositoryManager manager;
	private final File serverCacheDir;
	private String serverName = ObjectServer.class.getSimpleName();
	private int[] ports = new int[0];
	private int[] sslPorts = new int[0];
	private int timeout;
	private volatile boolean starting;
	private volatile boolean stopping;
	WebServer server;

	public ObjectServer(File dataDir) throws OpenRDFException,
			IOException {
		this(dataDir, ObjectServer.class.getClassLoader());
	}

	public ObjectServer(File dataDir, File cacheDir) throws OpenRDFException,
			IOException {
		this(dataDir, cacheDir, ObjectServer.class.getClassLoader());
	}

	public ObjectServer(File dataDir, ClassLoader cl) throws OpenRDFException,
			IOException {
		this.manager = new ObjectRepositoryManager(dataDir, cl);
		serverCacheDir = DirUtil.createTempDir("object-server-cache");
		DirUtil.deleteOnExit(serverCacheDir);
	}

	public ObjectServer(File dataDir, File cacheDir, ClassLoader cl)
			throws OpenRDFException, IOException {
		this.manager = new ObjectRepositoryManager(dataDir, new File(cacheDir, "lib"), cl);
		serverCacheDir = new File(cacheDir, "server");
	}

	public String toString() {
		try {
			return manager.getLocation().toString();
		} catch (MalformedURLException e) {
			logger.warn(e.toString(), e);
			return manager.toString();
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

	public boolean isStartingInProgress() {
		return starting;
	}

	public boolean isStoppingInProgress() {
		return stopping;
	}

	public boolean isCompilingInProgress() {
		return manager.isCompiling();
	}

	public boolean isRunning() {
		return server != null && server.isRunning();
	}

	public boolean isShutDown() {
		return !manager.isInitialized();
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
			manager.recompileSchema();
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
		for (int i = 0; i < beans.length; i++) {
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

	public RepositoryConnection openSchemaConnection()
			throws RepositoryException {
		return manager.openSchemaConnection();
	}

	public synchronized String[] getRepositoryIDs()
			throws RepositoryException {
		Set<String> ids = new TreeSet<String>(manager.getRepositoryIDs());
		return ids.toArray(new String[ids.size()]);
	}

	public RepositoryMXBean getRepositoryMXBean(String id)
			throws RepositoryConfigException, RepositoryException {
		return new RepositoryMXBeanImpl(manager, id);
	}

	public ObjectRepository getRepository(String id)
			throws RepositoryException, RepositoryConfigException {
		return manager.getObjectRepository(id);
	}

	public String[] getRepositoryPrefixes(String id) throws OpenRDFException {
		return manager.getRepositoryPrefixes(id);
	}

	public synchronized void addRepositoryPrefix(String id, String prefix)
			throws OpenRDFException {
		manager.addRepositoryPrefix(id, prefix);
		if (server != null) {
			startRepository(id);
		}
		notifyAll();
	}

	public synchronized void setRepositoryPrefixes(String id, String[] prefixes)
			throws OpenRDFException {
		if (!manager.isRepositoryPresent(id))
			throw new IllegalArgumentException("Unknown repository ID: " + id);
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
			startRepository(id);
		}
		notifyAll();
	}

	public synchronized String addRepository(String base, String configString)
			throws OpenRDFException, IOException {
		Model graph = parseTurtleGraph(configString, base);
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
			startRepository(id);
		}
		notifyAll();
		return config.getID();
	}

	public synchronized boolean removeRepository(String id)
			throws OpenRDFException {
		if (!manager.isRepositoryPresent(id))
			return false;
		stopRepository(id);
		notifyAll();
		return manager.removeRepository(id);
	}

	public synchronized void init() throws OpenRDFException, IOException {
		stop();
		try {
			if (!manager.isCompiled()) {
				recompileSchema();
			}
			server = createServer(this.serverCacheDir, this.timeout,
					this.ports, this.sslPorts);
			for (String id : manager.getRepositoryIDs()) {
				startRepository(id);
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
			long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
			logger.info("{} started after {} seconds", getServerName(),
					uptime / 1000.0);
		} catch (IOException e) {
			logger.error(e.toString(), e);
			throw e;
		} finally {
			starting = false;
			notifyAll();
		}
	}

	public synchronized void stop() throws IOException, OpenRDFException {
		if (!isRunning())
			return;
		stopping = true;
		try {
			if (server != null) {
				server.stop();
			}
		} finally {
			stopping = false;
			notifyAll();
		}
	}

	public synchronized void destroy() throws IOException, OpenRDFException {
		stop();
		try {
			if (server != null) {
				server.destroy();
				server = null;
			}
		} finally {
			manager.shutDown();
			notifyAll();
		}
	}

	public synchronized void restart() throws IOException, OpenRDFException {
		stop();
		resetConnections();
		if (server != null) {
			server.destroy();
			server = null;
		}
		manager.refresh();
		recompileSchema();
		server = createServer(this.serverCacheDir, this.timeout,
				this.ports, this.sslPorts);
		start();
	}

	private WebServer createServer(File serverCacheDir, int timeout,
			int[] ports, int[] sslPorts) throws OpenRDFException, IOException {
		serverCacheDir.mkdirs();
		WebServer server = new WebServer(serverCacheDir, timeout);
		server.setName(getServerName());
		server.listen(ports, sslPorts);
		return server;
	}

	private synchronized void startRepository(String id)
			throws OpenRDFException {
		String[] prefixes = getRepositoryPrefixes(id);
		if (prefixes != null && prefixes.length > 0) {
			URL url = manager.getRepositoryLocation(id);
			ObjectRepository obj = manager.getObjectRepository(id);
			for (String prefix : prefixes) {
				logger.info("Serving {} from {}", prefix, url);
				server.addRepository(prefix, obj);
			}
		}
	}

	private synchronized void stopRepository(String id) throws OpenRDFException {
		if (manager.isRepositoryPresent(id)) {
			for (String prefix : getRepositoryPrefixes(id)) {
				URL url = manager.getRepositoryLocation(id);
				logger.info("Stop serving {}", prefix, url);
				server.removeRepository(prefix);
			}
		}
	}

	private Model parseTurtleGraph(String configString, String base) throws IOException,
			OpenRDFException {
		Model graph = new LinkedHashModel();
		RDFParser rdfParser = Rio.createParser(RDFFormat.TURTLE);
		final RepositoryConnection con = this.openSchemaConnection();
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
			values.remove("");
			ports = new int[values.size()];
			for (int i = 0; i < values.size(); i++) {
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
