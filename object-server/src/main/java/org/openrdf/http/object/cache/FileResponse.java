/*
 * Copyright (c) 2009, James Leigh All rights reserved.
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

import info.aduna.concurrent.locks.Lock;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.openrdf.http.object.filters.OutputServletStream;

/**
 * A servlet response that stores the message response in a java.io.File.
 */
public class FileResponse extends InMemoryResponseHeader {
	private static AtomicLong seq = new AtomicLong(0);
	private HttpServletResponse response;
	private boolean storable = true;
	private boolean privatecache;
	private String entityTag;
	private boolean notModified = false;
	private OutputServletStream out;
	private ContentMD5Stream md5;
	private boolean committed = false;
	private String method;
	private String url;
	private File dir;
	private File file;
	private Lock lock;

	public FileResponse(String url, HttpServletRequest req, HttpServletResponse res,
			File dir, Lock lock) {
		this.method = req.getMethod();
		this.url = url;
		this.dir = dir;
		this.response = res;
		this.lock = lock;
		privatecache = req.getHeader("Authorization") != null;
	}

	public boolean isCachable() {
		if (storable && !privatecache && entityTag != null)
			return true;
		lock.release();
		return false;
	}

	public boolean isModified() {
		return !notModified;
	}

	public String getMethod() {
		return method;
	}

	public String getUrl() {
		return url;
	}

	public String getEntityTag() {
		return entityTag;
	}

	public File getMessageBody() {
		return file;
	}

	public String getContentMD5() throws UnsupportedEncodingException {
		if (md5 == null)
			return null;
		return md5.getContentMD5();
	}

	@Override
	public void setStatus(int sc, String sm) {
		if (sc == 412 || sc == 304) {
			notModified = true;
		}
		super.setStatus(sc, sm);
	}

	@Override
	public void setStatus(int sc) {
		if (sc == 412 || sc == 304) {
			notModified = true;
		}
		super.setStatus(sc);
	}

	@Override
	public void setHeader(String name, String value) {
		if ("Cache-Control".equalsIgnoreCase(name)) {
			if (value.contains("no-store") || value.contains("private")) {
				storable &= out != null;
			}
			if (value.contains("public")) {
				privatecache = false;
			}
		} else if ("ETag".equalsIgnoreCase(name)) {
			int start = value.indexOf('"');
			int end = value.lastIndexOf('"');
			entityTag = value.substring(start + 1, end);
		}
		super.setHeader(name, value);
	}

	@Override
	public void addHeader(String name, String value) {
		if ("Cache-Control".equalsIgnoreCase(name)) {
			if (value.contains("no-store") || value.contains("private")) {
				storable &= out != null;
			}
			if (value.contains("public")) {
				privatecache = false;
			}
			super.addHeader(name, value);
		} else if ("ETag".equalsIgnoreCase(name)) {
			setHeader(name, value);
		} else {
			super.addHeader(name, value);
		}
	}

	public ServletOutputStream getOutputStream() throws IOException {
		if (isCachable()) {
			assert isModified();
			if (out == null) {
				long id = seq.incrementAndGet();
				String hex = Integer.toHexString(url.hashCode());
				file = new File(dir, "$" + hex + '-' + id + ".part");
				dir.mkdirs();
				FileOutputStream fout = new FileOutputStream(file);
				try {
					md5 = new ContentMD5Stream(fout);
					out = new OutputServletStream(md5);
				} catch (NoSuchAlgorithmException e) {
					out = new OutputServletStream(fout);
				}
			}
			return out;
		} else {
			flushHeaders();
			return response.getOutputStream();
		}
	}

	public PrintWriter getWriter() throws IOException {
		throw new UnsupportedOperationException();
	}

	public void flushBuffer() throws IOException {
		if (!isCachable()) {
			flushHeaders();
			response.flushBuffer();
		} else if (out != null) {
			out.flush();
		}
	}

	public String encodeRedirectUrl(String arg0) {
		return response.encodeRedirectUrl(arg0);
	}

	public String encodeRedirectURL(String arg0) {
		return response.encodeRedirectURL(arg0);
	}

	public String encodeUrl(String arg0) {
		return response.encodeUrl(arg0);
	}

	public String encodeURL(String arg0) {
		return response.encodeURL(arg0);
	}

	public int getBufferSize() {
		return response.getBufferSize();
	}

	public boolean isCommitted() {
		return response.isCommitted();
	}

	public void setBufferSize(int arg0) {
		response.setBufferSize(arg0);
	}

	public String getCharacterEncoding() {
		throw new UnsupportedOperationException();
	}

	public void reset() {
		throw new UnsupportedOperationException();
	}

	public void resetBuffer() {
		throw new UnsupportedOperationException();
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(getStatus()).append(' ').append(getStatusText()).append("\n");
		Map<String, String> map = getHeaders();
		long lastModified = getLastModified();
		long date = getDate();
		for (String header : map.keySet()) {
			sb.append(header).append(": ").append(map.get(header)).append("\n");
		}
		if (lastModified > 0) {
			sb.append("Last-Modified: ").append(new Date(lastModified)).append(
					"\n");
		}
		if (date > 0) {
			sb.append("Date: ").append(new Date(date)).append("\n");
		}
		sb.append("\n");
		return sb.toString();
	}

	private void flushHeaders() {
		if (!committed) {
			committed = true;
			Integer status = getStatus();
			String statusText = getStatusText();
			Map<String, String> map = getHeaders();
			long lastModified = getLastModified();
			long date = getDate();
			if (statusText == null) {
				response.setStatus(status);
			} else {
				response.setStatus(status, statusText);
			}
			for (String header : map.keySet()) {
				response.setHeader(header, map.get(header));
			}
			if (lastModified > 0) {
				response.setDateHeader("Last-Modified", lastModified);
			}
			if (date > 0) {
				response.setDateHeader("Date", date);
			}
		}
	}

}