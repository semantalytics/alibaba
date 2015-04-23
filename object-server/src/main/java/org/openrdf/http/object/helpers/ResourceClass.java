/*
 * Copyright (c) 2009-2010, James Leigh and Zepheira LLC Some rights reserved.
 * Copyright (c) 2011 Talis Inc., Some rights reserved.
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

import info.aduna.net.ParsedURI;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import javax.activation.MimeType;
import javax.activation.MimeTypeParseException;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpVersion;
import org.apache.http.StatusLine;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.openrdf.annotations.Iri;
import org.openrdf.annotations.Param;
import org.openrdf.annotations.ParameterTypes;
import org.openrdf.annotations.Path;
import org.openrdf.http.object.client.HttpUriResponse;
import org.openrdf.http.object.exceptions.InternalServerError;
import org.openrdf.http.object.exceptions.NotAcceptable;
import org.openrdf.http.object.exceptions.ResponseException;
import org.openrdf.http.object.exceptions.UnsupportedMediaType;
import org.openrdf.http.object.fluid.Fluid;
import org.openrdf.http.object.fluid.FluidBuilder;
import org.openrdf.http.object.fluid.FluidFactory;
import org.openrdf.http.object.fluid.FluidType;
import org.openrdf.http.object.util.PathMatcher;
import org.openrdf.http.object.util.URLUtil;
import org.openrdf.repository.RepositoryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for {@link HttpServletRequest}.
 * 
 * @author James Leigh
 * 
 */
public class ResourceClass {
	private static final Pattern EMPTY_PATTERN = Pattern.compile("");
	private static final Pattern DEFAULT_PATH = PathMatcher.compile("$|\\?.*");
	private static final String SUB_CLASS_OF = "http://www.w3.org/2000/01/rdf-schema#subClassOf";

	private final Logger logger = LoggerFactory.getLogger(ResourceTarget.class);
	private final Map<String, Collection<Method>> methods;
	private final FluidFactory ff = FluidFactory.getInstance();
	private final Class<?> targetClass;

	public ResourceClass(Class<?> targetClass) {
		this.targetClass = targetClass;
		methods = new HashMap<String, Collection<Method>>();
		for (Method m : targetClass.getMethods()) {
			if (m.isAnnotationPresent(ParameterTypes.class))
				continue;
			org.openrdf.annotations.Method ann = m.getAnnotation(org.openrdf.annotations.Method.class);
			if (ann == null)
				continue;
			for (String method : ann.value()) {
				if (!methods.containsKey(method)) {
					methods.put(method, new ArrayList<Method>(targetClass.getMethods().length));
				}
				methods.get(method).add(m);
			}
		}
	}

	public String toString() {
		return targetClass.toString();
	}

	public Set<String> getAllowedMethods() {
		Set<String> set = new TreeSet<String>();
		set.addAll(methods.keySet());
		if (set.contains("GET")) {
			set.add("HEAD");
		}
		return set;
	}

	public Collection<String> getAllowedHeaders(String m, String url, int startingAt) {
		Collection<String> result = new TreeSet<String>();
		for (Method method : findHandlers(m, url, startingAt)) {
			result.addAll(getVaryHeaders(method));
		}
		return result;
	}

	public String getAccept(String req_method, String url, int startingAt) {
		Collection<String> types = new HashSet<String>();
		for (Method method : findHandlers(req_method, url, startingAt)) {
			for (int i=0,n=method.getParameterTypes().length; i<n;i++) {
				if (getParameterNames(method, i) != null
						|| getHeaderNames(method, i) != null)
					continue;
				for (String media : getParameterMediaTypes(method, i)) {
					if ("*/*".equals(media))
						continue;
					try {
						MimeType type = new MimeType(media);
						if (!"*".equals(type.getPrimaryType())
								|| !"*".equals(type.getSubType())) {
							type.removeParameter("q");
							types.add(type.toString());
						}
					} catch (MimeTypeParseException e) {
						continue;
					}
				}
			}
		}
		if (types.isEmpty())
			return null;
		String string = types.toString();
		return string.substring(1, string.length() - 1);
	}

	public Method getHandlerMethod(String iri, HttpRequest request)
			throws RepositoryException, ResponseException {
		String url = getRequestURL(iri, request);
		if (!url.startsWith(iri))
			throw new InternalServerError("URL " + url
					+ " does not start with IRI " + iri);
		return findMethod(request, url, iri.length());
	}

