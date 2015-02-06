/*
 * Copyright (c) 2009-2010, James Leigh and Zepheira LLC Some rights reserved.
 * Copyright (c) 2011 Talis Inc., Some rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * - Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution. 
 * - Neither the name of the openrdf.org nor the names of its contributors may
 *   be used to endorse or promote products derived from this software without
 *   specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * 
 */
package org.openrdf.server.object.helpers;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;

import org.apache.http.HttpException;
import org.apache.http.HttpInetConnection;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestFactory;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpExecutionAware;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.impl.client.cache.FileResourceFactory;
import org.apache.http.impl.execchain.ClientExecChain;
import org.apache.http.impl.nio.DefaultHttpServerIODispatch;
import org.apache.http.impl.nio.DefaultNHttpServerConnectionFactory;
import org.apache.http.impl.nio.SSLNHttpServerConnectionFactory;
import org.apache.http.impl.nio.codecs.DefaultHttpRequestParserFactory;
import org.apache.http.impl.nio.reactor.DefaultListeningIOReactor;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.nio.NHttpConnection;
import org.apache.http.nio.NHttpServerConnection;
import org.apache.http.nio.protocol.HttpAsyncService;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.IOReactorExceptionHandler;
import org.apache.http.nio.reactor.IOReactorStatus;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.nio.reactor.ssl.SSLSetupHandler;
import org.apache.http.nio.util.ByteBufferAllocator;
import org.apache.http.nio.util.HeapByteBufferAllocator;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.ImmutableHttpProcessor;
import org.apache.http.protocol.ResponseConnControl;
import org.apache.http.protocol.ResponseContent;
import org.apache.http.protocol.ResponseDate;
import org.apache.http.util.TextUtils;
import org.openrdf.repository.object.ObjectRepository;
import org.openrdf.server.object.Version;
import org.openrdf.server.object.chain.HeadRequestFilter;
import org.openrdf.server.object.chain.RequestExecChain;
import org.openrdf.server.object.chain.ServerNameFilter;
import org.openrdf.server.object.concurrent.NamedThreadFactory;
import org.openrdf.server.object.util.AnyHttpMethodRequestFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the start and stop stages of the server.
 * 
 * @author James Leigh
 * @param <a>
 * 
 */
public class WebServer implements WebServerMXBean, IOReactorExceptionHandler, ClientExecChain {
	protected static final String DEFAULT_NAME = Version.getInstance().getVersion();
	private static NamedThreadFactory executor = new NamedThreadFactory("WebServer", false);
	private static final Set<WebServer> instances = new HashSet<WebServer>();

	public static WebServer[] getInstances() {
		synchronized (instances) {
			return instances.toArray(new WebServer[instances.size()]);
		}
	}

	public static void resetAllCache() {
		for (WebServer server : getInstances()) {
			server.resetCache();
		}
	}

	final Logger logger = LoggerFactory.getLogger(WebServer.class);
	final Map<NHttpConnection, Boolean> connections = new WeakHashMap<NHttpConnection, Boolean>();
	final DefaultListeningIOReactor server;
	final IOEventDispatch dispatch;
	DefaultListeningIOReactor sslserver;
	IOEventDispatch ssldispatch;
	private int[] ports = new int[0];
	private int[] sslports = new int[0];
	private final ServerNameFilter name;
	private final RequestExecChain chain;
	volatile boolean listening;
	volatile boolean ssllistening;
	private final AsyncRequestHandler service;
	private int timeout = 0;
	private final HttpResponseInterceptor[] interceptors;

