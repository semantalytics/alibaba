/*
 * Copyright 2009-2010, James Leigh and Zepheira LLC Some rights reserved.
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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.protocol.HttpDateGenerator;
import org.openrdf.http.object.model.Filter;
import org.openrdf.http.object.model.InputStreamHttpEntity;
import org.openrdf.http.object.model.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CachingFilter extends Filter {
	private static AtomicLong seq = new AtomicLong(0);
	private static final HttpDateGenerator DATE_GENERATOR = new HttpDateGenerator();
	private static String hostname;
	static {
		try {
			hostname = InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			hostname = "AliBaba";
		}
	}
	private static String WARN_110 = "110 " + hostname
			+ " \"Response is stale\"";
	private Logger logger = LoggerFactory.getLogger(CachingFilter.class);
	private CacheIndex cache;

	public CachingFilter(Filter delegate, File dataDir, int maxCapacity) {
		super(delegate);
		this.cache = new CacheIndex(dataDir, maxCapacity);
	}

	public int getMaxCapacity() {
		return cache.getMaxCapacity();
	}

	public void setMaxCapacity(int maxCapacity) {
		cache.setMaxCapacity(maxCapacity);
	}

	@Override
	public Request filter(Request request) throws IOException {
		try {
			if (request.isStorable()) {
				CachableRequest req = forCache(request);
				if (req != null)
					return super.filter(req);
			}
		} catch (InterruptedException e) {
			logger.warn(e.getMessage(), e);
		}
		return super.filter(request);
	}

	@Override
	public HttpResponse intercept(Request headers) throws IOException {
		if (headers.isStorable()) {
			try {
				HttpResponse resp = useCache(headers);
				if (resp != null)
					return resp;
			} catch (InterruptedException e) {
				logger.warn(e.getMessage(), e);
				return respond(504, "Gateway Timeout");
			}
		}
		return super.intercept(headers);
	}

	@Override
	public HttpResponse filter(Request request, HttpResponse response)
			throws IOException {
		response = super.filter(request, response);
		try {
			if (request instanceof CachableRequest) {
				CachableRequest cr = (CachableRequest) request;
				HttpResponse resp = saveInCache(cr, response);
				if (resp != null)
					return resp;
			} else {
				invalidate(request);
			}
		} catch (InterruptedException e) {
			logger.warn(e.getMessage(), e);
		}
		return response;
	}

	private HttpResponse respond(int code, String reason) {
		ProtocolVersion ver = new ProtocolVersion("HTTP", 1, 1);
		BasicHttpResponse resp = new BasicHttpResponse(ver, code, reason);
		resp.setHeader("Date", DATE_GENERATOR.getCurrentDate());
		resp.setHeader("Content-Length", "0");
		return resp;
	}

	private CachableRequest forCache(Request headers) throws IOException,
			InterruptedException {
		long now = headers.getReceivedOn();
		CachedEntity cached = null;
		String url = headers.getRequestURL();
		CachedRequest index = cache.findCachedRequest(url);
		Lock lock = index.lock();
		try {
			cached = index.find(headers);
			boolean stale = isStale(cached, headers, now);
			if (stale && !headers.isOnlyIfCache()) {
				String match = index.findCachedETags(headers);
				return new CachableRequest(headers, cached, match);
			}
			return null;
		} finally {
			lock.release();
		}
	}

	private HttpResponse useCache(Request headers) throws IOException,
			InterruptedException {
		long now = headers.getReceivedOn();
		CachedEntity cached = null;
		String url = headers.getRequestURL();
		CachedRequest index = cache.findCachedRequest(url);
		Lock lock = index.lock();
		try {
			cached = index.find(headers);
			boolean stale = isStale(cached, headers, now);
			if (stale && !headers.isOnlyIfCache()) {
				return null;
			} else if (cached == null && headers.isOnlyIfCache()) {
				return respond(504, "Gateway Timeout");
			} else {
				return respondWithCache(now, headers, cached);
			}
		} finally {
			lock.release();
		}
	}

	private HttpResponse saveInCache(CachableRequest headers, HttpResponse res)
			throws IOException, InterruptedException {
		long now = headers.getReceivedOn();
		CachedEntity cached = null;
		String url = headers.getRequestURL();
		CachedRequest index = cache.findCachedRequest(url);
		Lock lock = index.lock();
		try {
			cached = index.find(headers);
			if (isCachable(res)) {
				File body = saveMessageBody(res, index.getDirectory(), url);
				CachedEntity fresh = index.find(headers, res, body);
				fresh.addRequest(headers);
				index.replace(cached, fresh);
				cached = fresh;
				return respondWithCache(now, headers, cached);
			}
		} finally {
			lock.release();
		}
		return null;
	}

	private File saveMessageBody(HttpResponse res, File dir, String url)
			throws FileNotFoundException, IOException {
		HttpEntity entity = res.getEntity();
		if (entity == null)
			return null;
		long id = seq.incrementAndGet();
		String hex = Integer.toHexString(url.hashCode());
		File file = new File(dir, "$" + hex + '-' + id + ".part");
		MessageDigest digest = null;
		try {
			digest = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			logger.debug(e.toString(), e);
		}
		dir.mkdirs();
		FileOutputStream out = new FileOutputStream(file);
		try {
			InputStream in = entity.getContent();
			try {
				int read;
				byte[] buf = new byte[1024];
				while ((read = in.read(buf)) >= 0) {
					out.write(buf, 0, read);
					if (digest != null) {
						digest.update(buf, 0, read);
					}
				}
			} finally {
				in.close();
			}
		} finally {
			out.close();
			entity.consumeContent();
		}
		if (digest != null) {
			byte[] hash = Base64.encodeBase64(digest.digest());
			String contentMD5 = new String(hash, "UTF-8");
			res.setHeader("Content-MD5", contentMD5);
		}
		return file;
	}

	private boolean isCachable(HttpResponse res) {
		for (Header hd : res.getHeaders("Cache-Control")) {
			if (hd.getValue().contains("no-store"))
				return false;
			if (hd.getValue().contains("private"))
				return false;
		}
		return res.containsHeader("ETag");
	}

	private boolean isStale(CachedEntity cached, Request headers, long now)
			throws IOException {
		if (cached == null || headers.isNoCache() || cached.isStale())
			return true;
		int age = cached.getAge(now);
		int lifeTime = cached.getLifeTime();
		int maxage = headers.getMaxAge();
		int minFresh = headers.getMinFresh();
		int maxStale;
		if (cached.mustRevalidate()) {
			maxStale = -1;
		} else {
			maxStale = headers.getMaxStale();
		}
		boolean fresh = age - lifeTime + minFresh <= maxStale;
		return age > maxage || !fresh;
	}

	private HttpResponse respondWithCache(long now, Request req,
			CachedEntity cached) throws IOException, InterruptedException {
		if (req instanceof CachableRequest) {
			req = ((CachableRequest) req).getOriginalRequest();
		}
		int status = cached.getStatus();
		String statusText = cached.getStatusText();
		ProtocolVersion ver = new ProtocolVersion("HTTP", 1, 1);
		BasicHttpResponse res = new BasicHttpResponse(ver, status, statusText);
		boolean unmodifiedSince = unmodifiedSince(req, cached);
		boolean modifiedSince = modifiedSince(req, cached);
		List<Long> range = range(req, cached);
		String method = req.getMethod();
		if (!unmodifiedSince) {
			res.setStatusLine(ver, 412, "Precondition Failed");
		} else if (!modifiedSince
				&& ("GET".equals(method) || "HEAD".equals(method))) {
			res.setStatusLine(ver, 304, "Not Modified");
		} else if (!modifiedSince) {
			res.setStatusLine(ver, 412, "Precondition Failed");
		} else if (status == 200 && range != null && range.isEmpty()) {
			res.setStatusLine(ver, 416, "Requested Range Not Satisfiable");
		} else if (status == 200 && range != null) {
			res.setStatusLine(ver, 206, "Partial Content");
		} else if (statusText == null) {
			res.setStatusCode(status);
		} else {
			res.setStatusLine(ver, status, statusText);
		}
		sendEntityHeaders(now, cached, res);
		if (unmodifiedSince && modifiedSince) {
			sendContentHeaders(cached, res);
			if (range != null) {
				sendRangeBody(method, range, cached, res);
			} else {
				sendMessageBody(method, cached, res);
			}
		} else {
			res.setHeader("Content-Length", "0");
		}
		return res;
	}

	private boolean unmodifiedSince(Request req, CachedEntity cached) {
		try {
			long lastModified = cached.lastModified();
			if (lastModified > 0) {
				long unmodified = req.getDateHeader("If-Unmodified-Since");
				if (unmodified > 0 && lastModified > unmodified)
					return false;
			}
		} catch (IllegalArgumentException e) {
			// invalid date header
		}
		Header[] matchs = req.getHeaders("If-Match");
		boolean mustMatch = matchs != null && matchs.length > 0;
		if (mustMatch) {
			String entityTag = cached.getETag();
			for (Header match : matchs) {
				if (match(entityTag, match.getValue()))
					return true;
			}
		}
		return !mustMatch;
	}

	private boolean modifiedSince(Request req, CachedEntity cached) {
		boolean notModified = false;
		try {
			long lastModified = cached.lastModified();
			if (lastModified > 0) {
				long modified = req.getDateHeader("If-Modified-Since");
				notModified = modified > 0;
				if (notModified && modified < lastModified)
					return true;
			}
		} catch (IllegalArgumentException e) {
			// invalid date header
		}
		Header[] matchs = req.getHeaders("If-None-Match");
		boolean mustMatch = matchs != null && matchs.length > 0;
		if (mustMatch) {
			String entityTag = cached.getETag();
			for (Header match : matchs) {
				if (match(entityTag, match.getValue()))
					return false;
			}
		}
		return !notModified || mustMatch;
	}

	/**
	 * None range request return null. Not satisfiable requests return an empty
	 * list. Satisfiable requests return a list of start and length pairs.
	 */
	private List<Long> range(Request req, CachedEntity cached) {
		if (!cached.isBodyPresent())
			return null;
		String tag = req.getHeader("If-Range");
		if (tag != null) {
			if (tag.startsWith("W/") || tag.charAt(0) == '"') {
				if (!match(cached.getETag(), tag))
					return null;
			} else {
				try {
					long date = req.getDateHeader("If-Range");
					if (cached.lastModified() > date)
						return null;
				} catch (IllegalArgumentException e) {
					// invalid date header
					return null;
				}
			}
		}
		try {
			String range = req.getHeader("Range");
			if (range == null || !range.startsWith("bytes="))
				return null;
			Long length = cached.contentLength();
			List<Long> ranges = new ArrayList<Long>();
			for (String r : range.substring(6).split("\\s*,\\s*")) {
				int idx = r.indexOf('-');
				if (idx == 0) {
					long l = Long.parseLong(r.substring(1));
					if (l > length)
						return null;
					ranges.add(length - l);
					ranges.add(l);
				} else if (idx < 0) {
					long l = Long.parseLong(r);
					if (l >= length)
						return Collections.emptyList();
					ranges.add(l);
					ranges.add(length - l);
				} else {
					long b = Long.parseLong(r.substring(0, idx));
					long e = Long.parseLong(r.substring(idx + 1));
					if (b > e)
						return null;
					if (b >= length)
						return Collections.emptyList();
					if (b == 0 && e + 1 >= length)
						return null;
					ranges.add(b);
					if (e < length) {
						ranges.add(e + 1 - b);
					} else {
						ranges.add(length - b);
					}
				}
			}
			return ranges;
		} catch (Exception e) {
			return null;
		}
	}

	private boolean match(String tag, String match) {
		if (tag == null)
			return false;
		if ("*".equals(match))
			return true;
		if (match.equals(tag))
			return true;
		int md = match.indexOf('-');
		int td = tag.indexOf('-');
		if (td >= 0 && md >= 0)
			return false;
		if (md < 0) {
			md = match.lastIndexOf('"');
		}
		if (td < 0) {
			td = tag.lastIndexOf('"');
		}
		int mq = match.indexOf('"');
		int tq = tag.indexOf('"');
		if (mq < 0 || tq < 0 || md < 0 || td < 0)
			return false;
		return match.substring(mq, md).equals(tag.substring(tq, td));
	}

	private void sendEntityHeaders(long now, CachedEntity cached,
			HttpResponse res) throws IOException {
		int age = cached.getAge(now);
		res.setHeader("Age", Integer.toString(age));
		if (age > cached.getLifeTime()) {
			res.addHeader("Warning", WARN_110);
		}
		String warning = cached.getWarning();
		if (warning != null && warning.length() > 0) {
			res.addHeader("Warning", warning);
		}
		String tag = cached.getETag();
		if (tag != null && tag.length() > 0) {
			res.setHeader("ETag", tag);
		}
		if (cached.lastModified() > 0) {
			res.setHeader("Last-Modified", cached.getLastModified());
		}
		if (cached.date() > 0) {
			res.setHeader("Date", cached.getDate());
		}
		String type = cached.getContentType();
		if (type != null) {
			res.setHeader("Content-Type", type);
		}
	}

	private void sendContentHeaders(CachedEntity cached, HttpResponse res) {
		for (Map.Entry<String, String> e : cached.getContentHeaders()
				.entrySet()) {
			if (e.getValue() != null && e.getValue().length() > 0) {
				res.setHeader(e.getKey(), e.getValue());
			}
		}
	}

	private void sendRangeBody(String method, List<Long> range,
			CachedEntity cached, HttpResponse res) throws IOException,
			InterruptedException {
		if (range.size() == 0)
			return;
		long contentLength = cached.contentLength();
		if (range.size() == 2) {
			long start = range.get(0);
			long length = range.get(1);
			long end = start + length - 1;
			String contentRange = "bytes " + start + "-" + end + "/"
					+ contentLength;
			res.setHeader("Content-Range", contentRange);
			res.setHeader("Content-Length", Long.toString(length));
			if (!"HEAD".equals(method)) {
				String type = null;
				Header hd = res.getFirstHeader("Content-Type");
				if (hd != null) {
					type = hd.getValue();
				}
				InputStream in = cached.writeBody(start, length);
				final Lock inUse = cached.open();
				res.setEntity(new InputStreamHttpEntity(type, length, in,
						new Runnable() {
							public void run() {
								inUse.release();
							}
						}));
			}
		} else {
			String boundary = "THIS_STRING_SEPARATES";
			String type = "multipart/byteranges; boundary=" + boundary;
			res.setHeader("ContentType", type);
			if (!"HEAD".equals(method)) {
				CatInputStream out = new CatInputStream();
				out.print("--");
				out.println(boundary);
				for (int i = 0, n = range.size(); i < n; i += 2) {
					long start = range.get(i);
					long length = range.get(i + 1);
					long end = start + length - 1;
					String ctype = cached.getContentType();
					if (ctype != null) {
						out.print("Content-Type: ");
						out.println(ctype);
					}
					out.print("Content-Length: ");
					out.println(Long.toString(length));
					out.print("Content-Range: bytes ");
					out.print(Long.toString(start));
					out.print("-");
					out.print(Long.toString(end));
					out.print("/");
					out.println(Long.toString(contentLength));
					out.println();
					out.append(cached.writeBody(start, length));
					out.println();
					out.print("--");
					out.println(boundary);
				}
				final Lock inUse = cached.open();
				res.setEntity(new InputStreamHttpEntity(type, -1, out,
						new Runnable() {
							public void run() {
								inUse.release();
							}
						}));
			}
		}
	}

	private void sendMessageBody(String method, CachedEntity cached,
			HttpResponse res) throws IOException, InterruptedException {
		res.setHeader("Accept-Ranges", "bytes");
		String length = cached.getContentLength();
		int size = -1;
		if (length != null) {
			res.setHeader("Content-Length", length);
			size = Integer.parseInt(length);
		}
		if (!"HEAD".equals(method) && cached.isBodyPresent()) {
			String type = null;
			Header hd = res.getFirstHeader("Content-Type");
			if (hd != null) {
				type = hd.getValue();
			}
			InputStream in = cached.writeBody();
			final Lock inUse = cached.open();
			Runnable onClose = new Runnable() {
				public void run() {
					inUse.release();
				}
			};
			res.setEntity(new InputStreamHttpEntity(type, size, in, onClose));
		}
	}

	private void invalidate(Request headers) throws IOException,
			InterruptedException {
		String loc = headers.getResolvedHeader("Location");
		String cloc = headers.getResolvedHeader("Content-Location");
		cache.invalidate(headers.getRequestURL(), loc, cloc);
	}
}
