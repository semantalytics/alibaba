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

import java.io.Closeable;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
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
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerConfigurationException;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.StatusLine;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.protocol.HttpContext;
import org.openrdf.annotations.Iri;
import org.openrdf.annotations.Param;
import org.openrdf.annotations.ParameterTypes;
import org.openrdf.annotations.Path;
import org.openrdf.http.object.client.HttpUriResponse;
import org.openrdf.http.object.exceptions.InternalServerError;
import org.openrdf.http.object.exceptions.MethodNotAllowed;
import org.openrdf.http.object.exceptions.NotAcceptable;
import org.openrdf.http.object.exceptions.NotFound;
import org.openrdf.http.object.exceptions.ResponseException;
import org.openrdf.http.object.exceptions.UnsupportedMediaType;
import org.openrdf.http.object.fluid.Fluid;
import org.openrdf.http.object.fluid.FluidBuilder;
import org.openrdf.http.object.fluid.FluidException;
import org.openrdf.http.object.fluid.FluidFactory;
import org.openrdf.http.object.fluid.FluidType;
import org.openrdf.http.object.util.PathMatcher;
import org.openrdf.model.URI;
import org.openrdf.model.ValueFactory;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.object.ObjectConnection;
import org.openrdf.repository.object.RDFObject;
import org.openrdf.repository.object.traits.RDFObjectBehaviour;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for {@link HttpServletRequest}.
 * 
 * @author James Leigh
 * 
 */
public class ResourceTarget {
	private static final Pattern DEFAULT_PATH = PathMatcher.compile("$|\\?.*");

	private interface MapStringArray extends Map<String, String[]> {
	}

	private static final Type mapOfStringArrayType = MapStringArray.class
			.getGenericInterfaces()[0];
	private static final String SUB_CLASS_OF = "http://www.w3.org/2000/01/rdf-schema#subClassOf";
	private final Logger logger = LoggerFactory.getLogger(ResourceTarget.class);

	private final FluidFactory ff = FluidFactory.getInstance();
	private final ObjectContext context;
	private final ValueFactory vf;
	private final ObjectConnection con;
	private RDFObject target;
	private final FluidBuilder writer;

	public ResourceTarget(RDFObject target, HttpContext context)
			throws QueryEvaluationException, RepositoryException {
		this.target = target;
		this.context = ObjectContext.adapt(context);
		this.con = target.getObjectConnection();
		this.vf = con.getValueFactory();
		this.writer = ff.builder(con);
	}

	public String toString() {
		return target.toString();
	}

	public RDFObject getTargetObject() {
		return target;
	}

	public Set<String> getAllowedMethods(String url) {
		Set<String> set = new TreeSet<String>();
		Collection<Method> methods = new ArrayList<Method>();
		for (Method m : target.getClass().getMethods()) {
			if (m.isAnnotationPresent(ParameterTypes.class))
				continue;
			org.openrdf.annotations.Method ann = m.getAnnotation(org.openrdf.annotations.Method.class);
			if (ann == null)
				continue;
			if (getLongestMatchingPath(m, url) != null) {
				methods.add(m);
			}
			set.addAll(Arrays.asList(ann.value()));
		}
		if (set.contains("GET")) {
			set.add("HEAD");
		}
		return set;
	}

	public Collection<String> getAllowedHeaders(String m, String url) {
		Collection<String> result = new TreeSet<String>();
		for (Method method : findHandlers(m, url)) {
			result.addAll(getVaryHeaders(method));
		}
		return result;
	}