	public WebServer(File cacheDir)
			throws IOException, NoSuchAlgorithmException {
		cacheDir.mkdirs();
		chain = new RequestExecChain(new FileResourceFactory(cacheDir));
		service = new AsyncRequestHandler(chain);
		interceptors = new HttpResponseInterceptor[] { new ResponseDate(),
				new ResponseContent(true), new ResponseConnControl(),
				name = new ServerNameFilter(DEFAULT_NAME),
				new HeadRequestFilter() };
		HttpRequestFactory rfactory = new AnyHttpMethodRequestFactory();
		HeapByteBufferAllocator allocator = new HeapByteBufferAllocator();
		IOReactorConfig config = createIOReactorConfig();
		// Create server-side I/O event dispatch
		dispatch = createIODispatch(rfactory, allocator);
		// Create server-side I/O reactor
		server = new DefaultListeningIOReactor(config);
		server.setExceptionHandler(this);
		if (System.getProperty("javax.net.ssl.keyStore") != null) {
			try {
				SSLContext sslcontext = SSLContext.getDefault();
				// Create server-side I/O event dispatch
				ssldispatch = createSSLDispatch(sslcontext, rfactory, allocator);
				// Create server-side I/O reactor
				sslserver = new DefaultListeningIOReactor(config);
				sslserver.setExceptionHandler(this);
			} catch (NoSuchAlgorithmException e) {
				logger.warn(e.toString(), e);
			}
		}
	}

	public synchronized void addRepository(String origin, ObjectRepository repository) {
		chain.addRepository(origin, repository);
	}

	public synchronized void removeRepository(String origin) {
		chain.removeRepository(origin);
	}

	public String getName() {
		return name.getServerName();
	}

	public void setName(String serverName) {
		this.name.setServerName(serverName);
	}

	/**
	 * Defines the keep alive timeout in milliseconds, which is the timeout for
	 * waiting for data. A timeout value of zero is interpreted as an infinite
	 * timeout.
	 */
	public int getTimeout() {
		return timeout;
	}

