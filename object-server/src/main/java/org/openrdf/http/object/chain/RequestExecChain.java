/*
 * Copyright (c) 2015 3 Round Stones Inc., Some rights reserved.
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
package org.openrdf.http.object.chain;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.cache.ResourceFactory;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpExecutionAware;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.impl.client.cache.CacheConfig;
import org.apache.http.impl.execchain.ClientExecChain;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.openrdf.http.object.client.HttpUriResponse;
import org.openrdf.http.object.concurrent.ManagedExecutors;
import org.openrdf.http.object.exceptions.BadGateway;
import org.openrdf.http.object.exceptions.GatewayTimeout;
import org.openrdf.http.object.exceptions.ResponseException;
import org.openrdf.http.object.helpers.ObjectContext;
import org.openrdf.http.object.helpers.ResponseBuilder;
import org.openrdf.http.object.util.InlineExecutorService;
import org.openrdf.repository.object.ObjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestExecChain implements AsyncExecChain, ClientExecChain {
	private static final int MAX_QUEUE_SIZE = 32;
	final Logger logger = LoggerFactory.getLogger(RequestExecChain.class);
	private final ThreadLocal<Boolean> foreground = new ThreadLocal<Boolean>();
	private final ExecutorService triaging = new InlineExecutorService(
			foreground, ManagedExecutors.getInstance()
					.newAntiDeadlockThreadPool(
							new ArrayBlockingQueue<Runnable>(MAX_QUEUE_SIZE),
							"HttpTriaging"));
	private final ExecutorService handling = new InlineExecutorService(
			foreground, ManagedExecutors.getInstance()
					.newAntiDeadlockThreadPool(
							new ArrayBlockingQueue<Runnable>(MAX_QUEUE_SIZE),
							"HttpHandling"));
	private final ExecutorService closing = new InlineExecutorService(
			foreground, ManagedExecutors.getInstance().newFixedThreadPool(1,
					"HttpTransactionClosing"));
	private final TransactionHandler transaction;
	final CacheHandler cache;
	private final AsyncExecChain chain;
	final ModifiedSinceHandler remoteCache;

	public RequestExecChain() {
		this(null);
	}

	public RequestExecChain(ResourceFactory factory) {
		// exec in handling thread
		ClientExecChain handler = new InvokeHandler();
		handler = new ContentPeekHandler(handler);
		handler = new NotFoundHandler(handler);
		// exec in triaging thread
		AsyncExecChain filter = new PooledExecChain(handler, handling);
		filter = new GETHeadResponseFilter(filter);
		filter = new ResponseExceptionHandler(filter);
		filter = new ExpectContinueHandler(filter);
		filter = new OptionsHandler(filter);
		filter = new HttpRequestChainInterceptorExecChain(filter);
		filter = new ContentHeadersFilter(filter);
		filter = remoteCache = new ModifiedSinceHandler(filter);
		filter = new UnmodifiedSinceHandler(filter);
		filter = new DerivedFromHeadFilter(filter);
		filter = transaction = new TransactionHandler(filter, closing);
		filter = new GZipFilter(filter);
		// exec in i/o thread
		filter = new PooledExecChain(filter, triaging);
		if (factory == null) {
			cache = null;
		} else {
			filter = cache = new CacheHandler(filter, factory, getDefaultCacheConfig());
		}
		filter = new GUnzipFilter(filter);
		filter = new PingOptionsHandler(filter);
		filter = new SecureChannelFilter(filter);
		chain = filter = new AccessLog(filter);
	}

	public void addRepository(String prefix, ObjectRepository repository) {
		transaction.addRepository(prefix, repository);
	}

	public void removeRepository(String prefix) {
		transaction.removeRepository(prefix);
	}

	public Collection<String> getRepositoryPrefixes() {
		return transaction.getRepositoryPrefixes();
	}

	public ObjectRepository getRepository(String prefix) {
		return transaction.getRepository(prefix);
	}

	public void removeAllRepositories() {
		transaction.removeAllRepositories();
	}

	public void resetCache() {
		ManagedExecutors.getInstance().getTimeoutThreadPool().execute(new Runnable() {
			public String toString() {
				return "reset cache";
			}

			public void run() {
				resetCacheNow();
			}
		});
	}

	public void resetCacheNow() {
		try {
			logger.info("Resetting cache");
			if (cache != null) {
				cache.reset();
			}
			remoteCache.invalidate();
		} catch (Error e) {
			logger.error(e.toString(), e);
		} catch (RuntimeException e) {
			logger.error(e.toString(), e);
		} finally {
			System.gc();
			System.runFinalization();
			logger.debug("Cache reset");
		}
	}

	public boolean isShutdown() {
		return handling.isShutdown() || triaging.isShutdown() || closing.isShutdown();
	}

	public void shutdown() {
		triaging.shutdown();
		handling.shutdown();
		closing.shutdown();
	}

	public boolean isTerminated() {
		return handling.isTerminated() && triaging.isTerminated();
	}

	public boolean awaitTermination(long timeout, TimeUnit unit)
			throws InterruptedException {
		return triaging.awaitTermination(timeout, unit) && handling.awaitTermination(timeout, unit);
	}

	@Override
	public Future<HttpResponse> execute(HttpHost target, HttpRequest request,
			HttpContext context, FutureCallback<HttpResponse> callback) {
		ObjectContext cc = ObjectContext.adapt(new BasicHttpContext(context));
		return chain.execute(target, request, cc, callback);
	}

	@Override
	public CloseableHttpResponse execute(HttpRoute route,
			HttpRequestWrapper request, HttpClientContext context,
			HttpExecutionAware execAware) throws IOException, HttpException {
		HttpResponse response = null;
		Boolean previously = foreground.get();
		try {
			if (previously == null) {
				foreground.set(true);
			}
			HttpHost target = route.getTargetHost();
			ObjectContext cc = ObjectContext.adapt(new BasicHttpContext(context));
			try {
				response = chain.execute(target, request, cc,
						new FutureCallback<HttpResponse>() {
							public void failed(Exception ex) {
								if (ex instanceof RuntimeException) {
									throw (RuntimeException) ex;
								} else {
									throw new BadGateway(ex);
								}
							}

							public void completed(HttpResponse result) {
								// yay!
							}

							public void cancelled() {
								// oops!
							}
						}).get();
			} catch (InterruptedException ex) {
				logger.error(ex.toString(), ex);
				response = new ResponseBuilder(request, cc).exception(new GatewayTimeout(ex));
			} catch (ExecutionException e) {
				Throwable ex = e.getCause();
				logger.error(ex.toString(), ex);
				if (ex instanceof Error) {
					throw (Error) ex;
				} else if (ex instanceof ResponseException) {
					response = new ResponseBuilder(request, cc).exception((ResponseException) ex);
				} else {
					response = new ResponseBuilder(request, cc).exception(new BadGateway(ex));
				}
			} catch (ResponseException ex) {
				response = new ResponseBuilder(request, cc).exception(ex);
			} catch (RuntimeException ex) {
				response = new ResponseBuilder(request, cc).exception(new BadGateway(ex));
			}
			if (response == null) {
				response = new ResponseBuilder(request, cc).exception(new BadGateway());
			}
			if (response instanceof CloseableHttpResponse)
				return (CloseableHttpResponse) response;
			String systemId;
			String uri = request.getRequestLine().getUri();
			if (uri.startsWith("/")) {
				URI net = URI.create(uri);
				URI rewriten = URIUtils.rewriteURI(net, target, true);
				systemId = rewriten.toASCIIString();
			} else {
				systemId = uri;
			}
			return new HttpUriResponse(systemId, response);
		} catch (URISyntaxException e) {
			if (response != null) {
				EntityUtils.consumeQuietly(response.getEntity());
			}
			throw new ClientProtocolException(e);
		} finally {
			if (previously == null) {
				foreground.remove();
			}
		}
	}

	private CacheConfig getDefaultCacheConfig() {
		return CacheConfig.custom().setSharedCache(true)
				.setAllow303Caching(true)
				.setWeakETagOnPutDeleteAllowed(true)
				.setHeuristicCachingEnabled(true)
				.setHeuristicDefaultLifetime(60 * 60 * 24)
				// TODO HTTPCLIENT-1469 5.0: .setAdditionalNotModifiedHeaders("Last-Modified")
				.setMaxObjectSize(1024 * 1024).build();
	}

}
