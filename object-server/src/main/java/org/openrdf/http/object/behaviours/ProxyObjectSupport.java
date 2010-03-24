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
package org.openrdf.http.object.behaviours;

import info.aduna.net.ParsedURI;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.net.URLEncoder;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.http.HttpResponse;
import org.openrdf.http.object.annotations.cacheControl;
import org.openrdf.http.object.annotations.encoding;
import org.openrdf.http.object.annotations.expect;
import org.openrdf.http.object.annotations.header;
import org.openrdf.http.object.annotations.method;
import org.openrdf.http.object.annotations.operation;
import org.openrdf.http.object.annotations.parameter;
import org.openrdf.http.object.annotations.type;
import org.openrdf.http.object.client.RemoteConnection;
import org.openrdf.http.object.exceptions.ResponseException;
import org.openrdf.http.object.readers.FormMapMessageReader;
import org.openrdf.http.object.traits.ProxyObject;
import org.openrdf.http.object.util.ChannelUtil;
import org.openrdf.http.object.util.GenericType;
import org.openrdf.http.object.writers.AggregateWriter;
import org.openrdf.http.object.writers.MessageBodyWriter;
import org.openrdf.repository.object.ObjectConnection;
import org.openrdf.repository.object.ObjectFactory;
import org.openrdf.repository.object.RDFObject;
import org.openrdf.repository.object.annotations.parameterTypes;
import org.openrdf.repository.object.concepts.Message;

public abstract class ProxyObjectSupport implements ProxyObject, RDFObject {
	static final String GET_PROXY_ADDRESS = "getProxyObjectInetAddress";
	private MessageBodyWriter writer = AggregateWriter.getInstance();
	private boolean local;
	private InetSocketAddress addr;

	public void initLocalFileObject(File file, boolean readOnly) {
		local = true;
	}

	@parameterTypes( {})
	public InetSocketAddress getProxyObjectInetAddress(Message msg) {
		if (local)
			return null;
		if (addr != null)
			return addr;
		InetSocketAddress inet = (InetSocketAddress) msg.getFunctionalObjectResponse();
		if (inet != null)
			return addr = inet;
		String uri = getResource().stringValue();
		if (!uri.startsWith("http"))
			return null;
		ParsedURI parsed = new ParsedURI(uri);
		int port = 80;
		if ("http".equalsIgnoreCase(parsed.getScheme())) {
			port = 80;
		} else if ("https".equalsIgnoreCase(parsed.getScheme())) {
			port = 443;
		} else {
			return null;
		}
		String authority = parsed.getAuthority();
		if (authority.contains("@")) {
			authority = authority.substring(authority.indexOf('@') + 1);
		}
		String hostname = authority;
		if (hostname.contains(":")) {
			hostname = hostname.substring(0, hostname.indexOf(':'));
		}
		if (authority.contains(":")) {
			int idx = authority.indexOf(':') + 1;
			port = Integer.parseInt(authority.substring(idx));
		}
		return addr = new InetSocketAddress(hostname, port);
	}

	public Object invokeRemote(Method method, Object[] parameters)
			throws Exception {
		String rm = getRequestMethod(method, parameters);
		String uri = getResource().stringValue();
		String qs = getQueryString(method, parameters);
		Annotation[][] panns = method.getParameterAnnotations();
		int body = getRequestBodyParameterIndex(panns, parameters);
		assert body < 0 || !method.isAnnotationPresent(parameterTypes.class);
		InetSocketAddress addr = getProxyObjectInetAddress();
		RemoteConnection con = openConnection(addr, rm, qs);
		Map<String, List<String>> headers = getHeaders(method, parameters);
		for (Map.Entry<String, List<String>> e : headers.entrySet()) {
			for (String value : e.getValue()) {
				con.addHeader(e.getKey(), value);
			}
		}
		String accept = getAcceptHeader(method, con.getEnvelopeType());
		if (accept != null && !headers.containsKey("accept")) {
			con.addHeader("Accept", accept);
		}
		if (body >= 0) {
			Object result = parameters[body];
			Class<?> ptype = method.getParameterTypes()[body];
			Type gtype = method.getGenericParameterTypes()[body];
			String media = getParameterMediaType(panns[body], ptype, gtype);
			if (!headers.containsKey("content-encoding")) {
				for (Annotation ann : panns[body]) {
					if (ann.annotationType().equals(encoding.class)) {
						for (String value : ((encoding) ann).value()) {
							con.addHeader("Content-Encoding", value);
						}
					}
				}
			}
			con.write(media, ptype, gtype, result);
		}
		int status = con.getResponseCode();
		Class<?> rtype = method.getReturnType();
		if (body < 0 && Set.class.equals(rtype) && status == 404) {
			con.close();
			Type gtype = method.getGenericReturnType();
			Set values = new HashSet();
			ObjectConnection oc = getObjectConnection();
			return new RemoteSetSupport(addr, uri, qs, gtype, values, oc);
		} else if (body < 0 && status == 404) {
			con.close();
			return null;
		} else if (status >= 400) {
			con.close();
			String msg = con.getResponseMessage();
			String stack = con.readString();
			throw ResponseException.create(status, msg, stack);
		} else if (Void.TYPE.equals(rtype)) {
			con.close();
			return null;
		} else if (body < 0 && Set.class.equals(rtype)) {
			Type gtype = method.getGenericReturnType();
			Set values = new HashSet((Set) con.read(gtype, rtype));
			ObjectConnection oc = getObjectConnection();
			return new RemoteSetSupport(addr, uri, qs, gtype, values, oc);
		} else if (rtype.isAssignableFrom(HttpResponse.class)) {
			if (method.isAnnotationPresent(type.class)) {
				String[] types = method.getAnnotation(type.class).value();
				for (String type : types) {
					if (type.equals(con.getEnvelopeType())) {
						return rtype.cast(con.getHttpResponse());
					}
				}
			}
			return con.read(method.getGenericReturnType(), rtype);
		} else {
			return con.read(method.getGenericReturnType(), rtype);
		}
	}

