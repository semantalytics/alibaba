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
package org.openrdf.http.object.behaviours;

import static org.openrdf.query.QueryLanguage.SPARQL;
import info.aduna.net.ParsedURI;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.channels.ReadableByteChannel;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.openrdf.http.object.concepts.HTTPFileObject;
import org.openrdf.http.object.exceptions.BadRequest;
import org.openrdf.http.object.traits.DigestRealm;
import org.openrdf.http.object.util.ChannelUtil;
import org.openrdf.query.MalformedQueryException;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.object.ObjectConnection;
import org.openrdf.repository.object.ObjectQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates HTTP digest authorization.
 */
public abstract class DigestRealmSupport implements DigestRealm, HTTPFileObject {
	private static final int NONCE_LENGTH = 8;
	private static final int MAX_NONCE_AGE = 1000 * 60 * 60 * 12;
	private static KeyPair key;
	static {
		try {
			KeyPairGenerator gen = KeyPairGenerator.getInstance("DSA");
			gen.initialize(512);
			key = gen.generateKeyPair();
		} catch (NoSuchAlgorithmException e) {
			key = null;
		}
	}
	private Logger logger = LoggerFactory.getLogger(DigestRealmSupport.class);

	private static final String SELECT_CRED = "PREFIX :<http://www.openrdf.org/rdf/2009/httpobject#>\n"
			+ "SELECT ?encoded\n"
			+ "WHERE { $this :credential [:name $name; :algorithm \"MD5\"; :encoded ?encoded] }";

	public String allowOrigin() {
		StringBuilder sb = new StringBuilder();
		for (HTTPFileObject origin : getOrigins()) {
			if (sb.length() > 0) {
				sb.append(", ");
			}
			sb.append(origin.toUri());
		}
		return sb.toString();
	}

	public ReadableByteChannel unauthorized() throws IOException {
		String realm = getRealmAuth();
		String nonce = nextNonce();
		String body = "Unauthorized";
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		Writer writer = new OutputStreamWriter(baos, "UTF-8");
		writer.write("HTTP/1.0 401 Unauthorized\r\n");
		writer.write("WWW-Authenticate: Digest realm=\"");
		writer.write(realm);
		writer.write("\", qop=\"auth,auth-int\", nonce=\"");
		writer.write(nonce);
		writer.write("\"\r\n");
		writer.write("Content-Type: text/plain\r\n");
		writer.write("Content-Length: ");
		writer.write(String.valueOf(body.length()));
		writer.write("\r\n\r\n");
		writer.write(body);
		writer.close();
		return ChannelUtil.newChannel(baos.toByteArray());
	}

	public boolean authorize(String format, String algorithm, byte[] encoded,
			String addr, String method) {
		return false;
	}

	public boolean authorize(String format, String algorithm, byte[] encoded,
			String addr, String method, Map<String, String[]> map) {
		String url = map.get("request-target")[0];
		String[] md5 = map.get("content-md5");
		String auth = map.get("authorization")[0];
		if (auth == null || !auth.startsWith("Digest"))
			return false;
		try {
			String string = auth.substring("Digest ".length());
			Map<String, String> options = parseOptions(string);
			if (options == null)
				throw new BadRequest("Invalid digest authorization header");
			String qop = options.get("qop");
			String realm = options.get("realm");
			String uri = options.get("uri");
			String username = options.get("username");
			String nonce = options.get("nonce");
			ParsedURI parsed = new ParsedURI(url);
			String path = parsed.getPath();
			if (parsed.getQuery() != null) {
				path = path + "?" + parsed.getQuery();
			}
			if (nonce == null || username == null || realm == null
					|| !realm.equals(getRealmAuth()) || !url.equals(uri)
					&& !path.equals(uri)) {
				logger.info("Bad authorization on {} {} using {}",
						new Object[] { method, url, auth });
				throw new BadRequest("Bad authorization");
			}
			if (!verify(nonce))
				return false;
			String ha2;
			if ("auth-int".equals(qop)) {
				if (md5 == null || md5.length != 1)
					throw new BadRequest("Missing content-md5");
				byte[] md5sum = Base64.decodeBase64(md5[0].getBytes("UTF-8"));
				char[] hex = Hex.encodeHex(md5sum);
				ha2 = md5(method + ":" + uri + ":" + new String(hex));
			} else {
				ha2 = md5(method + ":" + uri);
			}
			for (byte[] a1 : findDigest(username)) {
				String ha1 = new String(Hex.encodeHex(a1));
				String legacy = ha1 + ":" + nonce + ":" + ha2;
				if (md5(legacy).equals(options.get("response")))
					return true;
				String response = ha1 + ":" + nonce + ":" + options.get("nc")
						+ ":" + options.get("cnonce") + ":" + qop + ":" + ha2;
				if (md5(response).equals(options.get("response")))
					return true;
			}
			return false;
		} catch (BadRequest e) {
			throw e;
		} catch (Exception e) {
			logger.warn(e.toString(), e);
			return false;
		}
	}

