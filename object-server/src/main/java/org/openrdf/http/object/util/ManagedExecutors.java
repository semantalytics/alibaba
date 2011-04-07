/*
 * Copyright 2010, Zepheira LLC Some rights reserved.
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
package org.openrdf.http.object.util;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import org.openrdf.repository.object.util.ManagedScheduledThreadPool;
import org.openrdf.repository.object.util.ManagedThreadPool;

/**
 * Common Executors used.
 * 
 * @author James Leigh
 * 
 */
public class ManagedExecutors {
	private static Executor encoderThreadPool = newCachedPool("Encoder");
	private static Executor parserThreadPool = newCachedPool("Parser");
	private static Executor writerThreadPool = newCachedPool("Writer");
	private static ScheduledExecutorService timeoutThreadPool = newSingleScheduler("Timeout");

	public static Executor getEncoderThreadPool() {
		return encoderThreadPool;
	}

	public static Executor getParserThreadPool() {
		return parserThreadPool;
	}

	public static Executor getWriterThreadPool() {
		return writerThreadPool;
	}

	public static ScheduledExecutorService getTimeoutThreadPool() {
		return timeoutThreadPool;
	}

	public static ExecutorService newCachedPool(String name) {
		return new ManagedThreadPool(name, true);
	}

	public static ScheduledExecutorService newSingleScheduler(String name) {
		return new ManagedScheduledThreadPool(name, true);
	}

	public static Executor newAntiDeadlockThreadPool(
			BlockingQueue<Runnable> queue, String name) {
		return newAntiDeadlockThreadPool(Runtime.getRuntime()
				.availableProcessors() * 2 + 1, Runtime.getRuntime()
				.availableProcessors() * 100, queue, name);
	}

	public static Executor newAntiDeadlockThreadPool(int corePoolSize,
			int maximumPoolSize, BlockingQueue<Runnable> queue, String name) {
		return new AntiDeadlockThreadPool(corePoolSize, maximumPoolSize, queue,
				name);
	}

	private ManagedExecutors() {
		// singleton
	}
}