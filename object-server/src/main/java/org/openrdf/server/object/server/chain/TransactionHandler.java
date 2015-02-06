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
package org.openrdf.server.object.server.chain;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.protocol.HttpContext;
import org.openrdf.OpenRDFException;
import org.openrdf.model.ValueFactory;
import org.openrdf.query.QueryLanguage;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.object.ObjectConnection;
import org.openrdf.repository.object.ObjectQuery;
import org.openrdf.repository.object.ObjectRepository;
import org.openrdf.repository.object.RDFObject;
import org.openrdf.server.object.client.CloseableEntity;
import org.openrdf.server.object.client.HttpUriResponse;
import org.openrdf.server.object.io.ChannelUtil;
import org.openrdf.server.object.server.AsyncExecChain;
import org.openrdf.server.object.server.exceptions.InternalServerError;
import org.openrdf.server.object.server.exceptions.ServiceUnavailable;
import org.openrdf.server.object.server.helpers.CalliContext;
import org.openrdf.server.object.server.helpers.CompletedResponse;
import org.openrdf.server.object.server.helpers.Request;
import org.openrdf.server.object.server.helpers.ResourceOperation;
import org.openrdf.server.object.server.helpers.ResponseBuilder;
import org.openrdf.server.object.server.helpers.ResponseCallback;
import org.openrdf.server.object.util.PrefixMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransactionHandler implements AsyncExecChain {
	private static final int ONE_PACKET = 1024;

	private final Logger logger = LoggerFactory.getLogger(ResourceOperation.class);
	private final PrefixMap<ObjectRepository> repositories = new PrefixMap<ObjectRepository>();
	private final AsyncExecChain handler;
	final Executor executor;

	public TransactionHandler(AsyncExecChain handler, Executor executor) {
		this.handler = handler;
		this.executor = executor;
	}

	public synchronized void addRepository(String origin, ObjectRepository repository) {
		repositories.put(origin, repository);
	}

	public synchronized void removeRepository(String origin) {
		repositories.remove(origin);
	}

	public synchronized void removeAllRepositories() {
		repositories.clear();
	}

	@Override
	public Future<HttpResponse> execute(HttpHost target,
			HttpRequest request, HttpContext ctx,
			FutureCallback<HttpResponse> callback) {
		final Request req = new Request(request, ctx);
		ObjectRepository repo = getRepository(req.getRequestURL());
		if (repo == null || !repo.isInitialized())
			return notSetup(req, ctx, callback);
		final CalliContext context = CalliContext.adapt(ctx);
		try {
			final ObjectConnection con = repo.getConnection();
			boolean success = false;
			try {
				con.begin();
				context.setObjectConnection(con);
				RDFObject object = getRequestedObject(con, req.getIRI());
				final ResourceOperation op = new ResourceOperation(object, context);
				context.setResourceTransaction(op);
				Future<HttpResponse> future = handler.execute(target, request, context, new ResponseCallback(callback) {
					public void completed(HttpResponse result) {
						try {
							createSafeHttpEntity(result, con);
							super.completed(result);
						} catch (RepositoryException ex) {
							failed(ex);
						} catch (IOException ex) {
							failed(ex);
						} catch (RuntimeException ex) {
							failed(ex);
						} finally {
							context.setResourceTransaction(null);
							context.setObjectConnection(null);
						}
					}

					public void failed(Exception ex) {
						endTransaction(con);
						context.setResourceTransaction(null);
						context.setObjectConnection(null);
						super.failed(ex);
					}

					public void cancelled() {
						endTransaction(con);
						context.setResourceTransaction(null);
						context.setObjectConnection(null);
						super.cancelled();
					}
				});
				success = true;
				return future;
			} finally {
				if (!success) {
					endTransaction(con);
				}
			}
		} catch (OpenRDFException ex) {
			throw new InternalServerError(ex);
		}
	}

	private synchronized ObjectRepository getRepository(String origin) {
		return repositories.getClosest(origin);
	}

	private RDFObject getRequestedObject(final ObjectConnection con, String iri)
			throws OpenRDFException {
		int start = iri.indexOf("://") + 3;
		StringBuilder sparql = new StringBuilder();
		sparql.append("SELECT ?resource {\n{\n");
		for (int i = iri.length() - 1; i > start; i = iri.lastIndexOf('/', i - 1)) {
			sparql.append("BIND($p").append(i).append(" AS ?resource)\n");
			sparql.append("FILTER EXISTS { ?resource a ?type }\n");
			sparql.append("} UNION {\n");
		}
		sparql.append("BIND ($p0 AS ?resource)\n}\n");
		sparql.append("} LIMIT 1");
		ValueFactory vf = con.getValueFactory();
		ObjectQuery qry = con.prepareObjectQuery(QueryLanguage.SPARQL,
				sparql.toString());
		for (int i = iri.length() - 1; i > start; i = iri.lastIndexOf('/', i - 1)) {
			String path = iri.substring(0, i + 1);
			qry.setBinding("p" + i, vf.createURI(path));
		}
		qry.setBinding("p0", vf.createURI(iri));
		return qry.evaluate(RDFObject.class).singleResult();
	}

	private synchronized Future<HttpResponse> notSetup(Request request,
			HttpContext ctx, FutureCallback<HttpResponse> callback) {
		String msg = "No origins are configured";
		if (!repositories.isEmpty()) {
			String origin = request.getOrigin();
			String closest = closest(origin, repositories.keySet());
			msg = "Origin " + origin
					+ " is not configured, perhaps you wanted " + closest;
		}
		logger.warn(msg);
		ResponseBuilder rb = new ResponseBuilder(request, ctx);
		HttpUriResponse resp = rb.exception(new ServiceUnavailable(msg));
		return new CompletedResponse(callback, resp);
	}

	private String closest(String origin, Set<String> origins) {
		Set<Character> base = toSet(origin.toCharArray());
		String closest = null;
		Set<Character> common = null;
		for (String o : origins) {
			Set<Character> set = toSet(o.toCharArray());
			set.retainAll(base);
			if (common == null || set.size() > common.size()) {
				common = set;
				closest = o;
			}
		}
		return closest;
	}

	private Set<Character> toSet(char[] charArray) {
		Set<Character> set = new HashSet<Character>(charArray.length);
		for (int i = 0; i < charArray.length; i++) {
			set.add(charArray[i]);
		}
		return set;
	}

	void createSafeHttpEntity(HttpResponse resp, ObjectConnection con)
			throws IOException, RepositoryException {
		boolean endNow = true;
		try {
			if (resp.getEntity() != null) {
				int code = resp.getStatusLine().getStatusCode();
				HttpEntity entity = resp.getEntity();
				long length = entity.getContentLength();
				if ((code == 200 || code == 203)
						&& (length < 0 || length > ONE_PACKET)) {
					// chunk stream entity, close store connection later
					resp.setEntity(endEntity(entity, con));
					endNow = false;
				} else {
					// copy entity, close store now
					resp.setEntity(copyEntity(entity, (int) length));
					endNow = true;
				}
			} else {
				// no entity, close store now
				resp.setHeader("Content-Length", "0");
				endNow = true;
			}
		} finally {
			if (endNow) {
				endTransaction(con);
			}
		}
	}

	/**
	 * Request has been fully read and response has been fully written.
	 */
	public void endTransaction(ObjectConnection con) {
		try {
			if (con.isOpen()) {
				con.rollback();
				con.close();
			}
		} catch (RepositoryException e) {
			logger.error(e.toString(), e);
		}
	}

	private ByteArrayEntity copyEntity(HttpEntity entity, int length) throws IOException {
		InputStream in = entity.getContent();
		try {
			if (length < 0) {
				length = ONE_PACKET;
			}
			ByteArrayOutputStream baos = new ByteArrayOutputStream(length);
			ChannelUtil.transfer(in, baos);
			ByteArrayEntity bae = new ByteArrayEntity(baos.toByteArray());
			bae.setContentEncoding(entity.getContentEncoding());
			bae.setContentType(entity.getContentType());
			return bae;
		} finally {
			in.close();
		}
	}

	private CloseableEntity endEntity(HttpEntity entity,
			final ObjectConnection con) {
		return new CloseableEntity(entity, new Closeable() {
			public void close() {
				try {
					executor.execute(new Runnable() {
						public void run() {
							endTransaction(con);
						}
					});
				} catch (RejectedExecutionException ex) {
					endTransaction(con);
				}
			}
		});
	}

}
