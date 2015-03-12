/*
 * Portions Copyright (c) 2011-13 3 Round Stones Inc., Some Rights Reserved
 * Portions Copyright (c) 2009-10 Zepheira LLC, Some Rights Reserved
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
package org.openrdf.http.object;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.Arrays;
import java.util.List;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.Query;
import javax.management.QueryExp;

import org.openrdf.OpenRDFException;
import org.openrdf.http.object.cli.Command;
import org.openrdf.http.object.cli.CommandSet;
import org.openrdf.http.object.concurrent.ManagedExecutors;
import org.openrdf.http.object.concurrent.ManagedThreadPool;
import org.openrdf.http.object.concurrent.ManagedThreadPoolListener;
import org.openrdf.http.object.logging.LoggingProperties;
import org.openrdf.http.object.management.JVMUsage;
import org.openrdf.http.object.management.KeyStoreImpl;
import org.openrdf.http.object.management.ObjectServer;
import org.openrdf.http.object.management.RepositoryMXBean;
import org.openrdf.http.object.util.ServerPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Command line tool for launching the server.
 * 
 * @author James Leigh
 * 
 */
public class Server {
	public static final String NAME = Version.getInstance().getVersion();

	private static final CommandSet commands = new CommandSet(NAME);
	static {
		commands.require("d", "dataDir").arg("directory")
				.desc("Sesame data dir to 'connect' to");
		commands.option("n", "serverName").arg("name").desc("Web server name");
		commands.option("p", "port").arg("number").desc("HTTP port number");
		commands.option("s", "ssl").arg("number").desc("HTTPS port number");
		commands.option("trust").desc(
				"Allow all server code to read, write, and execute all files and directories "
						+ "according to the file system's ACL");
		commands.option("pid").arg("file")
				.desc("File to store current process id");
		commands.option("q", "quiet").desc(
				"Don't print status messages to standard output.");
		commands.option("h", "help").desc("Print help (this message) and exit");
		commands.option("v", "version").desc(
				"Print version information and exit");
	}