	public String getAccept(String req_method, String url) {
		Collection<String> types = new HashSet<String>();
		for (Method method : findHandlers(req_method, url)) {
			for (Annotation[] anns : method.getParameterAnnotations()) {
				if (getParameterNames(anns) != null
						|| getHeaderNames(anns) != null)
					continue;
				for (String media : getParameterMediaTypes(anns)) {
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

	public Method getHandlerMethod(HttpRequest request) {
		try {
			return findMethod(new Request(request, context));
		} catch (RepositoryException e) {
			throw new InternalServerError(e);
		} catch (ResponseException e) {
			return null;
		}
	}

	public HttpUriResponse head(HttpRequest request) throws IOException, HttpException {
		try {
			Request req = new Request(request, context);
			Method method = findMethod(req);
			BasicHttpResponse response = new BasicHttpResponse(getResponseStatus(method));
			response.setHeaders(getAdditionalHeaders(method));
			String type = getResponseContentType(req, method);
			if (type != null && !response.containsHeader("Content-Type")) {
				response.addHeader("Content-Type", type);
			}
			String vary = getVaryHeaderValue(method);
			if (vary != null && !response.containsHeader("Vary")) {
				response.addHeader("Vary", vary);
			}
			return new HttpUriResponse(req.getRequestURL(), response);
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new InternalServerError(e);
		}
	}

	public HttpUriResponse invoke(HttpRequest request) throws IOException, HttpException {
		try {
			Request req = new Request(request, context);
			Method method = findMethod(req);
			if (method == null && req.isSafe())
				throw new NotFound();
			if (method == null)
				throw new MethodNotAllowed("No such method for " + req.getIRI());
			return invoke(req, method, req.isSafe(), new ResponseBuilder(req));
		} catch (Error e) {
			throw e;
		} catch (RuntimeException e) {
			throw e;
		} catch (IOException e) {
			throw e;
		} catch (HttpException e) {
			throw e;
		} catch (Exception e) {
			throw new InternalServerError(e);
		}
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

	private Method findMethod(Request request) throws RepositoryException {
		boolean messageBody = request.isMessageBody();
		Collection<Method> methods = findHandlers(request.getMethod(), request.getRequestURL());
		if (!methods.isEmpty()) {
			Method method = findBestMethod(request, findAcceptableMethods(request, methods, messageBody));
			if (method != null)
				return method;
		}
		return null;
	}

	private Collection<Method> findHandlers(String req_method, String url) {
		Collection<Method> methods = new ArrayList<Method>();
		for (Method m : target.getClass().getMethods()) {
			if (m.isAnnotationPresent(ParameterTypes.class))
				continue;
			org.openrdf.annotations.Method ann = m.getAnnotation(org.openrdf.annotations.Method.class);
			if (ann == null)
				continue;
			if (req_method != null && !Arrays.asList(ann.value()).contains(req_method))
				continue;
			if (getLongestMatchingPath(m, url) != null) {
				methods.add(m);
			}
		}
		return methods;
	}

	private String getLongestMatchingPath(Method method, String url) {
		String iri = target.getResource().stringValue();
		if (!url.startsWith(iri))
			throw new InternalServerError("URL " + url
					+ " does not start with IRI " + iri);
		Path path = method.getAnnotation(Path.class);
		PathMatcher m = new PathMatcher(url, iri.length());
		if (path == null)
			return m.matches(DEFAULT_PATH) ? "": null;
		String longest = null;
		for (Pattern regex : PathMatcher.compile(path)) {
			if (!m.matches(regex))
				continue;
			if (longest == null || regex.pattern().length() > longest.length()) {
				longest = regex.pattern();
			}
		}
		return longest;
	}

	private Collection<Method> findAcceptableMethods(Request request, Collection<Method> methods, boolean messageBody) {
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
					String[] types = getParameterMediaTypes(anns[i]);
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

	private Fluid getBody(Request request) {
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
		String location = request.getResolvedHeader("Content-Location");
		if (location == null) {
			location = request.getRequestURL();
		} else {
			location = createURI(request, location).stringValue();
		}
		FluidType ftype = new FluidType(HttpEntity.class, mediaType);
		return getFluidBuilder().consume(request.getEntity(), location, ftype);
	}

	private URI createURI(Request request, String uriSpec) {
		return vf.createURI(request.resolve(uriSpec));
	}

	private FluidBuilder getFluidBuilder() {
		return FluidFactory.getInstance().builder(con);
	}

	private boolean isAcceptable(HttpRequest request, Method method) {
		assert method != null;
		if (method.getReturnType().equals(Void.TYPE))
			return true;
		String[] types = getTypes(method);
		Type gtype = method.getGenericReturnType();
		if (types.length == 0 && getFluidBuilder().isConsumable(gtype, "message/http"))
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

	private String[] getTypes(Method method) {
		org.openrdf.annotations.Type t = method
				.getAnnotation(org.openrdf.annotations.Type.class);
		if (t == null)
			return new String[0];
		return t.value();
	}

	private Collection<String> getReadableTypes(Fluid input,
			Method method, boolean typeRequired) {
		assert method != null;
		Class<?>[] ptypes = method.getParameterTypes();
		Annotation[][] anns = method.getParameterAnnotations();
		Type[] gtypes = method.getGenericParameterTypes();
		Object[] args = new Object[ptypes.length];
		if (args.length == 0 && !typeRequired)
			return Collections.singleton("*/*");
		int empty = 0;
		List<String> readable = new ArrayList<String>();
		for (int i = 0; i < args.length; i++) {
			Collection<String> set;
			set = getReadableTypes(input, anns[i], ptypes[i], gtypes[i],
					typeRequired);
			if (set.isEmpty()) {
				empty++;
			}
			if (getHeaderNames(anns[i]) == null
					&& getParameterNames(anns[i]) == null) {
				readable.addAll(set);
			}
		}
		if (empty > 0 && empty == args.length && typeRequired)
			return Collections.emptySet();
		if (readable.isEmpty() && !typeRequired)
			return Collections.singleton("*/*");
		return readable;
	}

	private Collection<String> getReadableTypes(Fluid input, Annotation[] anns, Class<?> ptype,
			Type gtype, boolean typeRequired) {
		if (getHeaderNames(anns) != null)
			return Collections.singleton("*/*");
		if (getParameterNames(anns) != null)
			return Collections.singleton("*/*");
		List<String> readable = new ArrayList<String>();
		String[] types = getParameterMediaTypes(anns);
		if (types.length == 0 && typeRequired)
			return Collections.emptySet();
		String media = input.toMedia(new FluidType(gtype, types));
		if (media != null) {
			readable.add(media);
		}
		return readable;
	}

	private Method findBestMethod(Request request, Collection<Method> methods) {
		if (methods.isEmpty())
			return null;
		if (methods.size() == 1) {
			return methods.iterator().next();
		}
		Collection<Method> filtered = filterPreferResponseType(request, methods);
		if (filtered.size() == 1)
			return filtered.iterator().next();
		Collection<Method> submethods = filterSubMethods(filtered);
		if (submethods.isEmpty())
			return null;
		Collection<Method> longerPath = filterLongestPathMethods(submethods, request.getRequestURL());
		if (longerPath.size() == 1)
			return longerPath.iterator().next();
		Method best = findBestMethodByRequestType(request, longerPath);
		if (best == null)
			return submethods.iterator().next();
		return best;
	}

	private Collection<Method> filterPreferResponseType(Request request,
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
			Collection<Method> methods, String url) {
		int longest = 0;
		Collection<Method> result = new ArrayList<Method>(methods.size());
		for (Method m : methods) {
			String path = getLongestMatchingPath(m, url);
			int length = 0;
			if (path != null) {
				if (path.length() > length) {
					length = path.length();
				}
			}
			if (length > longest) {
				result.clear();
				longest = length;
			}
			if (length >= longest) {
				result.add(m);
			}
		}
		return result;
	}

	private Method findBestMethodByRequestType(Request request, Collection<Method> methods) {
		Method best = null;
		double quality = Double.MIN_VALUE;
		Fluid body = getBody(request);
		for (Method method : methods) {
			Type[] gtypes = method.getGenericParameterTypes();
			Annotation[][] params = method.getParameterAnnotations();
			for (int i=0; i<params.length; i++) {
				Type gtype = gtypes[i];
				Annotation[] anns = params[i];
				if (getHeaderNames(anns) != null || getParameterNames(anns) != null)
					continue;
				String[] types = getParameterMediaTypes(anns);
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

	private RDFObject getRequestedResource() {
		return target;
	}

	private String[] getParameterNames(Annotation[] annotations) {
		for (int i = 0; i < annotations.length; i++) {
			if (annotations[i].annotationType().equals(Param.class))
				return ((Param) annotations[i]).value();
		}
		return null;
	}

	private String[] getHeaderNames(Annotation[] annotations) {
		for (int i = 0; i < annotations.length; i++) {
			if (annotations[i].annotationType().equals(org.openrdf.annotations.HeaderParam.class))
				return ((org.openrdf.annotations.HeaderParam) annotations[i]).value();
		}
		return null;
	}

	private String[] getParameterMediaTypes(Annotation[] annotations) {
		for (int i = 0; i < annotations.length; i++) {
			if (annotations[i].annotationType().equals(org.openrdf.annotations.Type.class))
				return ((org.openrdf.annotations.Type) annotations[i]).value();
		}
		return new String[0];
	}

	private HttpUriResponse invoke(Request req, Method method, boolean safe, ResponseBuilder builder)
			throws Exception {
		Fluid body = getBody(req);
		try {
			Object[] args;
			try {
				args = getParameters(req, method, body);
			} catch (ParserConfigurationException e) {
				throw e;
			} catch (TransformerConfigurationException e) {
				throw e;
			} catch (Exception e) {
				String message = e.getMessage();
				if (message == null) {
					message = e.toString();
				}
				return builder.badRequest(message);
			}
			try {
				HttpUriResponse response = invoke(req, method, args, builder);
				if (!safe && response.getStatusLine().getStatusCode() < 400) {
					flush();
				}
				String vary = getVaryHeaderValue(method);
				if (vary != null) {
					response.addHeader("Vary", vary);
				}
				return response;
			} finally {
				for (Object arg : args) {
					if (arg instanceof Closeable) {
						((Closeable) arg).close();
					}
				}
			}
		} catch (InvocationTargetException e) {
			try {
				throw e.getCause();
			} catch (Error cause) {
				throw cause;
			} catch (Exception cause) {
				throw cause;
			} catch (Throwable cause) {
				throw e;
			}
		} finally {
			if (body != null) {
				try {
					body.asVoid();
				} catch (IOException e) {
					logger.error(req.toString(), e);
				}
			}
		}
	}

	private void flush() throws RepositoryException, QueryEvaluationException,
			IOException {
		con.commit();
		this.target = con.getObject(RDFObject.class, getRequestedResource().getResource());
	}

	private Object[] getParameters(Request req, Method method,
			Fluid input) throws Exception {
		Class<?>[] ptypes = method.getParameterTypes();
		Annotation[][] anns = method.getParameterAnnotations();
		Type[] gtypes = method.getGenericParameterTypes();
		Map<String, String> values = getPathVariables(req.getRequestURL(), method);
		Object[] args = new Object[ptypes.length];
		for (int i = 0; i < args.length; i++) {
			Fluid entity = getParameter(req, anns[i], ptypes[i], values, input);
			if (entity != null) {
				String[] types = getParameterMediaTypes(anns[i]);
				args[i] = entity.as(new FluidType(gtypes[i], types));
			}
		}
		return args;
	}

	private Map<String, String> getPathVariables(String url, Method method) {
		String iri = target.getResource().stringValue();
		assert url.startsWith(iri);
		Map<String, String> values = new LinkedHashMap<String, String>();
		Path path = method.getAnnotation(Path.class);
		if (path == null) {
			values.put("0", url.substring(iri.length()));
		} else {
			PathMatcher m = new PathMatcher(url, iri.length());
			for (Pattern regex : PathMatcher.compile(path)) {
				Map<String, String> match = m.match(regex);
				if (match != null) {
					values.putAll(match);
				}
			}
		}
		return values;
	}

	private Fluid getParameter(Request req, Annotation[] anns, Class<?> ptype,
			Map<String, String> values, Fluid input) throws Exception {
		String[] names = getParameterNames(anns);
		String[] headers = getHeaderNames(anns);
		String[] types = getParameterMediaTypes(anns);
		if (names == null && headers == null && types.length == 0) {
			return getFluidBuilder().media("*/*");
		} else if (names == null && headers == null) {
			return input;
		} else if (headers != null && names != null) {
			return getHeaderAndQuery(req, headers, names, values);
		} else if (headers != null) {
			return getHeader(req, headers);
		} else {
			return getParameter(req, names, values);
		}
	}

	private Fluid getHeaderAndQuery(Request req, String[] headers,
			String[] queries, Map<String, String> values) {
		String[] qvalues = getParameterValues(req, queries, values);
		if (qvalues == null)
			return getHeader(req, headers);
		List<String> list = new ArrayList<String>(qvalues.length * 2);
		if (qvalues.length > 0) {
			list.addAll(Arrays.asList(qvalues));
		}
		for (String name : headers) {
			for (Header header : req.getHeaders(name)) {
				list.add(header.getValue());
			}
		}
		String[] array = list.toArray(new String[list.size()]);
		FluidType ftype = new FluidType(String[].class, "text/plain", "text/*");
		FluidBuilder fb = getFluidBuilder();
		return fb.consume(array, req.getIRI(), ftype);
	}

	private String[] getParameterValues(Request req, String[] names, Map<String, String> values) {
		if (names.length == 0)
			return new String[0];
		Map<String, String[]> map = getParameterMap(req);
		List<String> list = new ArrayList<String>(names.length * 2);
		for (String name : names) {
			if (values.containsKey(name)) {
				if (values.get(name) != null) {
					list.add(values.get(name));
				}
			} else if (map != null && map.containsKey(name)) {
				list.addAll(Arrays.asList(map.get(name)));
			}
		}
		return list.toArray(new String[list.size()]);
	}

	@SuppressWarnings("unchecked")
	private Map<String, String[]> getParameterMap(Request req) {
		try {
			return (Map<String, String[]>) getQueryStringParameter(req).as(
					new FluidType(mapOfStringArrayType,
							"application/x-www-form-urlencoded"));
		} catch (Exception e) {
			return Collections.emptyMap();
		}
	}

	private Fluid getQueryStringParameter(Request request) {
		String value = request.getQueryString();
		FluidType ftype = new FluidType(String.class, "application/x-www-form-urlencoded");
		FluidBuilder fb = FluidFactory.getInstance().builder(con);
		return fb.consume(value, request.getIRI(), ftype);
	}

	private Fluid getHeader(Request request, String... names) {
		List<String> list = new ArrayList<String>();
		for (String name : names) {
			for (Header header : request.getHeaders(name)) {
				list.add(header.getValue());
			}
		}
		String[] values = list.toArray(new String[list.size()]);
		FluidType ftype = new FluidType(String[].class, "text/plain", "text/*", "*/*");
		FluidBuilder fb = FluidFactory.getInstance().builder(con);
		return fb.consume(values, request.getIRI(), ftype);
	}

	private Fluid getParameter(Request req, String[] names, Map<String, String> values) {
		String[] array = getParameterValues(req, names, values);
		FluidType ftype = new FluidType(String[].class, "text/plain", "text/*", "*/*");
		FluidBuilder fb = getFluidBuilder();
		return fb.consume(array, req.getIRI(), ftype);
	}

	private String[] getResponseTypes(Request req, Method method) {
		String preferred = getContentType(req, method);
		String[] types = getTypes(method);
		if (preferred == null && types.length < 1)
			return new String[] { "*/*" };
		if (preferred == null)
			return types;
		String[] result = new String[types.length + 1];
		result[0] = preferred;
		System.arraycopy(types, 0, result, 1, types.length);
		return result;
	}

	private String getContentType(Request request, Method method) {
		Type genericType = method.getGenericReturnType();
		String[] mediaTypes = getTypes(method);
		return writer.nil(new FluidType(genericType, mediaTypes)).toMedia(getAcceptable(request));
	}

	private HttpUriResponse invoke(Request req, Method method, Object[] args,
			ResponseBuilder rbuilder) throws Exception {
		Object result = method.invoke(getRequestedResource(), args);
		if (result instanceof RDFObjectBehaviour) {
			result = ((RDFObjectBehaviour) result).getBehaviourDelegate();
		}
		return new HttpUriResponse(req.getRequestURL(), createResponse(req,
				result, method, getResponseTypes(req, method), rbuilder));
	}

	private HttpResponse createResponse(Request req, Object result,
			Method method, String[] responseTypes, ResponseBuilder rbuilder)
			throws IOException, FluidException {
		FluidBuilder builder = getFluidBuilder();
		Type gtype = method.getGenericReturnType();
		if (!method.isAnnotationPresent(org.openrdf.annotations.Type.class)
				&& builder.isConsumable(gtype, "message/http")) {
			Fluid writer = builder.consume(result, req.getRequestURL(), gtype,
					"message/http");
			HttpResponse http = writer.asHttpResponse("message/http");
			if (http == null)
				return rbuilder.noContent(204, "No Content");
			return http;
		}
		Class<?> type = method.getReturnType();
		if (result == null || Set.class.equals(type)
				&& ((Set<?>) result).isEmpty()) {
			return rbuilder.noContent(204, "No Content");
		}
		Fluid writer = builder.consume(result, req.getRequestURL(),
				gtype, responseTypes);
		HttpEntity entity = writer.asHttpEntity(getResponseContentType(req,
				method));
		return rbuilder.content(200, "OK", entity);
	}

	private String getResponseContentType(Request request, Method method) {
		if (method == null || method.getReturnType().equals(Void.TYPE))
			return null;
		return getContentType(request, method);
	}

}