	private Map<String, List<String>> getHeaders(Method method, Object[] param)
			throws Exception {
		Map<String, List<String>> map = new HashMap<String, List<String>>();
		Annotation[][] panns = method.getParameterAnnotations();
		Class<?>[] ptypes = method.getParameterTypes();
		Type[] gtypes = method.getGenericParameterTypes();
		for (int i = 0; i < panns.length; i++) {
			if (param[i] == null)
				continue;
			for (Annotation ann : panns[i]) {
				if (ann.annotationType().equals(header.class)) {
					Charset cs = Charset.forName("ISO-8859-1");
					String m = getParameterMediaType(panns[i], ptypes[i],
							gtypes[i]);
					String txt = m == null ? "text/plain" : m;
					String value = writeToString(txt, ptypes[i], gtypes[i],
							param[i], cs);
					for (String name : ((header) ann).value()) {
						List<String> list = map.get(name.toLowerCase());
						if (list == null) {
							map.put(name.toLowerCase(),
									list = new LinkedList<String>());
						}
						list.add(value);
					}
				}
			}
		}
		if (method.isAnnotationPresent(cacheControl.class)) {
			String[] values = method.getAnnotation(cacheControl.class).value();
			for (String value : values) {
				List<String> list = map.get("cache-control");
				if (list == null) {
					map.put("cache-control", list = new LinkedList<String>());
				}
				list.add(value);
			}
		}
		if (method.isAnnotationPresent(expect.class)) {
			String value = method.getAnnotation(expect.class).value();
			List<String> list = map.get("expect");
			if (list == null) {
				map.put("expect", list = new LinkedList<String>());
			}
			list.add(value);
		}
		if (method.isAnnotationPresent(encoding.class)) {
			String[] values = method.getAnnotation(encoding.class).value();
			for (String value : values) {
				List<String> list = map.get("accept-encoding");
				if (list == null) {
					map.put("accept-encoding", list = new LinkedList<String>());
				}
				list.add(value);
			}
		}
		return map;
	}

	private RemoteConnection openConnection(InetSocketAddress addr,
			String method, String qs) throws IOException {
		String uri = getResource().stringValue();
		ObjectConnection oc = getObjectConnection();
		return new RemoteConnection(addr, method, uri, qs, oc);
	}

	private String getRequestMethod(Method method, Object[] parameters) {
		Class<?> rt = method.getReturnType();
		Annotation[][] panns = method.getParameterAnnotations();
		String rm = getPropertyMethod(rt, panns, parameters);
		if (method.isAnnotationPresent(method.class)) {
			String[] values = method.getAnnotation(method.class).value();
			for (String value : values) {
				if (value.equals(rm))
					return value;
			}
			if (values.length > 0)
				return values[0];
		}
		return rm;
	}

	private String getPropertyMethod(Class<?> rt, Annotation[][] panns,
			Object[] parameters) {
		int body = getRequestBodyParameterIndex(panns, parameters);
		if (!Void.TYPE.equals(rt) && body < 0) {
			return "GET";
		}
		if (Void.TYPE.equals(rt) && body >= 0) {
			if (parameters[body] == null)
				return "DELETE";
			return "PUT";
		}
		return "POST";
	}