	public HttpUriResponse head(String iri, HttpRequest request) throws IOException, HttpException {
		try {
			String url = getRequestURL(iri, request);
			if (!url.startsWith(iri))
				throw new InternalServerError("URL " + url
						+ " does not start with IRI " + iri);
			Method method = findMethod(request, url, iri.length());
			BasicHttpResponse response = new BasicHttpResponse(getResponseStatus(method));
			response.setHeaders(getAdditionalHeaders(method));
			String type = getResponseContentType(request, method);
			if (type != null && !response.containsHeader("Content-Type")) {
				response.addHeader("Content-Type", type);
			}
			String vary = getVaryHeaderValue(method);
			if (vary != null && !response.containsHeader("Vary")) {
				response.addHeader("Vary", vary);
			}
			return new HttpUriResponse(url, response);
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new InternalServerError(e);
		}
	}

	public String[] getHandlerMediaTypes(Method method) {
		org.openrdf.annotations.Type t = method
				.getAnnotation(org.openrdf.annotations.Type.class);
		if (t == null)
			return new String[0];
		return t.value();
	}

	public String[] getParameterNames(Method method, int arg) {
		Annotation[] annotations = method.getParameterAnnotations()[arg];
		for (int i = 0; i < annotations.length; i++) {
			if (annotations[i].annotationType().equals(Param.class))
				return ((Param) annotations[i]).value();
		}
		return null;
	}

	public String[] getHeaderNames(Method method, int arg) {
		Annotation[] annotations = method.getParameterAnnotations()[arg];
		for (int i = 0; i < annotations.length; i++) {
			if (annotations[i].annotationType().equals(org.openrdf.annotations.HeaderParam.class))
				return ((org.openrdf.annotations.HeaderParam) annotations[i]).value();
		}
		return null;
	}

	public String[] getParameterMediaTypes(Method method, int arg) {
		Annotation[] annotations = method.getParameterAnnotations()[arg];
		for (int i = 0; i < annotations.length; i++) {
			if (annotations[i].annotationType().equals(org.openrdf.annotations.Type.class))
				return ((org.openrdf.annotations.Type) annotations[i]).value();
		}
		return new String[0];
	}

	public Map<String, String> getPathVariables(Method method, String url, int startingAt) {
		Path path = method.getAnnotation(Path.class);
		if (path == null) {
			return Collections.emptyMap();
		} else {
			Map<String, String> values = new LinkedHashMap<String, String>();
			PathMatcher m = new PathMatcher(url, startingAt);
			for (String regex : path.value()) {
				Map<String, String> match = m.match(regex);
				if (match != null) {
					values.putAll(match);
				}
			}
			return values;
		}
	}

	public String getResponseContentType(HttpRequest request, Method method) {
		if (method == null || method.getReturnType().equals(Void.TYPE))
			return null;
		return getContentType(request, method);
	}

	private String getContentType(HttpRequest request, Method method) {
		Type genericType = method.getGenericReturnType();
		String[] mediaTypes = getHandlerMediaTypes(method);
		return getFluidBuilder().nil(new FluidType(genericType, mediaTypes)).toMedia(getAcceptable(request));
	}

	private String getRequestURL(String iri, HttpRequest request) {
		String uri = request.getRequestLine().getUri();
		if (uri.equals("*"))
			return "*";
		if (!uri.startsWith("/")) {
			return URLUtil.canonicalize(uri);
		}
		String qs = null;
		int qx = uri.indexOf('?');
		if (qx > 0) {
			qs = uri.substring(qx + 1);
			uri = uri.substring(0, qx);
		}
		ParsedURI base = new ParsedURI(iri);
		String scheme = base.getScheme().toLowerCase();
		String host = base.getAuthority().toLowerCase();
		// path is already encoded, so use ParsedURI to concat
		// note that java.net.URI would double encode the path here
		return URLUtil.canonicalize(new ParsedURI(scheme, host, uri, qs, null).toString());
	}

	private StatusLine getResponseStatus(Method method) {
		if (method == null)
			return new BasicStatusLine(HttpVersion.HTTP_1_1, 405, "Method Not Allowed");
		Class<?> type = method.getReturnType();
		if (Void.TYPE.equals(type) || Void.class.equals(type))
			return new BasicStatusLine(HttpVersion.HTTP_1_1, 204, "No Content");
		else
			return new BasicStatusLine(HttpVersion.HTTP_1_1, 200, "OK");
	}

