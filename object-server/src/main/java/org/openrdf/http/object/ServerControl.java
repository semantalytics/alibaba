/*
 * Copyright (c) 2012 3 Round Stones Inc., Some Rights Reserved
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
package org.openrdf.http.object;

import info.aduna.io.IOUtil;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.UndeclaredThrowableException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.rmi.UnmarshalException;
import java.util.Arrays;
import java.util.GregorianCalendar;
import java.util.Properties;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ThreadFactory;

import javax.management.InstanceNotFoundException;
import javax.management.JMX;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.Query;
import javax.management.QueryExp;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import javax.xml.datatype.DatatypeFactory;

import org.apache.commons.io.FileUtils;
import org.openrdf.http.object.cli.Command;
import org.openrdf.http.object.cli.CommandSet;
import org.openrdf.http.object.concurrent.ThreadPoolMXBean;
import org.openrdf.http.object.io.ChannelUtil;
import org.openrdf.http.object.management.JVMUsageMBean;
import org.openrdf.http.object.management.ObjectServerMBean;
import org.openrdf.http.object.management.RepositoryMXBean;

/**
 * Command line tool for monitoring and controlling the server.
 * 
 * @author James Leigh
 * 
 */
public class ServerControl {
	private static final String ATTACH_MACHINE = "com.sun.tools.attach.VirtualMachine";
	private static final String REPO_NAME = Server.class.getPackage().getName() + ":*,name=";
	public static final String NAME = Version.getInstance().getVersion();
	private static final String CONNECTOR_ADDRESS = "com.sun.management.jmxremote.localConnectorAddress";
	private static final String ENDPOINT_CONFIG = "@prefix rep: <http://www.openrdf.org/config/repository#>.\n"
			+ "@prefix hr: <http://www.openrdf.org/config/repository/http#>.\n"
			+ "<#{id}> a rep:Repository ;\n"
			+ "   rep:repositoryImpl [ rep:repositoryType 'openrdf:HTTPRepository' ; hr:repositoryURL <{url}> ];\n"
			+ "   rep:repositoryID '{id}'.\n";
							
	private static final ThreadFactory tfactory = new ThreadFactory() {
		public Thread newThread(Runnable r) {
			Thread t = new Thread(r, "ServerControl-Queue" + Integer.toHexString(r.hashCode()));
			t.setDaemon(true);
			return t;
		}
	};

	private static final CommandSet commands = new CommandSet(NAME);
	static {
		commands.option("d", "dataDir").arg("directory")
				.desc("Sesame data dir to 'connect' to");
		commands.option("pid").arg("file")
				.desc("File to read the server process id to monitor");
		commands.option("n", "serverName").optional("name")
				.desc("Web server name");
		commands.option("p", "port").optional("number")
				.desc("HTTP port number");
		commands.option("s", "ssl").optional("number")
				.desc("HTTPS port number");
		commands.option("status").desc("Print status of server and exit");
		commands.option("dump")
				.arg("directory")
				.desc("Use the directory to dump the server status in the given directory");
		commands.option("remove").arg("Endpoint ID")
				.desc("Remove SPARQL endpoint repository from server");
		commands.option("endpoint").arg("SPARQL endpoint URL")
				.desc("Adds or updates SPARQL endpoint URL");
		commands.option("c", "conf").arg("file")
				.desc("The local repository config file to update with");
		commands.option("l", "list").desc("List endpoint IDs");
		commands.option("i", "id").arg("Endpoint ID")
				.desc("Endpoint to modify");
		commands.option("x", "prefix").arg("URL prefix")
				.desc("URL prefix to map to the given endpoint");
		commands.option("u", "update").arg("SPARQL Update")
				.desc("Execute update against endpoint");
		commands.option("q", "query").arg("SPARQL Query")
				.desc("Evaluate query against endpoint");
		commands.option("w", "write").arg("BLOB URI")
				.desc("Store a file into this URI for the endpoint");
		commands.option("r", "read").arg("BLOB URI")
				.desc("Read this URI for from the endpoint into a file");
		commands.option("f", "file").arg("BLOB file")
				.desc("Local file to read or write BLOB content to or from");
		commands.option("reset").desc("Empty any cache on the server");
		commands.option("restart").desc("Restart the server");
		commands.option("stop").desc("Use the PID file to shutdown the server");
		commands.option("h", "help").desc("Print help (this message) and exit");
		commands.option("v", "version").desc(
				"Print version information and exit");
	}

