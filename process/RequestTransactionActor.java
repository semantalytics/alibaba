package org.callimachusproject.server.process;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.callimachusproject.client.CloseableEntity;
import org.callimachusproject.client.HttpUriResponse;
import org.callimachusproject.concurrent.ManagedExecutors;
import org.callimachusproject.io.ChannelUtil;
import org.callimachusproject.repository.CalliRepository;
import org.callimachusproject.server.exceptions.InternalServerError;
import org.callimachusproject.server.exceptions.ResponseException;
import org.callimachusproject.server.exceptions.ServiceUnavailable;
import org.callimachusproject.server.model.CalliContext;
import org.callimachusproject.server.model.Filter;
import org.callimachusproject.server.model.Handler;
import org.callimachusproject.server.model.Request;
import org.callimachusproject.server.model.ResourceOperation;
import org.callimachusproject.server.model.ResponseBuilder;

public class RequestTransactionActor extends ExchangeActor {
	private static final int ONE_PACKET = 1024;
	private final Filter filter;
	private final Handler handler;

	public RequestTransactionActor(Filter filter, Handler handler) {
		this(new LinkedBlockingDeque<Runnable>(), filter, handler);
	}

	private RequestTransactionActor(BlockingQueue<Runnable> queue,
			Filter filter, Handler handler) {
		super(ManagedExecutors.getInstance().newAntiDeadlockThreadPool(queue,
				"HttpTransaction"), queue);
		this.filter = filter;
		this.handler = handler;
	}

	protected void process(Exchange exchange, boolean foreground)
			throws Exception {
		Request req = exchange.getRequest();
		boolean success = false;
		CalliRepository repo = exchange.getRepository();
		if (repo == null || !repo.isInitialized())
			throw new ServiceUnavailable("This origin is not configured");
		CalliContext context = CalliContext.adapt(exchange.getContext());
		final ResourceOperation op = new ResourceOperation(req, context, repo);
		try {
			context.setResourceTransaction(op);
			op.begin();
			HttpUriResponse resp = handler.verify(op, context);
			if (resp == null) {
				exchange.verified(op.getCredential());
				resp = handler.handle(op, context);
				if (resp.getStatusLine().getStatusCode() >= 400) {
					op.rollback();
				} else if (!exchange.getRequest().isSafe()) {
					op.commit();
				}
			}
			HttpResponse response = createSafeHttpResponse(op, resp, exchange, foreground);
			exchange.submitResponse(filter(req, context, response));
			success = true;
		} finally {
			if (!success) {
				op.endExchange();
			}
			context.setResourceTransaction(null);
		}
	}

	protected HttpResponse filter(Request request, HttpContext context, HttpResponse response)
			throws IOException {
		HttpResponse resp = filter.filter(request, context, response);
		HttpEntity entity = resp.getEntity();
		if ("HEAD".equals(request.getMethod()) && entity != null) {
			EntityUtils.consume(entity);
			resp.setEntity(null);
		}
		return resp;
	}

	private HttpUriResponse createSafeHttpResponse(final ResourceOperation req,
			HttpUriResponse resp, final Exchange exchange, final boolean fore)
			throws Exception {
		boolean endNow = true;
		try {
			if (resp.getEntity() != null) {
				HttpEntity entity = resp.getEntity();
				long length = entity.getContentLength();
				if (length < 0 || length > ONE_PACKET) {
					// chunk stream entity, close store connection later
					resp.setEntity(endEntity(entity, exchange, req, fore));
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
		} catch (ResponseException e) {
			return new ResponseBuilder(req).exception(e);
		} catch (Exception e) {
			return new ResponseBuilder(req).exception(new InternalServerError(e));
		} finally {
			if (endNow) {
				req.endExchange();
			}
		}
		return resp;
	}

	private ByteArrayEntity copyEntity(HttpEntity entity, int length) throws IOException {
		InputStream in = entity.getContent();
		try {
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
			final Exchange exchange, final ResourceOperation req,
			final boolean foreground) {
		return new CloseableEntity(entity, new Closeable() {
			public void close() {
				if (foreground) {
					req.endExchange();
				} else {
					submitEndExchange(new ExchangeTask(exchange) {
						public void run() {
							req.endExchange();
						}
					});
				}
			}
		});
	}

}