	private Header[] getAdditionalHeaders(Method method) {
		if (method == null || !method.isAnnotationPresent(org.openrdf.annotations.Header.class))
			return new Header[0];
		String[] headers = method.getAnnotation(org.openrdf.annotations.Header.class).value();
		Header[] result = new Header[headers.length];
		for (int i=0; i<headers.length; i++) {
			String[] split = headers[i].split("\\s*:\\s*", 2);
			result[i] = new BasicHeader(split[0], split[1]);
		}
		return result;
	}

	private String getVaryHeaderValue(Method method) {
		Collection<String> headers = getVaryHeaders(method);
		if (headers != null && !headers.isEmpty()) {
			StringBuilder sb = new StringBuilder();
			for (String vary : headers) {
				if (vary.length() > 0
						&& !vary.equalsIgnoreCase("Authorization")
						&& !vary.equalsIgnoreCase("Cookie")) {
					sb.append(vary).append(',');
				}
			}
			if (sb.length() > 0) {
				return sb.substring(0, sb.length() - 1);
			}
		}
		return null;
	}

	private Collection<String> getVaryHeaders(Method method) {
		if (method == null)
			return Collections.emptySet();
		Collection<String> result = new TreeSet<String>();
		for (Annotation[] anns : method.getParameterAnnotations()) {
			for (Annotation ann : anns) {
				if (ann.annotationType().equals(org.openrdf.annotations.HeaderParam.class)) {
					result.addAll(Arrays.asList(((org.openrdf.annotations.HeaderParam) ann).value()));
				}
			}
		}
		return result;
	}

	private Method findMethod(HttpRequest request, String url, int startingAt)
			throws RepositoryException {
		boolean messageBody = isMessageBody(request);
		Collection<Method> methods = findHandlers(request.getRequestLine()
				.getMethod(), url, startingAt);
		if (!methods.isEmpty()) {
			Method method = findBestMethod(request, url, startingAt,
					findAcceptableMethods(request, methods, messageBody));
			if (method != null)
				return method;
		}
		return null;
	}

	private boolean isMessageBody(HttpRequest req) {
		Header length = req.getFirstHeader("Content-Length");
		return length != null && !"0".equals(length.getValue())
				|| req.containsHeader("Transfer-Encoding");
	}

	private Collection<Method> findHandlers(String req_method, String url, int startingAt) {
		Collection<Method> list = new ArrayList<Method>();
		if (req_method == null) {
			for (String m : methods.keySet()) {
				list.addAll(findHandlers(m, url, startingAt));
			}
		} else {
			Collection<Method> collection = methods.get(req_method);
			if (collection == null || collection.isEmpty())
				return list;
			for (Method m : collection) {
				if (getLongestMatchingPath(m, url, startingAt) != null) {
					list.add(m);
				}
			}
		}
		return list;
	}

	private Pattern getLongestMatchingPath(Method method, String url, int startingAt) {
		Path path = method.getAnnotation(Path.class);
		PathMatcher m = new PathMatcher(url, startingAt);
		if (path == null)
			return m.matches(DEFAULT_PATH) ? EMPTY_PATTERN: null;
		Pattern longest = null;
		for (String regex : path.value()) {
			Pattern pattern = PathMatcher.compile(regex);
			if (!m.matches(pattern))
				continue;
			if (longest == null) {
				longest = pattern;
			}
			int rlen = pattern.pattern().length();
			int llen = longest.pattern().length();
			if (rlen > llen || rlen == llen && isLiteral(pattern)
					&& !isLiteral(longest)) {
				longest = pattern;
			}
		}
		return longest;
	}

	private boolean isLiteral(Pattern regex) {
		return (regex.flags() & Pattern.LITERAL) != 0;
	}

	private Collection<Method> findAcceptableMethods(HttpRequest request,
			Collection<Method> methods, boolean messageBody) {
		String readable = null;
		String acceptable = null;
		Collection<Method> list = new LinkedHashSet<Method>(methods.size());
		Fluid body = getBody(request);
		loop: for (Method method : methods) {
			Collection<String> readableTypes;
			readableTypes = getReadableTypes(body, method, messageBody);
			if (readableTypes.isEmpty()) {
				String contentType = body.getFluidType().preferred();
				Annotation[][] anns = method.getParameterAnnotations();
				for (int i = 0; i < anns.length; i++) {
					String[] types = getParameterMediaTypes(method, i);
					Type gtype = method.getGenericParameterTypes()[i];
					if (body.toMedia(new FluidType(gtype, types)) == null) {
						if (contentType == null) {
							readable = "Cannot read unknown body into " + gtype;
						} else {
							readable = "Cannot read " + contentType + " into "
									+ gtype;
						}
						continue loop;
					}
				}
				if (readable == null && contentType != null) {
					readable = "Cannot read " + contentType;
				}
				if (readable != null) {
					continue loop;
				}
			}
			if (isAcceptable(request, method)) {
				list.add(method);
				continue loop;
			}
			acceptable = "Cannot write " + method.getGenericReturnType();
		}
		if (list.isEmpty() && readable != null) {
			throw new UnsupportedMediaType(readable);
		}
		if (list.isEmpty() && acceptable != null) {
			throw new NotAcceptable(acceptable);
		}
		return list;
	}

