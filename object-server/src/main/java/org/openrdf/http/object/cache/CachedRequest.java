/*
 * Copyright 2009-2010, James Leigh and Zepheira Some rights reserved.
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
package org.openrdf.http.object.cache;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.http.HttpResponse;
import org.openrdf.http.object.model.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A set of HTTP request headers that are connected to a particular response entity.
 */
public class CachedRequest {
	private static Logger logger = LoggerFactory.getLogger(CachedRequest.class);

	public static void delete(File dir) {
		File[] files = dir.listFiles();
		if (files == null)
			return;
		for (File file : files) {
			String name = file.getName();
			if (name.endsWith("-head")) {
				File body = new File(dir, name.substring(0, name.length() - 5));
				file.delete();
				body.delete();
			}
		}
		dir.delete();
	}

	public static String getURL(File dir) {
		File[] files = dir.listFiles();
		if (files == null)
			return null;
		for (File file : files) {
			String name = file.getName();
			if (name.endsWith("-head")) {
				try {
					return CachedEntity.getURL(file);
				} catch (IOException e) {
					// try the next one
					logger.warn(e.toString(), e);
				}
			}
		}
		return null;
	}

	private File dir;
	private ReentrantLock locker = new ReentrantLock();
	private List<CachedEntity> responses;

	public CachedRequest(File dir) throws IOException {
		this.dir = dir;
		if (dir.exists()) {
			responses = load(dir);
		} else {
			responses = new LinkedList<CachedEntity>();
		}
	}

	public void delete() {
		for (CachedEntity cached : responses) {
			try {
				cached.delete();
			} catch (InterruptedException e) {
				// leave it
			}
		}
		dir.delete();
	}

	public Lock lock() throws InterruptedException {
		locker.lock();
		return locker;
	}

	public File getDirectory() {
		return dir;
	}

	public CachedEntity find(Request req) {
		String method = req.getMethod();
		if ("HEAD".equals(method)) {
			method = "GET";
		}
		String url = req.getRequestURL();
		Iterator<CachedEntity> iter = responses.iterator();
		while (iter.hasNext()) {
			CachedEntity cached = iter.next();
			if (cached.getMethod().equals(method)
					&& cached.isVariation(req) && cached.getURL().equals(url)) {
				if (cached.isMissing()) {
					iter.remove();
				} else {
					return cached;
				}
			}
		}
		return null;
	}

	public List<CachedEntity> findCachedETags(Request req) {
		String url = req.getRequestURL();
		String method = req.getMethod();
		if ("HEAD".equals(method)) {
			method = "GET";
		}
		List<CachedEntity> list = new ArrayList<CachedEntity>();
		for (CachedEntity cached : responses) {
			if (!cached.isMissing() && cached.getURL().equals(url)
					&& cached.getMethod().equals(method)) {
				list.add(cached);
			}
		}
		return list;
	}

	public CachedEntity find(Request req, HttpResponse response, File tmp) throws IOException,
			InterruptedException {
		String method = req.getMethod();
		String url = req.getRequestURL();
		String value = response.getFirstHeader("ETag").getValue();
		int start = value.indexOf('"');
		int end = value.lastIndexOf('"');
		String entityTag = value.substring(start + 1, end);
		for (CachedEntity cached : responses) {
			if (cached.getEntityTag().equals(entityTag)
					&& cached.getMethod().equals(method)
					&& cached.getURL().equals(url)) {
				cached.setResponse(response, tmp);
				return cached;
			}
		}
		String hex = Integer.toHexString(url.hashCode());
		String name = "$" + method + '-' + hex + '-' + entityTag;
		File body = new File(dir, name);
		File head = new File(dir, name + "-head");
		return new CachedEntity(method, url, response, tmp, head, body);
	}

	public void replace(CachedEntity stale, CachedEntity fresh)
			throws IOException, InterruptedException {
		if (stale == fresh)
			return;
		if (responses.isEmpty()) {
			dir.mkdirs();
		}
		if (stale != null) {
			responses.remove(stale);
			stale.delete();
		}
		if (!responses.contains(fresh)) {
			responses.add(fresh);
		}
	}

	public void stale() throws IOException {
		for (CachedEntity cached : responses) {
			cached.setStale(true);
		}
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (CachedEntity cached : responses) {
			sb.append(cached.toString()).append("\n");
		}
		return sb.toString();
	}

	private List<CachedEntity> load(File dir) {
		List<CachedEntity> responses;
		responses = new LinkedList<CachedEntity>();
		for (File file : dir.listFiles()) {
			String name = file.getName();
			if (name.endsWith("-head")) {
				try {
					File body = new File(dir, name.substring(0,
							name.length() - 5));
					CachedEntity response = new CachedEntity(file, body);
					responses.add(response);
				} catch (Exception e) {
					// skip file
					logger.warn("{} in {}", e.toString(), file);
				}
			}
		}
		return responses;
	}

}
