/*
 * Copyright 2013, 3 Round Stones Inc., Some rights reserved.
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
package org.openrdf.http.object.helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.http.HttpResponse;
import org.apache.http.concurrent.FutureCallback;

public class StagedFuture implements Future<HttpResponse>,
		FutureCallback<HttpResponse> {
	private final FutureCallback<HttpResponse> callback;
	private final List<Future<?>> stages = new ArrayList<Future<?>>(4);
	private volatile boolean done;
	private HttpResponse result;
	private Throwable thrown;

	public StagedFuture(FutureCallback<HttpResponse> callback) {
		this.callback = callback;
	}

	public String toString() {
		return String.valueOf(result);
	}

	public void addStage(Future<?> stage) {
		synchronized (stages) {
			stages.add(stage);
		}
		synchronized (this) {
			notifyAll();
		}
	}

	public boolean isCancelled() {
		synchronized (stages) {
			for (Future<?> stage : stages) {
				if (stage.isCancelled())
					return true;
			}
		}
		return false;
	}

	public boolean isDone() {
		synchronized (stages) {
			for (Future<?> stage : stages) {
				if (!stage.isDone())
					return false;
			}
		}
		return this.done;
	}

	public HttpResponse get() throws InterruptedException, ExecutionException {
		waitUntilDone();
		if (thrown != null)
			throw new ExecutionException(thrown);
		return result;
	}

	public HttpResponse get(long timeout, TimeUnit unit)
			throws InterruptedException, ExecutionException, TimeoutException {
		waitUntilDone(timeout, unit);
		if (thrown != null)
			throw new ExecutionException(thrown);
		return result;
	}

	public void completed(final HttpResponse result) {
		try {
			this.result = result;
			if (this.callback != null) {
				this.callback.completed(result);
			}
		} finally {
			done();
		}
	}

	public void failed(final Exception exception) {
		try {
			thrown = exception;
			if (this.callback != null) {
				this.callback.failed(exception);
			}
		} finally {
			done();
		}
	}

	public boolean cancel(final boolean mayInterruptIfRunning) {
		try {
			boolean cancelled = true;
			synchronized (stages) {
				for (Future<?> stage : stages) {
					cancelled &= stage.cancel(mayInterruptIfRunning);
				}
			}
			if (this.callback != null) {
				this.callback.cancelled();
			}
			return cancelled;
		} finally {
			done();
		}
	}

	public boolean cancel() {
		return cancel(true);
	}

	@Override
	public void cancelled() {
		try {
			if (this.callback != null) {
				this.callback.cancelled();
			}
		} finally {
			done();
		}
	}

	private Future<?> getCurrentStage() {
		synchronized (stages) {
			for (Future<?> stage : stages) {
				if (!stage.isDone())
					return stage;
			}
		}
		return null;
	}

	private synchronized void done() {
		if (!this.done) {
			this.done = true;
			notifyAll();
		}
	}

	private void waitUntilDone() throws InterruptedException,
			ExecutionException {
		Future<?> stage = getCurrentStage();
		if (stage == null && !this.done) {
			// all known stages are complete
			// wait for a new stage or for this to complete
			synchronized (this) {
				if (!this.done) {
					wait();
				}
			}
			waitUntilDone(); // check for additional stages
		} else if (stage != null) {
			stage.get(); // wait for current stage to complete
			waitUntilDone(); // wait for another stage to complete
		}
	}

	private void waitUntilDone(long timeout, TimeUnit unit)
			throws InterruptedException, ExecutionException, TimeoutException {
		final long msecs = unit.toMillis(timeout);
		final long startTime = (msecs <= 0) ? 0 : System.currentTimeMillis();
		long waitTime = msecs;
		Future<?> stage = getCurrentStage();
		if (stage == null && !this.done) {
			synchronized (this) {
				if (!this.done) {
					wait(waitTime);
				}
			}
			waitTime = msecs - (System.currentTimeMillis() - startTime);
			if (!this.done && waitTime <= 0)
				throw new TimeoutException();
			waitUntilDone(waitTime, TimeUnit.MILLISECONDS);
		} else if (stage != null) {
			stage.get(waitTime, TimeUnit.MILLISECONDS);
			waitTime = msecs - (System.currentTimeMillis() - startTime);
			if (!this.done && waitTime <= 0)
				throw new TimeoutException();
			waitUntilDone(waitTime, TimeUnit.MILLISECONDS);
		}
	}

}