	public static void main(String[] args) {
		try {
			final ServerControl control = new ServerControl();
			Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
				public void run() {
					try {
						control.destroy();
					} catch (Throwable e) {
						println(e);
					}
				}
			}));
			control.init(args);
			control.start();
			control.stop();
			control.destroy();
			System.exit(0);
		} catch (ClassNotFoundException e) {
			System.err.print("Missing jar with: ");
			System.err.println(e.toString());
			System.exit(5);
		} catch (Throwable e) {
			println(e);
			System.err.println("Arguments: " + Arrays.toString(args));
			System.exit(1);
		}
	}

	static void println(Throwable e) {
		Throwable cause = e.getCause();
		if (cause == null && e.getMessage() == null) {
			e.printStackTrace(System.err);
		} else if (cause != null) {
			println(cause);
		} else {
			e.printStackTrace(System.err);
		}
	}

	private Object vm;
	private MBeanServerConnection mbsc;
	private JVMUsageMBean usage;
	private ObjectServerMBean server;
	private Command line;
	private Server internalServer;

	public ServerControl() {
		mbsc = ManagementFactory.getPlatformMBeanServer();
	}

	public ServerControl(File pidFile) throws Exception {
		setPid(IOUtil.readString(pidFile).trim());
	}

	public void init(String... args) {
		try {
			line = commands.parse(args);
			if (line.has("help")) {
				line.printHelp();
				System.exit(0);
				return;
			} else if (line.has("version")) {
				line.printCommandName();
				System.exit(0);
				return;
			} else if (line.isParseError()) {
				line.printParseError();
				System.exit(2);
				return;
			} else if (!line.has("id")
					&& (line.has("update") || line.has("query")
							|| line.has("read") || line.has("write"))) {
				System.err.println("Missing required option: id");
				line.printHelp();
				System.exit(2);
				return;
			} else if (!line.has("file")
					&& (line.has("read") || line.has("write"))) {
				System.err.println("Missing required option: file");
				line.printHelp();
				System.exit(2);
				return;
			} else if (line.has("pid")){
				setPid(IOUtil.readString(new File(line.get("pid"))).trim());
			} else if (line.has("dataDir")) {
				File run = new File(line.get("dataDir"), "run");
				File pidFile = new File(run, "object-server.pid");
				if (pidFile.canRead()) {
					setPid(IOUtil.readString(pidFile).trim());
				} else if (getObjectNames(ObjectServerMBean.class, mbsc).isEmpty()) {
					initInternalServer();
				}
			}
			for (ObjectName name : getObjectNames(ObjectServerMBean.class, mbsc)) {
				server = JMX.newMXBeanProxy(mbsc, name, ObjectServerMBean.class);
			}
			for (ObjectName name : getObjectNames(JVMUsageMBean.class, mbsc)) {
				usage = JMX.newMXBeanProxy(mbsc, name, JVMUsageMBean.class);
			}
			if (server == null) {
				System.err.println("Object server was not found, provide a different pid or dataDir option");
				System.exit(2);
			}
		} catch (Throwable e) {
			println(e);
			System.err.println("Arguments: " + Arrays.toString(args));
			System.exit(1);
		}
	}

	public void start() throws Exception {
		if (line.has("serverName")) {
			String name = line.get("serverName");
			if (name == null || name.length() == 0) {
				System.out.println(server.getServerName());
			} else {
				server.setServerName(name);
			}
		}
		if (line.has("port")) {
			if (line.getAll("port").length > 0) {
				server.setPorts(Arrays.toString(line.getAll("port")));
			} else {
				System.out.println(server.getPorts());
			}
		}
		if (line.has("ssl")) {
			if (line.getAll("ssl").length > 0) {
				server.setSSLPorts(Arrays.toString(line.getAll("ssl")));
			} else {
				System.out.println(server.getSSLPorts());
			}
		}
		if (line.has("status")) {
			System.out.println(server.getStatus());
		}
		if (line.has("dump")) {
			dumpService(line.get("dump") + File.separatorChar);
		}
		if (line.has("remove")) {
			for (String id : line.getAll("remove")) {
				server.removeRepository(id);
			}
		}
		if (line.has("endpoint")) {
			String[] urls = line.getAll("endpoint");
			String[] ids = line.getAll("id");
			if (ids == null || urls.length != ids.length) {
				ids = new String[urls.length];
				for (int i = 0; i < urls.length; i++) {
					String w = urls[i].replaceAll("\\W*$", "").replaceAll(
							".*\\W", "");
					ids[i] = w + Integer.toHexString(urls[i].hashCode());
				}
			}
			for (int i = 0; i < urls.length; i++) {
				System.out.println("Assigning endpoint " + urls[i] + " to ID "
						+ ids[i]);
				String config = ENDPOINT_CONFIG.replace("{id}", ids[i])
						.replace("{url}", urls[i]);
				String base = new File("").toURI().toASCIIString();
				server.addRepository(base, config);
			}
		}
		if (line.has("conf")) {
			String[] files = line.getAll("conf");
			for (int i = 0; i < files.length; i++) {
				File file = new File(files[i]);
				String config = FileUtils.readFileToString(file);
				String base = file.toURI().toASCIIString();
				String id = server.addRepository(base, config);
				System.out.println("Assigning endpoint " + files[i] + " to ID "
						+ id);
			}
		}
		if (line.has("list")) {
			for (String id : server.getRepositoryIDs()) {
				System.out.println(id);
			}
		}
		if (line.has("id") && line.has("prefix")) {
			String[] ids = line.getAll("id");
			String[] prefixes = line.getAll("prefix");
			for (int i = 0; i < prefixes.length; i++) {
				if (i >= ids.length) {
					server.addRepositoryPrefix(ids[ids.length - 1], prefixes[i]);
				} else {
					server.setRepositoryPrefixes(ids[i],
							new String[] { prefixes[i] });
				}
			}
		}
		if (line.has("update")) {
			for (String update : line.getAll("update")) {
				for (String id : line.getAll("id")) {
					QueryExp instanceOf = Query.isInstanceOf(Query.value(RepositoryMXBean.class.getName()));
					Set<ObjectName> names = mbsc.queryNames(new ObjectName(REPO_NAME + id), instanceOf);
					for (ObjectName name : names) {
						RepositoryMXBean repo = JMX.newMXBeanProxy(mbsc, name, RepositoryMXBean.class);
						repo.sparqlUpdate(update);
					}
				}
			}
		}
		if (line.has("query")) {
			for (String query : line.getAll("query")) {
				for (String id : line.getAll("id")) {
					QueryExp instanceOf = Query.isInstanceOf(Query.value(RepositoryMXBean.class.getName()));
					Set<ObjectName> names = mbsc.queryNames(new ObjectName(REPO_NAME + id), instanceOf);
					for (ObjectName name : names) {
						RepositoryMXBean repo = JMX.newMXBeanProxy(mbsc, name, RepositoryMXBean.class);
						for (String line : repo.sparqlQuery(query)) {
							System.out.println(line);
						}
					}
				}
			}
		}
		if (line.has("write")) {
			byte[] content = FileUtils.readFileToByteArray(new File(line.get("file")));
			for (String uri : line.getAll("write")) {
				for (String id : line.getAll("id")) {
					QueryExp instanceOf = Query.isInstanceOf(Query.value(RepositoryMXBean.class.getName()));
					Set<ObjectName> names = mbsc.queryNames(new ObjectName(REPO_NAME + id), instanceOf);
					for (ObjectName name : names) {
						RepositoryMXBean repo = JMX.newMXBeanProxy(mbsc, name, RepositoryMXBean.class);
						repo.storeBinaryBlob(uri, content);
					}
				}
			}
		}
		if (line.has("read")) {
			for (String uri : line.getAll("read")) {
				for (String id : line.getAll("id")) {
					QueryExp instanceOf = Query.isInstanceOf(Query.value(RepositoryMXBean.class.getName()));
					Set<ObjectName> names = mbsc.queryNames(new ObjectName(REPO_NAME + id), instanceOf);
					for (ObjectName name : names) {
						RepositoryMXBean repo = JMX.newMXBeanProxy(mbsc, name, RepositoryMXBean.class);
						byte[] content = repo.readBinaryBlob(uri);
						FileUtils.writeByteArrayToFile(new File(line.get("file")), content);
					}
				}
			}
		}
		if (line.has("reset")) {
			server.resetCache();
		}
		if (line.has("restart")) {
			server.restart();
		}
		if (line.has("stop")) {
			destroyService();
		}
	}

	public void stop() throws Throwable {
		// nothing to stop
	}

	public void destroy() throws Exception {
		if (internalServer != null) {
			internalServer.destroy();
			internalServer = null;
		}
	}

	private boolean destroyService() throws Exception {
		try {
			if (server.isShutDown())
				return false;
			try {
				try {
					server.stop();
				} finally {
					server.destroy();
				}
				info("Server has stopped");
				return true;
			} catch (UndeclaredThrowableException e) {
				if (e.getCause() instanceof InstanceNotFoundException) {
					// remote MBean has unregistered
					info("Server has been destroyed");
					return true;
				}
				throw e;
			} catch (UnmarshalException e) {
				if (e.getCause() instanceof IOException) {
					// remote JVM has terminated
					info("Server has shutdown");
					return true;
				}
				throw e;
			}
		} catch (UndeclaredThrowableException e) {
			try {
				throw e.getCause();
			} catch (Exception cause) {
				throw cause;
			} catch (Throwable cause) {
				throw e;
			}
		}
	}

	private void dumpService(String dir) throws Exception {
		GregorianCalendar now = new GregorianCalendar(
				TimeZone.getTimeZone("UTC"));
		DatatypeFactory df = DatatypeFactory.newInstance();
		String stamp = df.newXMLGregorianCalendar(now).toXMLFormat();
		stamp = stamp.replaceAll("[^0-9]", "");
		if (vm != null) {
			// execute remote VM command
			executeVMCommand(vm, "remoteDataDump", dir + "threads-" + stamp
					+ ".tdump");
			heapDump(vm, dir + "heap-" + stamp + ".hprof");
			executeVMCommand(vm, "heapHisto", dir + "heap-" + stamp + ".histo");
			connectionDump(mbsc, dir + "server-" + stamp + ".csv");
			poolDump(mbsc, dir + "pool-" + stamp + ".tdump");
		}
		// dump info
		usageDump(mbsc, dir + "usage-" + stamp + ".txt");
		netStatistics(dir + "netstat-" + stamp + ".txt");
		topStatistics(dir + "top-" + stamp + ".txt");
	}

	private void setPid(String pid) throws Exception {
		info("Connecting to " + pid);
		vm = getRemoteVirtualMachine(pid);
		mbsc = getMBeanConnection(vm);
	}

	private void heapDump(Object vm, String hprof) throws Exception {
		String[] args = { hprof };
		Method remoteDataDump = vm.getClass().getMethod("dumpHeap",
				Object[].class);
		InputStream in = (InputStream) remoteDataDump.invoke(vm,
				new Object[] { args });
		try {
			ChannelUtil.transfer(in, System.out);
		} finally {
			in.close();
		}
		info(hprof);
	}

	private void executeVMCommand(Object vm, String cmd, String filename,
			String... args) throws Exception {
		Method remoteDataDump = vm.getClass().getMethod(cmd, Object[].class);
		InputStream in = (InputStream) remoteDataDump.invoke(vm,
				new Object[] { args });
		try {
			FileOutputStream out = new FileOutputStream(filename);
			try {
				ChannelUtil.transfer(in, out);
			} finally {
				out.close();
			}
		} finally {
			in.close();
		}
		info(filename);
	}

	private void connectionDump(MBeanServerConnection mbsc, String filename)
			throws MalformedObjectNameException, IOException {
		for (ObjectName name : getObjectNames(ObjectServerMBean.class, mbsc)) {
			ObjectServerMBean server = JMX.newMXBeanProxy(mbsc, name,
					ObjectServerMBean.class);
			server.connectionDumpToFile(filename);
			info(filename);
		}
	}

	private Set<ObjectName> getObjectNames(Class<?> mx,
			MBeanServerConnection mbsc) throws IOException,
			MalformedObjectNameException {
		String pkg = Server.class.getPackage().getName();
		ObjectName name = new ObjectName(pkg + ":*");
		QueryExp instanceOf = Query.isInstanceOf(Query.value(mx.getName()));
		return mbsc.queryNames(name, instanceOf);
	}

	private void poolDump(MBeanServerConnection mbsc, String filename)
			throws MalformedObjectNameException, IOException {
		boolean empty = true;
		for (ObjectName mon : getObjectNames(ThreadPoolMXBean.class, mbsc)) {
			ThreadPoolMXBean pool = JMX.newMXBeanProxy(mbsc, mon,
					ThreadPoolMXBean.class);
			pool.threadDumpToFile(filename);
			empty = false;
		}
		if (!empty) {
			info(filename);
		}
	}

	private void usageDump(MBeanServerConnection mbsc, String filename)
			throws Exception {
		try {
			if (this.usage != null) {
				String[] summary = this.usage.getJVMUsage();
				PrintWriter w = new PrintWriter(filename);
				try {
					for (String line : summary) {
						w.println(line);
					}
				} finally {
					w.close();
				}
				info(filename);
			}
		} catch (UndeclaredThrowableException e) {
			try {
				throw e.getCause();
			} catch (Exception cause) {
				throw cause;
			} catch (Throwable cause) {
				throw e;
			}
		}
	}

	private Object getRemoteVirtualMachine(String pid)
			throws Exception {
		Class<?> VM = loadVirtualMatchineClass();
		Method attach = VM.getDeclaredMethod("attach", String.class);
		// attach to the target application
		info("Connecting to " + pid);
		try {
			return attach.invoke(null, pid);
		} catch (InvocationTargetException e) {
			try {
				throw e.getCause();
			} catch (Exception cause) {
				throw cause;
			} catch (Throwable cause) {
				throw e;
			}
		}
	}

	private Class<?> loadVirtualMatchineClass() throws ClassNotFoundException,
			MalformedURLException {
		try {
			return Class.forName(ATTACH_MACHINE);
		} catch (ClassNotFoundException e) {
			try {
				File jre = new File(System.getProperty("java.home"));
				File jdk = jre.getParentFile();
				File tools = new File(new File(jdk, "lib"), "tools.jar");
				URL url = tools.toURI().toURL();
				URLClassLoader cl = new URLClassLoader(new URL[] { url });
				return Class.forName(ATTACH_MACHINE,
						true, cl);
			} catch (ClassNotFoundException exc) {
				System.err.println("MISSING tools.jar in classpath");
				throw e;
			}
		}
	}

	private void netStatistics(String fileName) throws IOException {
		FileOutputStream out = new FileOutputStream(fileName);
		try {
			// Not all processes could be identified
			if (exec(out, new ByteArrayOutputStream(), "netstat", "-tnpo")) {
				exec(out, System.err, "netstat", "-st");
				info(fileName);
			} else {
				new File(fileName).delete();
			}
		} catch (IOException e) {
			// netstat not installed
			new File(fileName).delete();
		} finally {
			out.close();
		}
	}

	private void topStatistics(String fileName) throws IOException {
		FileOutputStream out = new FileOutputStream(fileName);
		try {
			if (exec(out, System.err, "top", "-bn", "1")) {
				info(fileName);
			} else {
				new File(fileName).delete();
			}
		} catch (IOException e) {
			// top not installed
			new File(fileName).delete();
		} finally {
			out.close();
		}
	}

	private boolean exec(OutputStream stdout, OutputStream stderr, String... command) throws IOException {
		ProcessBuilder process = new ProcessBuilder(command);
		Process p = process.start();
		Thread tin = transfer(p.getInputStream(), stdout);
		Thread terr = transfer(p.getErrorStream(), stderr);
		p.getOutputStream().close();
		try {
			int ret = p.waitFor();
			if (tin != null) {
				tin.join();
			}
			if (terr != null) {
				terr.join();
			}
			return ret == 0;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	private Thread transfer(final InputStream in, final OutputStream out) {
		if (in == null)
			return null;
		Thread thread = tfactory.newThread(new Runnable() {
			public void run() {
				try {
					try {
						int read;
						byte[] buf = new byte[1024];
						while ((read = in.read(buf)) >= 0) {
							out.write(buf, 0, read);
						}
					} finally {
						in.close();
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});
		thread.start();
		return thread;
	}

	private void info(String message) {
		System.err.println(message);
	}

	private MBeanServerConnection getMBeanConnection(Object vm)
			throws Exception {
		Method getAgentProperties = vm.getClass().getMethod(
				"getAgentProperties");
		Method getSystemProperties = vm.getClass().getMethod(
				"getSystemProperties");
		Method loadAgent = vm.getClass().getMethod("loadAgent", String.class);

		// get the connector address
		Properties properties = (Properties) getAgentProperties.invoke(vm);
		String connectorAddress = properties.getProperty(CONNECTOR_ADDRESS);

		// no connector address, so we start the JMX agent
		if (connectorAddress == null) {
			properties = (Properties) getSystemProperties.invoke(vm);
			String agent = properties.getProperty("java.home") + File.separator
					+ "lib" + File.separator + "management-agent.jar";
			loadAgent.invoke(vm, agent);
	
			// agent is started, get the connector address
			properties = (Properties) getAgentProperties.invoke(vm);
			connectorAddress = properties.getProperty(CONNECTOR_ADDRESS);
		}

		JMXServiceURL service = new JMXServiceURL(connectorAddress);
		JMXConnector connector = JMXConnectorFactory.connect(service);
		return connector.getMBeanServerConnection();
	}

	private void initInternalServer() {
		internalServer = new Server();
		internalServer.init("--dataDir", line.get("dataDir"), "--trust");
	}

}
