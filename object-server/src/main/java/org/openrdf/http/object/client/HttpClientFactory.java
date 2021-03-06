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
package org.openrdf.http.object.client;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpClientConnection;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.cache.ResourceFactory;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.conn.ConnectionRequest;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.LayeredConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.client.DefaultConnectionKeepAliveStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.SystemDefaultCredentialsProvider;
import org.apache.http.impl.client.cache.CacheConfig;
import org.apache.http.impl.client.cache.CachingHttpClientBuilder;
import org.apache.http.impl.client.cache.FileResourceFactory;
import org.apache.http.impl.client.cache.ManagedHttpCacheStorage;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.impl.execchain.ClientExecChain;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HttpContext;
import org.openrdf.http.object.Version;

/**
 * Manages the connections and cache entries for outgoing requests.
 * 
 * @author James Leigh
 * 
 */
public class HttpClientFactory implements Closeable {
	private static final String DEFAULT_NAME = Version.getInstance()
			.getVersion();
	static HttpClientFactory instance = new HttpClientFactory();

	public static synchronized HttpClientFactory getInstance() {
		return instance;
	}

	public static synchronized void setCacheDirectory(File dir)
			throws IOException {
		if (instance != null) {
			instance.close();
		}
		instance = new HttpClientFactory(dir);
	}

	final long KEEPALIVE = getClientKeepAliveTimeout();
	final ProxyClientExecDecorator decorator;
	private ResourceFactory entryFactory;
	final PoolingHttpClientConnectionManager connManager;
	private final ConnectionReuseStrategy reuseStrategy;
	private final ConnectionKeepAliveStrategy keepAliveStrategy;

	private HttpClientFactory() {
		decorator = new ProxyClientExecDecorator();
		LayeredConnectionSocketFactory sslSocketFactory = SSLConnectionSocketFactory
				.getSystemSocketFactory();

		connManager = new PoolingHttpClientConnectionManager(RegistryBuilder
				.<ConnectionSocketFactory> create()
				.register("http", PlainConnectionSocketFactory.getSocketFactory())
				.register("https", sslSocketFactory).build());
		connManager.setDefaultSocketConfig(getDefaultSocketConfig());
		connManager.setDefaultConnectionConfig(getDefaultConnectionConfig());
		int max = Integer.parseInt(System.getProperty("http.maxConnections", "20"));
		connManager.setDefaultMaxPerRoute(max);
		connManager.setMaxTotal(2 * max);

		reuseStrategy = DefaultConnectionReuseStrategy.INSTANCE;
		keepAliveStrategy = new ConnectionKeepAliveStrategy() {
			private ConnectionKeepAliveStrategy delegate = DefaultConnectionKeepAliveStrategy.INSTANCE;

			public long getKeepAliveDuration(HttpResponse response,
					HttpContext context) {
				long ret = delegate.getKeepAliveDuration(response, context);
				if (ret > 0)
					return ret;
				return KEEPALIVE;
			}
		};
	}

	private HttpClientFactory(File cacheDir) throws IOException {
		this();
		if (cacheDir != null) {
			cacheDir.mkdirs();
			entryFactory = new FileResourceFactory(cacheDir);
		}
	}

	public synchronized void close() {
		//connManager.shutdown();
	}

	public synchronized ClientExecChain getProxy(HttpHost destination) {
		return decorator.getProxy(destination);
	}

	public synchronized ClientExecChain putProxy(HttpHost destination,
			ClientExecChain proxy) {
		return decorator.putProxy(destination, proxy);
	}

	public synchronized ClientExecChain putProxyIfAbsent(HttpHost destination,
			ClientExecChain proxy) {
		return decorator.putProxyIfAbsent(destination, proxy);
	}

	public synchronized boolean removeProxy(HttpHost destination,
			ClientExecChain proxy) {
		return decorator.removeProxy(destination, proxy);
	}

	public synchronized boolean removeProxy(ClientExecChain proxy) {
		return decorator.removeProxy(proxy);
	}

	public HttpUriClient createHttpClient() {
		return createHttpClient(null, new SystemDefaultCredentialsProvider());
	}

	public synchronized HttpUriClient createHttpClient(CredentialsProvider credentials) {
		return createHttpClient(null, credentials);
	}

	public HttpUriClient createHttpClient(String source) {
		return createHttpClient(source, new SystemDefaultCredentialsProvider());
	}