	private String nextNonce() {
		StringBuilder sb = new StringBuilder();
		String nonce = Long.toString(System.currentTimeMillis(),
				Character.MAX_RADIX);
		if (nonce.length() < NONCE_LENGTH) {
			for (int i = 0, n = NONCE_LENGTH - nonce.length(); i < n; i++) {
				sb.append('0');
			}
			sb.append(nonce);
		} else if (nonce.length() > NONCE_LENGTH) {
			sb.append(nonce, nonce.length() - NONCE_LENGTH, nonce.length());
		} else {
			sb.append(nonce);
		}
		if (key != null) {
			try {
				Signature sig = Signature.getInstance("DSA");
				sig.initSign(key.getPrivate());
				sig.update(sb.toString().getBytes());
				sb.append(new String(Base64.encodeBase64(sig.sign())));
			} catch (Exception e) {
				logger.error(e.toString(), e);
			}
		}
		return sb.toString();
	}

	private boolean verify(String nonce) {
		if (nonce.length() < NONCE_LENGTH)
			return false;
		String time = nonce.substring(0, NONCE_LENGTH);
		String sign = nonce.substring(NONCE_LENGTH);
		long age = System.currentTimeMillis()
				- Long.valueOf(time, Character.MAX_RADIX);
		if (age > MAX_NONCE_AGE)
			return false;
		if (key == null)
			return true;
		try {
			Signature sig = Signature.getInstance("DSA");
			sig.initVerify(key.getPublic());
			sig.update(time.getBytes());
			return sig.verify(Base64.decodeBase64(sign.getBytes()));
		} catch (Exception e) {
			logger.error(e.toString(), e);
			return true;
		}
	}

	private List<byte[]> findDigest(String username)
			throws MalformedQueryException, RepositoryException,
			QueryEvaluationException {
		ObjectConnection con = getObjectConnection();
		ObjectQuery query = con.prepareObjectQuery(SPARQL, SELECT_CRED);
		query.setObject("name", username);
		return query.evaluate(byte[].class).asList();
	}

	private String md5(String a2) {
		return new String(Hex.encodeHex(DigestUtils.md5(a2)));
	}

	private Map<String, String> parseOptions(String options) {
		Map<String, String> result = new HashMap<String, String>();
		for (String keyvalue : options.split("\\s*,\\s*")) {
			int idx = keyvalue.indexOf('=');
			if (idx < 0)
				return null;
			String key = keyvalue.substring(0, idx);
			if (keyvalue.charAt(idx + 1) == '"') {
				int eq = keyvalue.lastIndexOf('"');
				if (eq <= idx + 2)
					return null;
				String value = keyvalue.substring(idx + 2, eq);
				result.put(key, value);
			} else {
				String value = keyvalue.substring(idx + 1);
				result.put(key, value);
			}
		}
		return result;
	}

}