	public static void main(String[] args) throws IOException, OpenRDFException {
		final Server node = new Server();
		try {
			Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
				public void run() {
					try {
						node.stop();
						node.destroy();
					} catch (Exception e) {
						e.printStackTrace(System.err);
					}
				}
			}));
			node.init(args);
			node.start();
			node.await();
		} catch (Throwable e) {
			while (e.getCause() != null) {
				e = e.getCause();
			}
			System.err.println("Arguments: " + Arrays.toString(args));
			System.err.println(e.toString());
			e.printStackTrace(System.err);
			System.exit(1);
		} finally {
			node.destroy();
		}
	}

	private final Logger logger = LoggerFactory.getLogger(Server.class);
	private String name;
	private ObjectServer node;

	public Server() {
		super();
	}

	public Server(File pidFile) throws IOException {
		RuntimeMXBean bean = ManagementFactory.getRuntimeMXBean();
		String pid = bean.getName().replaceAll("@.*", "");
		FileWriter writer = new FileWriter(pidFile);
		try {
			writer.append(pid);
		} finally {
			writer.close();
		}
	}

	public void init(String... args) {
		try {
			Command line = commands.parse(args);
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
			} else if (line.has("quiet")) {
				try {
					logStdout();
				} catch (SecurityException e) {
					// ignore
				}
			}
			File dataDir = new File(".");
			if (line.has("dataDir")) {
				dataDir = new File(line.get("dataDir"));
			}
			String serverName = line.get("serverName");
			String ports = line.has("port") ? Arrays.toString(line.getAll("port")) : null;
			String ssl = line.has("ssl") ? Arrays.toString(line.getAll("ssl")) : null;
			name = line.has("port") ? line.get("port") : line.has("ssl") ? line.get("ssl") : "0";
			ManagedExecutors.getInstance().addListener(
					new ManagedThreadPoolListener() {
						public void threadPoolStarted(String name,
								ManagedThreadPool pool) {
							registerMBean(pool, mbean(ManagedThreadPool.class, name));

						}

						public void threadPoolTerminated(String name) {
							unregisterMBean(name, ManagedThreadPool.class);
						}
					});
			if (node != null) {
				node.destroy();
			}
			node = new ObjectServer(dataDir);
			node.setServerName(serverName);
			node.setPorts(ports);
			node.setSSLPorts(ssl);
			registerMBean(node, mbean(ObjectServer.class, name));
			registerMBean(new JVMUsage(), mbean(JVMUsage.class));
			LoggingProperties loggingBean = new LoggingProperties();
			registerMBean(loggingBean, mbean(LoggingProperties.class));
			File etc = new File(dataDir, "etc");
			KeyStoreImpl keystore = new KeyStoreImpl(etc);
			registerMBean(keystore, mbean(KeyStoreImpl.class));
			poke();
			node.init();
			storePID(line.get("pid"), dataDir);
			if (!line.has("trust")) {
				ServerPolicy.apply(new String[0], loggingBean
						.getLoggingPropertiesFile(), new File(dataDir,
						"repositories"));
			}
		} catch (Throwable e) {
			while (e.getCause() != null) {
				e = e.getCause();
			}
			System.err.println("Arguments: " + Arrays.toString(args));
			System.err.println(e.toString());
			e.printStackTrace(System.err);
			System.exit(1);
		}
	}

	public void start() throws Exception {
		poke();
		node.start();
	}

	public void stop() throws Exception {
		if (node != null) {
			node.stop();
		}
	}

	public synchronized void destroy() throws IOException, OpenRDFException {
		try {
			if (node != null) {
				node.destroy();
			}
		} finally {
			unregisterMBean(JVMUsage.class);
			unregisterMBean(LoggingProperties.class);
			unregisterMBean(KeyStoreImpl.class);
			ManagedExecutors.getInstance().cleanup();
			MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
			try {
				if (name != null) {
					mbs.unregisterMBean(new ObjectName(mbean(ObjectServer.class, name)));
				}
				QueryExp instanceOf = Query.isInstanceOf(Query.value(RepositoryMXBean.class.getName()));
				ObjectName rn = new ObjectName(getRepositoryMBeanPrefix() + ",*");
				for (ObjectName on : mbs.queryNames(rn, instanceOf)) {
					mbs.unregisterMBean(on);
				}
			} catch (InstanceNotFoundException e) {
				// already unregistered
			} catch (JMException e) {
				logger.error(e.toString(), e);
			}
		}
	}

	public void await() throws InterruptedException, OpenRDFException {
		synchronized (node) {
			node.wait(500); // give server a chance to start endpoint threads
			while (node.isRunning()) {
				poke();
				node.wait();
			}
		}
	}

	public void poke() throws OpenRDFException {
		String repositories = getRepositoryMBeanPrefix();
		MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
		if (node.isShutDown()) {
			try {
				QueryExp instanceOf = Query.isInstanceOf(Query.value(RepositoryMXBean.class.getName()));
				for (ObjectName on : mbs.queryNames(new ObjectName(repositories + ",*"), instanceOf)) {
					mbs.unregisterMBean(on);
				}
				if (name != null) {
					mbs.unregisterMBean(new ObjectName(mbean(ObjectServer.class, name)));
					name = null;
				}
			} catch (JMException e) {
				logger.error(e.toString(), e);
			}
		} else {
			node.poke();
			List<String> active = Arrays.asList(node.getRepositoryIDs());
			try {
				QueryExp instanceOf = Query.isInstanceOf(Query.value(RepositoryMXBean.class.getName()));
				for (ObjectName on : mbs.queryNames(new ObjectName(repositories + ",*"), instanceOf)) {
					if (!active.contains(on.getKeyProperty("name"))) {
						mbs.unregisterMBean(on);
					}
				}
			} catch (JMException e) {
				logger.error(e.toString(), e);
			}
			for (String id : active) {
				try {
					String oname = repositories + ",name=" + id;
					if (!mbs.isRegistered(new ObjectName(oname))) {
						registerMBean(node.getRepositoryMXBean(id), oname);
					}
				} catch (MalformedObjectNameException e) {
					logger.error(e.toString(), e);
				}
			}
		}
	}

	<T> void registerMBean(T bean, String oname) {
		try {
			MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
			mbs.registerMBean(bean, new ObjectName(oname));
		} catch (InstanceAlreadyExistsException e) {
			logger.debug(e.toString(), e);
		} catch (Exception e) {
			logger.error(e.toString(), e);
		}
	}

	void unregisterMBean(Class<?> beanClass) {
		try {
			MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
			ObjectName oname = new ObjectName("*:type=" + beanClass.getSimpleName() + ",*");
			for (Class<?> mx : beanClass.getInterfaces()) {
				if (mx.getName().endsWith("Bean")) {
					QueryExp instanceOf = Query.isInstanceOf(Query.value(beanClass.getName()));
					for (ObjectName on : mbs.queryNames(oname, instanceOf)) {
						mbs.unregisterMBean(on);
					}
				}
			}
		} catch (Exception e) {
			logger.error(e.toString(), e);
		}
	}

	void unregisterMBean(String name, Class<?> beanClass) {
		try {
			MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
			if (mbs.isRegistered(new ObjectName(mbean(beanClass, name)))) {
				mbs.unregisterMBean(new ObjectName(mbean(beanClass, name)));
			}
		} catch (Exception e) {
			// ignore
		}
	}

	String mbean(Class<?> beanClass) {
		String pkg = Server.class.getPackage().getName();
		String simple = beanClass.getSimpleName();
		StringBuilder sb = new StringBuilder();
		sb.append(pkg).append(":type=").append(simple);
		return sb.toString();
	}

	String mbean(Class<?> beanClass, String name) {
		String pkg = Server.class.getPackage().getName();
		String simple = beanClass.getSimpleName();
		StringBuilder sb = new StringBuilder();
		sb.append(pkg).append(":type=").append(simple);
		sb.append(",name=").append(name);
		return sb.toString();
	}

	private void storePID(String pidFile, File dataDir) throws IOException {
		File file;
		if (pidFile != null) {
			file = new File(pidFile);
		} else {
			File run = new File(dataDir, "run");
			file = new File(run, "object-server.pid");
		}
		file.getParentFile().mkdirs();
		file.deleteOnExit();
		RuntimeMXBean bean = ManagementFactory.getRuntimeMXBean();
		String pid = bean.getName().replaceAll("@.*", "");
		FileWriter writer = new FileWriter(file);
		try {
			writer.append(pid);
		} finally {
			writer.close();
		}
	}

	private String getRepositoryMBeanPrefix() {
		String pkg = Server.class.getPackage().getName();
		String simple = ObjectServer.class.getSimpleName();
		StringBuilder sb = new StringBuilder();
		sb.append(pkg).append(":type=").append(simple).append(".").append("Repository");
		sb.append(",").append(simple).append("=").append(name);
		return sb.toString();
	}

	private void logStdout() {
		System.setOut(new PrintStream(new OutputStream() {
			private int ret = "\r".getBytes()[0];
			private int newline = "\n".getBytes()[0];
			private Logger logger = LoggerFactory.getLogger("stdout");
			private ByteArrayOutputStream buffer = new ByteArrayOutputStream();

			public synchronized void write(int b) throws IOException {
				if (b == ret || b == newline) {
					if (buffer.size() > 0) {
						logger.info(buffer.toString());
						buffer.reset();
					}
				} else {
					buffer.write(b);
				}
			}
		}, true));
		System.setErr(new PrintStream(new OutputStream() {
			private int ret = "\r".getBytes()[0];
			private int newline = "\n".getBytes()[0];
			private Logger logger = LoggerFactory.getLogger("stderr");
			private ByteArrayOutputStream buffer = new ByteArrayOutputStream();

			public synchronized void write(int b) throws IOException {
				if (b == ret || b == newline) {
					if (buffer.size() > 0) {
						logger.warn(buffer.toString());
						buffer.reset();
					}
				} else {
					buffer.write(b);
				}
			}
		}, true));
	}
}
