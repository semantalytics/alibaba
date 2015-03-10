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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpInetConnection;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.nio.NHttpConnection;
import org.openrdf.OpenRDFException;
import org.openrdf.http.object.Version;
import org.openrdf.http.object.helpers.Exchange;
import org.openrdf.http.object.helpers.ObjectContext;
import org.openrdf.http.object.io.FileUtil;
import org.openrdf.model.Model;
import org.openrdf.model.Resource;
import org.openrdf.model.impl.LinkedHashModel;
import org.openrdf.model.impl.TreeModel;
import org.openrdf.model.vocabulary.RDF;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.config.RepositoryConfig;
import org.openrdf.repository.config.RepositoryConfigException;
import org.openrdf.repository.config.RepositoryConfigSchema;
import org.openrdf.repository.manager.LocalRepositoryManager;
import org.openrdf.repository.manager.RepositoryInfo;
import org.openrdf.repository.manager.RepositoryManager;
import org.openrdf.repository.manager.RepositoryProvider;
import org.openrdf.repository.manager.SystemRepository;
import org.openrdf.repository.object.ObjectFactory;
import org.openrdf.repository.object.ObjectRepository;
import org.openrdf.repository.object.ObjectService;
import org.openrdf.repository.object.ObjectServiceImpl;
import org.openrdf.repository.object.compiler.OWLCompiler;
import org.openrdf.repository.object.config.ObjectRepositoryConfig;
import org.openrdf.repository.object.config.ObjectRepositoryFactory;
import org.openrdf.repository.object.managers.LiteralManager;
import org.openrdf.repository.object.managers.RoleMapper;
import org.openrdf.repository.object.managers.helpers.RoleClassLoader;
import org.openrdf.repository.sail.config.RepositoryResolver;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParser;
import org.openrdf.rio.Rio;
import org.openrdf.rio.helpers.ContextStatementCollector;
import org.openrdf.rio.helpers.StatementCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ObjectServer implements ObjectServerMXBean, RepositoryResolver {
	private static final Pattern HTTP_DOTALL = Pattern.compile("\\s*\\bhttp.*", Pattern.DOTALL);
	private static final Pattern URL_PATTERN = Pattern
			.compile("https?://[a-zA-Z0-9\\-\\._~%!\\$\\&'\\(\\)\\*\\+,;=:/\\[\\]@]+/(?![a-zA-Z0-9\\-\\._~%!\\$\\&'\\(\\)\\*\\+,;=:/\\?\\#\\[\\]@])");

	private static File createCacheDirectory() throws IOException {
		File cacheDir = FileUtil.createTempDir("http-server-cache");
		FileUtil.deleteOnExit(cacheDir);
		return cacheDir;
	}

	private final Logger logger = LoggerFactory.getLogger(ObjectServer.class);
	private final Map<String, ObjectRepository> repositories = new HashMap<String, ObjectRepository>();
	private final LocalRepositoryManager manager;
	private final File serverCacheDir;
	private final File libDir;
	private final ClassLoader cl;
	private String serverName = ObjectServer.class.getSimpleName();
	private int[] ports = new int[0];
	private int[] sslPorts = new int[0];
	private int timeout;
	private volatile boolean starting;
	private volatile boolean stopping;
	private volatile boolean compiling;
	ObjectService service = new ObjectServiceImpl();
	WebServer server;

	public ObjectServer(File dataDir) throws OpenRDFException,
			IOException {
		this(dataDir, createCacheDirectory(), ObjectServer.class.getClassLoader());
	}

	public ObjectServer(File dataDir, File cacheDir) throws OpenRDFException,
			IOException {
		this(dataDir, cacheDir, ObjectServer.class.getClassLoader());
	}

	public ObjectServer(File dataDir, ClassLoader cl) throws OpenRDFException,
			IOException {
		this(dataDir, createCacheDirectory(), cl);
	}

	public ObjectServer(File dataDir, File cacheDir, ClassLoader cl) throws OpenRDFException,
			IOException {
		this.manager = RepositoryProvider.getRepositoryManager(dataDir);
		serverCacheDir = new File(cacheDir, "server");
		libDir = new File(cacheDir, "lib");
		this.cl = cl;
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
	public synchronized String getServerName() throws IOException {
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

	public synchronized String getPorts() throws IOException {
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

	public synchronized String getSSLPorts() throws IOException {
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
		return compiling;
	}

	public synchronized boolean isRunning() {
		return server != null && server.isRunning();
	}

	public synchronized boolean isShutDown() {
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
			compiling = true;
			service = compileSchema(manager.getSystemRepository());
		} finally {
			compiling = false;
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

	public synchronized SystemRepository getSystemRepository() {
		return manager.getSystemRepository();
	}

	public synchronized String[] getRepositoryIDs()
			throws RepositoryException {
		Set<String> ids = new TreeSet<String>(manager.getRepositoryIDs());
		return ids.toArray(new String[ids.size()]);
	}

	public RepositoryMXBean getRepositoryMXBean(String id)
			throws RepositoryConfigException, RepositoryException {
		return new RepositoryMXBeanImpl(this, id);
	}

	public ObjectRepository getRepository(String id)
			throws RepositoryException, RepositoryConfigException {
		return getObjectRepository(id);
	}

	public String[] getRepositoryPrefixes(String id) throws RepositoryException {
		RepositoryInfo info = manager.getRepositoryInfo(id);
		if (info == null)
			return null;
		String desc = info.getDescription();
		return splitURLs(desc);
	}

	public synchronized void setRepositoryPrefixes(String id, String prefixes)
			throws OpenRDFException {
		RepositoryInfo info = manager.getRepositoryInfo(id);
		if (info == null)
			throw new IllegalArgumentException("Unknown repository ID: " + id);
		RepositoryConfig config = manager.getRepositoryConfig(id);
		Matcher m = HTTP_DOTALL.matcher(config.getTitle());
		config.setTitle(m.replaceAll("\n") + prefixes);
		addRepository(config);
	}

	public synchronized void addRepository(String base, String configString)
			throws OpenRDFException, IOException {
		Model graph = parseTurtleGraph(manager, configString, base);
		Resource node = graph.filter(null, RDF.TYPE,
				RepositoryConfigSchema.REPOSITORY).subjectResource();
		addRepository(RepositoryConfig.create(graph, node));
	}

	public synchronized boolean removeRepository(String id)
			throws OpenRDFException {
		if (!manager.hasRepositoryConfig(id))
			return false;
		stopRepository(id);
		notifyAll();
		return manager.removeRepository(id);
	}

	public synchronized void init() throws OpenRDFException, IOException {
		stop();
		try {
			recompileSchema();
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
			logger.info("{} is binding to port {}", getServerName(),
					getPorts() + ' ' + getSSLPorts());
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
			for (ObjectRepository repo : repositories.values()) {
				repo.shutDown();
			}
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

	private ObjectService compileSchema(Repository repo) throws IOException,
			OpenRDFException {
		libDir.mkdirs();
		File jar = File.createTempFile("model", "", libDir);
		jar.deleteOnExit();
		RoleMapper mapper = new RoleMapper();
		new RoleClassLoader(mapper).loadRoles(cl);
		LiteralManager literals = new LiteralManager(cl);
		OWLCompiler converter = new OWLCompiler(mapper, literals);
		converter.setClassLoader(cl);
		Model schema = new TreeModel();
		RepositoryConnection con = repo.getConnection();
		try {
			ContextStatementCollector collector = new ContextStatementCollector(
					schema, con.getValueFactory());
			con.export(collector);
			converter.setNamespaces(collector.getNamespaces());
			converter.setModel(schema);
		} finally {
			con.close();
		}
		return new ObjectServiceImpl(converter.createJar(jar));
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
			throws RepositoryConfigException, RepositoryException {
		String[] prefixes = getRepositoryPrefixes(id);
		if (prefixes != null && prefixes.length > 0) {
			RepositoryInfo info = manager.getRepositoryInfo(id);
			ObjectRepository obj = getObjectRepository(id);
			for (String prefix : prefixes) {
				logger.info("Serving {} from {}", prefix, info.getLocation());
				server.addRepository(prefix, obj);
			}
		}
	}

	private synchronized void stopRepository(String id)
			throws RepositoryConfigException, RepositoryException {
		RepositoryInfo info = manager.getRepositoryInfo(id);
		if (info != null) {
			for (String prefix : getRepositoryPrefixes(id)) {
				logger.info("Stop serving {}", prefix, info.getLocation());
				server.removeRepository(prefix);
			}
		}
	}

	private synchronized ObjectRepository getObjectRepository(String id)
			throws RepositoryConfigException, RepositoryException {
		if (repositories.containsKey(id))
			return repositories.get(id);
		File dataDir = manager.getRepositoryDir(id);
		Repository repo = manager.getRepository(id);
		ObjectRepositoryFactory factory = new ObjectRepositoryFactory();
		ObjectRepositoryConfig config = new ObjectRepositoryConfig();
		config.setBlobStore(new File(dataDir, "blob").toURI().toASCIIString());
		ObjectRepository obj = factory.createRepository(config, repo);
		obj.setObjectService(new ObjectService() {
			public ObjectFactory createObjectFactory() {
				return service.createObjectFactory();
			}
		});
		repositories.put(id, obj);
		return obj;
	}

	private Model parseTurtleGraph(RepositoryManager manager,
			String configString, String base) throws IOException,
			OpenRDFException {
		Model graph = new LinkedHashModel();
		RDFParser rdfParser = Rio.createParser(RDFFormat.TURTLE);
		Repository repo = manager.getSystemRepository();
		final RepositoryConnection con = repo.getConnection();
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

	private synchronized void addRepository(RepositoryConfig config)
			throws RepositoryException, RepositoryConfigException {
		String id = config.getID();
		if (manager.hasRepositoryConfig(id) && server != null) {
			RepositoryInfo info = manager.getRepositoryInfo(id);
			String[] existing = splitURLs(info.getDescription());
			List<String> removed = new ArrayList<String>(
					Arrays.asList(existing));
			removed.removeAll(Arrays.asList(splitURLs(config.getTitle())));
			for (String rem : removed) {
				server.removeRepository(rem);
			}
		}
		manager.addRepositoryConfig(config);
		if (server != null) {
			startRepository(id);
		}
		notifyAll();
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

	private String[] splitURLs(String desc) {
		List<String> list = new ArrayList<String>();
		Matcher m = desc != null ? URL_PATTERN.matcher(desc) : null;
		if (m != null && m.find()) {
			do {
				list.add(m.group());
			} while (m.find());
		}
		return list.toArray(new String[list.size()]);
	}

	private String toString(String string) {
		if (string == null)
			return "";
		return string;
	}

}