	private String getQueryString(Method method, Object[] param)
			throws Exception {
		Map<String, String[]> map = new LinkedHashMap<String, String[]>();
		Class<?>[] ptypes = method.getParameterTypes();
		Type[] gtypes = method.getGenericParameterTypes();
		Annotation[][] panns = method.getParameterAnnotations();
		for (int i = 0; i < panns.length; i++) {
			if (param[i] == null)
				continue;
			for (Annotation ann : panns[i]) {
				if (parameter.class.equals(ann.annotationType())) {
					String name = ((parameter) ann).value()[0];
					append(ptypes[i], gtypes[i], panns[i], name, param[i], map);
				}
			}
		}
		StringBuilder sb = new StringBuilder();
		if (method.isAnnotationPresent(operation.class)) {
			String[] values = method.getAnnotation(operation.class).value();
			if (values.length > 0) {
				sb.append(enc(values[0]));
			}
		}
		for (String name : map.keySet()) {
			for (String value : map.get(name)) {
				if (sb.length() > 0) {
					sb.append("&");
				}
				sb.append(enc(name)).append("=").append(enc(value));
			}
		}
		if (sb.length() == 0)
			return null;
		return sb.toString();
	}

	private void append(Class<?> ptype, Type gtype, Annotation[] panns,
			String name, Object param, Map<String, String[]> map)
			throws Exception {
		GenericType<?> type = new GenericType(ptype, gtype);
		Class<?> cc = type.getComponentClass();
		Type ctype = type.getComponentType();
		String m = getParameterMediaType(panns, ptype, gtype);
		Charset cs = Charset.forName("ISO-8859-1");
		if ("*".equals(name)) {
			String form = m == null ? "application/x-www-form-urlencoded" : m;
			ReadableByteChannel in = write(form, ptype, gtype, param, cs);
			FormMapMessageReader reader = new FormMapMessageReader();
			ObjectConnection con = getObjectConnection();
			Class<Map> mt = Map.class;
			Map f = reader.readFrom(mt, mt, m, in, cs, null, null, con);
			for (Object key : f.keySet()) {
				if (!map.containsKey(key)) {
					map.put((String) key, (String[]) f.get(key));
				}
			}
		} else if (type.isSet()) {
			List<String> values = new ArrayList<String>();
			String txt = m == null ? "text/plain" : m;
			for (Object o : (Set) param) {
				values.add(writeToString(txt, cc, ctype, o, cs));
			}
			map.put(name, values.toArray(new String[values.size()]));
		} else if (type.isArray()) {
			String txt = m == null ? "text/plain" : m;
			int len = Array.getLength(param);
			String[] values = new String[len];
			for (int i = 0; i < len; i++) {
				values[i] = writeToString(txt, cc, ctype, Array.get(param, i),
						cs);
			}
			map.put(name, values);
		} else {
			String txt = m == null ? "text/plain" : m;
			String value = writeToString(txt, ptype, gtype, param, cs);
			map.put(name, new String[] { value });
		}
	}

	private String getParameterMediaType(Annotation[] anns, Class<?> ptype,
			Type gtype) {
		ObjectFactory of = getObjectConnection().getObjectFactory();
		for (Annotation ann : anns) {
			if (ann.annotationType().equals(type.class)) {
				for (String media : ((type) ann).value()) {
					if (writer.isWriteable(media, ptype, gtype, of))
						return media;
				}
			}
		}
		return null;
	}

	private ReadableByteChannel write(String mediaType, Class<?> ptype,
			Type gtype, Object result, Charset charset) throws Exception {
		String uri = getResource().stringValue();
		ObjectFactory of = getObjectConnection().getObjectFactory();
		return writer.write(mediaType, ptype, gtype, of, result, uri, charset);
	}

	private String writeToString(String mediaType, Class<?> ptype, Type gtype,
			Object result, Charset cs) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ReadableByteChannel in = write(mediaType, ptype, gtype, result, cs);
		try {
			ChannelUtil.transfer(in, out);
		} finally {
			in.close();
		}
		return out.toString(cs.name());
	}

	private String enc(String value) throws AssertionError {
		try {
			return URLEncoder.encode(value, "ISO-8859-1");
		} catch (UnsupportedEncodingException e) {
			throw new AssertionError(e);
		}
	}

	private String getAcceptHeader(Method method, String ignore) {
		if (method.isAnnotationPresent(type.class)) {
			String[] types = method.getAnnotation(type.class).value();
			if (types.length == 1 && types[0].equals(ignore))
				return "*/*";
			if (types.length == 1)
				return types[0];
			StringBuilder sb = new StringBuilder();
			for (String type : types) {
				if (type.equals(ignore))
					continue;
				if (sb.length() > 0) {
					sb.append(", ");
				}
				sb.append(type);
			}
			return sb.toString();
		} else {
			return "*/*";
		}
	}

	private int getRequestBodyParameterIndex(Annotation[][] panns,
			Object[] parameters) {
		for (int i = 0; i < panns.length; i++) {
			boolean body = true;
			for (Annotation ann : panns[i]) {
				if (parameter.class.equals(ann.annotationType())) {
					body = false;
				} else if (header.class.equals(ann.annotationType())) {
					body = false;
				}
			}
			if (body && i < parameters.length) {
				return i;
			}
		}
		return -1;
	}

}