	private Fluid getBody(HttpRequest request) {
		Header[] headers = request.getHeaders("Content-Type");
		String[] mediaType;
		if (headers == null || headers.length == 0) {
			mediaType = new String[] { "text/plain",
					"application/octet-stream", "*/*" };
		} else {
			mediaType = new String[headers.length];
			for (int i = 0; i < headers.length; i++) {
				mediaType[i] = headers[i].getValue();
			}
		}
		FluidType ftype = new FluidType(HttpEntity.class, mediaType);
		return getFluidBuilder().nil(ftype);
	}

	private FluidBuilder getFluidBuilder() {
		return ff.builder();
	}

	private boolean isAcceptable(HttpRequest request, Method method) {
		assert method != null;
		if (method.getReturnType().equals(Void.TYPE))
			return true;
		String[] types = getHandlerMediaTypes(method);
		Type gtype = method.getGenericReturnType();
		FluidBuilder writer = getFluidBuilder();
		if (types.length == 0 && writer.isConsumable(gtype, "message/http"))
			return true;
		FluidType ftype = new FluidType(gtype, types);
		return writer.isConsumable(ftype) && writer.nil(ftype).toMedia(getAcceptable(request)) != null;
	}

	private FluidType getAcceptable(HttpRequest request) {
		Header[] headers = request.getHeaders("Accept");
		if (headers == null || headers.length == 0) {
			return new FluidType(HttpEntity.class);
		} else {
			StringBuilder sb = new StringBuilder();
			for (Header hd : headers) {
				if (sb.length() > 0) {
					sb.append(",");
				}
				sb.append(hd.getValue());
			}
			return new FluidType(HttpEntity.class, sb.toString().split("\\s*,\\s*"));
		}
	}

	private Collection<String> getReadableTypes(Fluid input,
			Method method, boolean typeRequired) {
		assert method != null;
		Class<?>[] ptypes = method.getParameterTypes();
		Object[] args = new Object[ptypes.length];
		if (args.length == 0 && !typeRequired)
			return Collections.singleton("*/*");
		int empty = 0;
		List<String> readable = new ArrayList<String>();
		for (int i = 0; i < args.length; i++) {
			Collection<String> set;
			set = getReadableTypes(input, method, i,
					typeRequired);
			if (set.isEmpty()) {
				empty++;
			}
			if (getHeaderNames(method, i) == null
					&& getParameterNames(method, i) == null) {
				readable.addAll(set);
			}
		}
		if (empty > 0 && empty == args.length && typeRequired)
			return Collections.emptySet();
		if (readable.isEmpty() && !typeRequired)
			return Collections.singleton("*/*");
		return readable;
	}

	private Collection<String> getReadableTypes(Fluid input, Method method, int i, boolean typeRequired) {
		if (getHeaderNames(method, i) != null)
			return Collections.singleton("*/*");
		if (getParameterNames(method, i) != null)
			return Collections.singleton("*/*");
		List<String> readable = new ArrayList<String>();
		String[] types = getParameterMediaTypes(method, i);
		if (types.length == 0 && typeRequired)
			return Collections.emptySet();
		String media = input.toMedia(new FluidType(method.getGenericParameterTypes()[i], types));
		if (media != null) {
			readable.add(media);
		}
		return readable;
	}

	private Method findBestMethod(HttpRequest request, String url,
			int startingAt, Collection<Method> methods) {
		if (methods.isEmpty())
			return null;
		if (methods.size() == 1) {
			return methods.iterator().next();
		}
		Collection<Method> submethods = filterSubMethods(methods);
		if (submethods.isEmpty())
			return null;
		Collection<Method> longerPath = filterLongestPathMethods(submethods, url, startingAt);
		if (longerPath.size() == 1)
			return longerPath.iterator().next();
		Collection<Method> filtered = filterPreferResponseType(request, longerPath);
		if (filtered.size() == 1)
			return filtered.iterator().next();
		Method best = findBestMethodByRequestType(request, filtered);
		if (best == null)
			return submethods.iterator().next();
		return best;
	}

