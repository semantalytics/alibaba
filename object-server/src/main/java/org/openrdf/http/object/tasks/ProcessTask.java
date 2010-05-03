package org.openrdf.http.object.tasks;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.locks.Lock;

import org.openrdf.http.object.exceptions.Conflict;
import org.openrdf.http.object.model.Filter;
import org.openrdf.http.object.model.Handler;
import org.openrdf.http.object.model.Request;
import org.openrdf.http.object.model.ResourceOperation;
import org.openrdf.http.object.model.Response;
import org.openrdf.http.object.util.FileLockManager;
import org.openrdf.repository.RepositoryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessTask extends Task {
	private Logger logger = LoggerFactory.getLogger(ProcessTask.class);
	private Request request;
	private Filter filter;
	private ResourceOperation req;
	private FileLockManager locks;
	private Handler handler;
	private boolean content;
	private int max;

	public ProcessTask(Request request, Filter filter,
			ResourceOperation operation, FileLockManager locks, Handler handler) {
		this(request, filter, operation, locks, handler, 10);
	}

	public ProcessTask(Request request, Filter filter,
			ResourceOperation operation, FileLockManager locks,
			Handler handler, int max) {
		super(request, filter);
		this.request = request;
		this.filter = filter;
		this.req = operation;
		this.locks = locks;
		this.handler = handler;
		this.max = max;
	}

	@Override
	public int getGeneration() {
		return 2;
	}

	public void perform() throws Exception {
		String method = req.getMethod();
		File file = req.getFile();
		Lock lock = null;
		if (method.equals("PUT") || file != null && file.exists()) {
			boolean shared = method.equals("GET") || method.equals("HEAD")
					|| method.equals("OPTIONS") || method.equals("TRACE")
					|| method.equals("POST") || method.equals("PROPFIND");
			if (shared) {
				lock = locks.lock(file, shared);
			} else {
				lock = locks.tryLock(file, shared);
				if (lock == null && max < 0) {
					req.close();
					submitResponse(new Response().exception(new Conflict()));
					return;
				} else if (lock == null) {
					bear(new ProcessTask(request, filter, req, locks, handler,
							max - 1));
					return;
				}
			}
		}
		try {
			Response resp = handler.handle(req);
			if (req.isSafe() || resp.getStatusCode() >= 400) {
				req.rollback();
			} else {
				req.commit();
			}
			if (resp.isContent() && !resp.isException()) {
				content = true;
				resp.onClose(new Runnable() {
					public void run() {
						try {
							req.close();
						} catch (IOException e) {
							logger.error(e.toString(), e);
						} catch (RepositoryException e) {
							logger.error(e.toString(), e);
						}
					}
				});
				submitResponse(resp);
			} else {
				submitResponse(resp);
			}
		} finally {
			if (lock != null) {
				lock.unlock();
			}
		}
	}

	@Override
	public void abort() {
		super.abort();
		try {
			if (content) {
				req.close();
			}
		} catch (IOException e) {
			logger.error(e.toString(), e);
		} catch (RepositoryException e) {
			logger.error(e.toString(), e);
		}
	}

	@Override
	public void close() {
		super.close();
		try {
			if (!content) {
				req.close();
			}
		} catch (IOException e) {
			logger.error(e.toString(), e);
		} catch (RepositoryException e) {
			logger.error(e.toString(), e);
		}
	}
}