	public synchronized HttpUriClient createHttpClient(String source,
			CredentialsProvider credentials) {
		CacheConfig cache = getDefaultCacheConfig();
		ManagedHttpCacheStorage storage = entryFactory == null ? null
				: new ManagedHttpCacheStorage(cache);
		List<BasicHeader> headers = new ArrayList<BasicHeader>();
		if (source != null && source.length() > 0) {
			headers.add(new BasicHeader("Origin", getOrigin(source)));
		}
		return new HttpUriClient(new AutoClosingHttpClient(
				createHttpClientBuilder(cache, storage)
						.setConnectionManager(getConnectionManager())
						.setConnectionReuseStrategy(reuseStrategy)
						.setKeepAliveStrategy(keepAliveStrategy)
						.useSystemProperties().disableContentCompression()
						.setDefaultRequestConfig(getDefaultRequestConfig())
						.addInterceptorFirst(new GZipInterceptor())
						.addInterceptorFirst(new GUnzipInterceptor())
						.setDefaultCredentialsProvider(credentials)
						.setDefaultHeaders(headers).setUserAgent(DEFAULT_NAME)
						.build(), storage));
	}

	private HttpClientBuilder createHttpClientBuilder(CacheConfig cache,
			ManagedHttpCacheStorage storage) {
		if (entryFactory == null)
			return new HttpClientBuilder() {
				protected ClientExecChain decorateMainExec(
						ClientExecChain mainExec) {
					return super.decorateMainExec(decorator
							.decorateMainExec(mainExec));
				}
			};
		else
			return new CachingHttpClientBuilder() {
				protected ClientExecChain decorateMainExec(
						ClientExecChain mainExec) {
					return super.decorateMainExec(decorator
							.decorateMainExec(mainExec));
				}
			}.setResourceFactory(entryFactory).setHttpCacheStorage(storage)
					.setCacheConfig(cache);
	}

	private HttpClientConnectionManager getConnectionManager() {
		return new HttpClientConnectionManager() {
			public ConnectionRequest requestConnection(HttpRoute route,
					Object state) {
				return connManager.requestConnection(route, state);
			}

			public void releaseConnection(HttpClientConnection conn,
					Object newState, long validDuration, TimeUnit timeUnit) {
				connManager.releaseConnection(conn, newState, validDuration,
						timeUnit);
			}

			public void connect(HttpClientConnection conn, HttpRoute route,
					int connectTimeout, HttpContext context) throws IOException {
				connManager.connect(conn, route, connectTimeout, context);
			}

			public void upgrade(HttpClientConnection conn, HttpRoute route,
					HttpContext context) throws IOException {
				connManager.upgrade(conn, route, context);
			}

			public void routeComplete(HttpClientConnection conn,
					HttpRoute route, HttpContext context) throws IOException {
				connManager.routeComplete(conn, route, context);
			}

			public void closeIdleConnections(long idletime, TimeUnit tunit) {
				connManager.closeIdleConnections(idletime, tunit);
			}

			public void closeExpiredConnections() {
				connManager.closeExpiredConnections();
			}

			public void shutdown() {
				// connection manager is closed elsewhere
			}
		};
	}

	private String getOrigin(String source) {
		assert source != null;
		int scheme = source.indexOf("://");
		if (scheme < 0 && (source.startsWith("file:") || source.startsWith("jar:file:"))) {
			return "file://";
		} else {
			if (scheme < 0)
				throw new IllegalArgumentException("Not an absolute hierarchical URI: " + source);
			int path = source.indexOf('/', scheme + 3);
			if (path >= 0) {
				return source.substring(0, path);
			} else {
				return source;
			}
		}
	}

	private RequestConfig getDefaultRequestConfig() {
		return RequestConfig.custom().setSocketTimeout(0)
				.setConnectTimeout(10000).setStaleConnectionCheckEnabled(false)
				.setExpectContinueEnabled(true).setMaxRedirects(20)
				.setRedirectsEnabled(true).setCircularRedirectsAllowed(false)
				.build();
	}

	private ConnectionConfig getDefaultConnectionConfig() {
		return ConnectionConfig.custom().setBufferSize(8 * 1024).build();
	}

	private SocketConfig getDefaultSocketConfig() {
		return SocketConfig.custom().setTcpNoDelay(false)
				.setSoTimeout(60 * 1000).build();
	}

	private CacheConfig getDefaultCacheConfig() {
		return CacheConfig.custom().setSharedCache(false)
				.setAllow303Caching(true)
				.setWeakETagOnPutDeleteAllowed(true)
				.setHeuristicCachingEnabled(true)
				.setHeuristicDefaultLifetime(60 * 60 * 24)
				.setMaxObjectSize(1024 * 1024).build();
	}

	private long getClientKeepAliveTimeout() {
		String pkg = HttpClientFactory.class.getPackage().getName();
		String keepAliveTimeout = getProperty(pkg + ".keepAliveTimeout");
		if (keepAliveTimeout != null && Pattern.matches("\\d+", keepAliveTimeout))
			return Math.abs(Long.parseLong(keepAliveTimeout));
		return 4000;
	}

	private String getProperty(String key) {
		try {
			return System.getProperty(key);
		} catch (SecurityException e) {
			e.printStackTrace(System.err);
		}
		return null;
	}
}