	private Collection<Method> filterPreferResponseType(HttpRequest request,
			Collection<Method> methods) {
		FluidType acceptable = getAcceptable(request);
		Collection<Method> filtered = new HashSet<Method>(methods.size());
		double quality = Double.MIN_VALUE;
		for (Method m : methods) {
			if (!m.isAnnotationPresent(org.openrdf.annotations.Type.class))
				continue;
			Collection<String> possible = getAllMimeTypesOf(m);
			String[] media = possible.toArray(new String[possible.size()]);
			double q = acceptable.as(new FluidType(acceptable.asType(), media))
					.getQuality();
			if (q > quality) {
				quality = q;
				filtered.clear();
			}
			if (q >= quality) {
				filtered.add(m);
			}
		}
		for (Method m : methods) {
			if (!m.isAnnotationPresent(org.openrdf.annotations.Type.class)) {
				filtered.add(m); // free pass if type is not known in advance
			}
		}
		return filtered;
	}

	private Collection<String> getAllMimeTypesOf(Method m) {
		Collection<String> result = new LinkedHashSet<String>();
		if (m.isAnnotationPresent(org.openrdf.annotations.Type.class)) {
			for (String media : m.getAnnotation(org.openrdf.annotations.Type.class).value()) {
				result.add(media);
			}
		}
		if (result.isEmpty()) {
			result.add("*/*");
		}
		return result;
	}

	private Collection<Method> filterSubMethods(Collection<Method> methods) {
		Map<String, Method> map = new LinkedHashMap<String, Method>();
		for (Method m : methods) {
			String iri;
			Iri ann = m.getAnnotation(Iri.class);
			if (ann == null) {
				iri = m.toString();
			} else {
				iri = ann.value();
			}
			map.put(iri, m);
		}
		for (Method method : methods) {
			for (String iri : getAnnotationStringValue(method, SUB_CLASS_OF)) {
				map.remove(iri);
			}
		}
		if (map.isEmpty())
			return methods;
		return map.values();
	}

	private String[] getAnnotationStringValue(Method method, String iri) {
		for (Annotation ann : method.getAnnotations()) {
			for (Method field : ann.annotationType().getMethods()) {
				Iri airi = field.getAnnotation(Iri.class);
				if (airi != null && iri.equals(airi.value()))
					try {
						Object arg = field.invoke(ann);
						if (arg instanceof String[])
							return (String[]) arg;
						return new String[] { arg.toString() };
					} catch (IllegalArgumentException e) {
						logger.warn(e.toString(), e);
					} catch (InvocationTargetException e) {
						logger.warn(e.toString(), e);
					} catch (IllegalAccessException e) {
						logger.warn(e.toString(), e);
					}
			}
		}
		return new String[0];
	}

	private Collection<Method> filterLongestPathMethods(
			Collection<Method> methods, String url, int startingAt) {
		int length = -1;
		Pattern longest = null;
		Collection<Method> result = new ArrayList<Method>(methods.size());
		for (Method m : methods) {
			Pattern path = getLongestMatchingPath(m, url, startingAt);
			int len = path == null ? -1 : path.pattern().length();
			if (len > length || len == length && isLiteral(path)
					&& !isLiteral(longest)) {
				result.clear();
				longest = path;
				length = len;
			}
			if (len >= length && (isLiteral(path) || !isLiteral(longest))) {
				result.add(m);
			}
		}
		return result;
	}

	private Method findBestMethodByRequestType(HttpRequest request, Collection<Method> methods) {
		Method best = null;
		double quality = Double.MIN_VALUE;
		Fluid body = getBody(request);
		for (Method method : methods) {
			Type[] gtypes = method.getGenericParameterTypes();
			for (int i=0,n=method.getParameterTypes().length; i<n; i++) {
				Type gtype = gtypes[i];
				if (getHeaderNames(method, i) != null || getParameterNames(method, i) != null)
					continue;
				String[] types = getParameterMediaTypes(method, i);
				if (types.length == 0)
					continue;
				FluidType fluidType = new FluidType(gtype, types);
				double q = fluidType.as(body.getFluidType()).getQuality();
				if (q > quality) {
					quality = q;
					best = method;
				}
			}
		}
		return best;
	}

}