	/**
	 * Defines the keep alive timeout in milliseconds, which is the timeout for
	 * waiting for data. A timeout value of zero is interpreted as an infinite
	 * timeout.
	 */
	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}

	public void resetCache() {
		chain.resetCache();
	}

	public void resetConnections() throws IOException {
		NHttpConnection[] connections = getOpenConnections();
		for (int i = 0; i < connections.length; i++) {
			connections[i].shutdown();
		}
	}

	public synchronized void listen(int[] ports, int[] sslports)
			throws IOException {
		if (ports == null) {
			ports = new int[0];
		}
		if (sslports == null) {
			sslports = new int[0];
		}
		if (sslports.length > 0 && sslserver == null)
			throw new IllegalStateException(
					"No configured keystore for SSL ports");
		for (int port : diff(this.ports, ports)) {
			server.listen(new InetSocketAddress(port));
		}
		for (int port : diff(this.sslports, sslports)) {
			sslserver.listen(new InetSocketAddress(port));
		}
		if (!isRunning()) {
			if (ports.length > 0) {
				server.pause();
			}
			if (sslserver != null && sslports.length > 0) {
				sslserver.pause();
			}
		}
		this.ports = ports;
		this.sslports = sslports;
		if (ports.length > 0) {
			name.setPort(ports[0]);
		} else if (sslports.length > 0) {
			name.setPort(sslports[0]);
		}
		if (!listening && ports.length > 0) {
			executor.newThread(new Runnable() {
				public void run() {
					try {
						synchronized (WebServer.this) {
							listening = true;
							WebServer.this.notifyAll();
						}
						server.execute(dispatch);
					} catch (IOException e) {
						logger.error(e.toString(), e);
					} finally {
						synchronized (WebServer.this) {
							listening = false;
							WebServer.this.notifyAll();
						}
					}
				}
			}).start();
		}
		if (!ssllistening && sslserver != null && sslports.length > 0) {
			executor.newThread(new Runnable() {
				public void run() {
					try {
						synchronized (WebServer.this) {
							ssllistening = true;
							WebServer.this.notifyAll();
						}
						sslserver.execute(ssldispatch);
					} catch (IOException e) {
						logger.error(e.toString(), e);
					} finally {
						synchronized (WebServer.this) {
							ssllistening = false;
							WebServer.this.notifyAll();
						}
					}
				}
			}).start();
		}
		try {
			synchronized (WebServer.this) {
				while (!listening && ports.length > 0 || !ssllistening
						&& sslserver != null && sslports.length > 0) {
					WebServer.this.wait();
				}
			}
			Thread.sleep(100);
			if (ports.length > 0 && server != null
					&& server.getStatus() != IOReactorStatus.ACTIVE
					|| sslports.length > 0 && sslserver != null
					&& sslserver.getStatus() != IOReactorStatus.ACTIVE) {
				String str = Arrays.toString(ports)
						+ Arrays.toString(sslports);
				str = str.replace('[', ' ').replace(']', ' ');
				throw new BindException("Could not bind to port" + str
						+ "server is " + getStatus());
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		synchronized (instances) {
			instances.add(this);
		}
	}

	public synchronized void start() throws IOException {
		if (ports.length > 0) {
			server.resume();
		}
		if (sslserver != null && sslports.length > 0) {
			sslserver.resume();
		}
	}

	public boolean isRunning() {
		if (ports == null || sslports == null)
			return false;
		if (ports.length > 0 && server.getStatus() == IOReactorStatus.ACTIVE)
			return !chain.isShutdown();
		if (sslports.length > 0 && sslserver != null
				&& sslserver.getStatus() == IOReactorStatus.ACTIVE)
			return !chain.isShutdown();
		return false;
	}

	public synchronized void stop() throws IOException {
		if (ports.length > 0) {
			server.pause();
		}
		if (sslserver != null && sslports.length > 0) {
			sslserver.pause();
		}
	}

	public synchronized void destroy() throws IOException {
		stop();
		chain.shutdown();
		server.shutdown();
		if (sslserver != null) {
			sslserver.shutdown();
		}
		chain.removeAllRepositories();
		resetConnections();
		try {
			synchronized (WebServer.this) {
				while (!listening && !ssllistening) {
					WebServer.this.wait();
				}
			}
			Thread.sleep(100);
			while (server.getStatus() != IOReactorStatus.SHUT_DOWN
					&& server.getStatus() != IOReactorStatus.INACTIVE) {
				Thread.sleep(1000);
				if (isRunning())
					throw new IOException("Could not shutdown Web server");
			}
			if (sslserver != null) {
				while (sslserver.getStatus() != IOReactorStatus.SHUT_DOWN
						&& sslserver.getStatus() != IOReactorStatus.INACTIVE) {
					Thread.sleep(1000);
					if (isRunning())
						throw new IOException("Could not shutdown secure Web server");
				}
			}
			chain.awaitTermination(1000, TimeUnit.MILLISECONDS);
			if (!chain.isTerminated()) {
				throw new IOException("Could not shutdown triage queue server");
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		synchronized (instances) {
			instances.remove(this);
		}
	}

	@Override
	public boolean handle(IOException ex) {
		logger.warn(ex.toString());
		return true;
	}

	@Override
	public boolean handle(RuntimeException ex) {
		logger.error(ex.toString(), ex);
		return true;
	}

	public void poke() {
		System.gc();
		System.runFinalization();
		for (NHttpConnection conn : getOpenConnections()) {
			conn.requestInput();
			conn.requestOutput();
		}
	}

	public String getStatus() {
		StringBuilder sb = new StringBuilder();
		if (ports.length > 0) {
			sb.append(server.getStatus().toString());
		}
		if (ports.length > 0 && sslports.length > 0) {
			sb.append(", ssl: ");
		}
		if (sslserver != null && sslports.length > 0) {
			sb.append(sslserver.getStatus().toString());
		}
		return sb.toString();
	}

	@Override
	public CloseableHttpResponse execute(HttpRoute route,
			HttpRequestWrapper request, HttpClientContext context,
			HttpExecutionAware execAware) throws IOException, HttpException {
		HttpProcessor httpproc = getHttpProcessor();
		httpproc.process(request, context);
		CloseableHttpResponse resp = chain.execute(route, request, context, execAware);
		httpproc.process(resp, context);
		return resp;
	}

	public ConnectionBean[] getConnections() {
		NHttpConnection[] connections = getOpenConnections();
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
					bean.setStatus(bean.getStatus() + " " + remote + "->" + local);
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
				for (int j=0;j<pending.length;j++) {
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
					for(String p : pending) {
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

	private DefaultHttpServerIODispatch createIODispatch(
			HttpRequestFactory requestFactory, ByteBufferAllocator allocator) {
		HttpAsyncService handler;
		DefaultNHttpServerConnectionFactory factory;
		ConnectionConfig params = getDefaultConnectionConfig();
		handler = createProtocolHandler(getHttpProcessor(), service);
		DefaultHttpRequestParserFactory rparser = new DefaultHttpRequestParserFactory(null, requestFactory);
		factory = new DefaultNHttpServerConnectionFactory(allocator, rparser, null, params);
		return new DefaultHttpServerIODispatch(handler, factory);
	}

	private DefaultHttpServerIODispatch createSSLDispatch(
			SSLContext sslcontext, HttpRequestFactory requestFactory,
			ByteBufferAllocator allocator) {
		HttpAsyncService handler;
		SSLNHttpServerConnectionFactory factory;
		ConnectionConfig params = getDefaultConnectionConfig();
		handler = createProtocolHandler(getHttpProcessor(), service);
		DefaultHttpRequestParserFactory rparser = new DefaultHttpRequestParserFactory(null, requestFactory);
		factory = new SSLNHttpServerConnectionFactory(sslcontext, getSSLSetupHandler(), rparser, null, allocator, params);
		return new DefaultHttpServerIODispatch(handler, factory);
	}

	private SSLSetupHandler getSSLSetupHandler() {
		return new SSLSetupHandler() {
			private final String[] supportedProtocols = split(System
					.getProperty("https.protocols"));
			private final String[] supportedCipherSuites = split(System
					.getProperty("https.cipherSuites"));

			@Override
			public void verify(IOSession iosession, SSLSession sslsession)
					throws SSLException {
				// no-op
			}

			@Override
			public void initalize(SSLEngine sslengine) throws SSLException {
				if (supportedProtocols != null) {
					sslengine.setEnabledProtocols(supportedProtocols);
				}
				if (supportedCipherSuites != null) {
					sslengine.setEnabledCipherSuites(supportedCipherSuites);
				}
			}

			private String[] split(final String s) {
				if (TextUtils.isBlank(s)) {
					return null;
				}
				return s.split(" *, *");
			}
		};
	}

	private ImmutableHttpProcessor getHttpProcessor() {
		return new ImmutableHttpProcessor(new HttpRequestInterceptor[0],
				interceptors);
	}

	private ConnectionConfig getDefaultConnectionConfig() {
		return ConnectionConfig.DEFAULT;
	}

	private IOReactorConfig createIOReactorConfig() {
		return IOReactorConfig.custom().setConnectTimeout(timeout)
				.setIoThreadCount(Runtime.getRuntime().availableProcessors())
				.setSndBufSize(8 * 1024).setSoKeepAlive(true)
				.setSoReuseAddress(true).setSoTimeout(timeout)
				.setTcpNoDelay(false).build();
	}

	private HttpAsyncService createProtocolHandler(HttpProcessor httpproc,
			AsyncRequestHandler service) {
		// Create server-side HTTP protocol handler
		HttpAsyncService protocolHandler = new HttpAsyncService(httpproc,
				service) {
			private final Logger logger = LoggerFactory.getLogger(WebServer.class);
	
			@Override
			public void connected(final NHttpServerConnection conn) {
				super.connected(conn);
				synchronized (connections) {
					connections.put(conn, true);
				}
			}
	
			@Override
			public void closed(final NHttpServerConnection conn) {
				synchronized (connections) {
					connections.remove(conn);
				}
				super.closed(conn);
			}

			@Override
			public void exception(NHttpServerConnection conn, Exception cause) {
				try {
					if (cause.getClass().equals(IOException.class)) {
						logger.warn(cause.toString());
					} else {
						logger.warn(cause.toString(), cause);
					}
					super.exception(conn, cause);
				} finally {
					try {
						conn.shutdown();
					} catch (IOException e) {
						log(e);
					}
				}
			}

			@Override
			protected void log(Exception ex) {
				logger.warn(ex.toString(), ex);
			}

		};
		return protocolHandler;
	}

	private Set<Integer> diff(int[] existingPorts, int[] ports) {
		Set<Integer> newPorts = new LinkedHashSet<Integer>(ports.length);
		for (int p : ports) {
			newPorts.add(p);
		}
		for (int p : existingPorts) {
			newPorts.remove(p);
		}
		return newPorts;
	}

	private NHttpConnection[] getOpenConnections() {
		synchronized (connections) {
			return connections.keySet().toArray(new NHttpConnection[connections.size()]);
		}
	}

	private String toString(String string) {
		if (string == null)
			return "";
		return string;
	}

}
