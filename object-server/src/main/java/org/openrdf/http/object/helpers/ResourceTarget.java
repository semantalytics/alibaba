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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerConfigurationException;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.protocol.HttpContext;
import org.openrdf.http.object.client.HttpUriResponse;
import org.openrdf.http.object.exceptions.InternalServerError;
import org.openrdf.http.object.exceptions.MethodNotAllowed;
import org.openrdf.http.object.exceptions.NotFound;
import org.openrdf.http.object.exceptions.ResponseException;
import org.openrdf.http.object.fluid.Fluid;
import org.openrdf.http.object.fluid.FluidBuilder;
import org.openrdf.http.object.fluid.FluidException;
import org.openrdf.http.object.fluid.FluidFactory;
import org.openrdf.http.object.fluid.FluidType;
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
	private static final Charset UTF8 = Charset.forName("UTF-8");
	private final Logger logger = LoggerFactory.getLogger(ResourceTarget.class);
	private final FluidFactory ff = FluidFactory.getInstance();
	private final ResourceClass rClass;
	private final ObjectContext context;
	private final ValueFactory vf;
	private final ObjectConnection con;
	private RDFObject target;
	private final FluidBuilder writer;

	public ResourceTarget(ResourceClass rClass, RDFObject target,
			HttpContext context) {
		this.rClass = rClass;
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
		return rClass.getAllowedMethods();
	}

	public Collection<String> getAllowedHeaders(String m, String url) {
		String iri = target.getResource().stringValue();
		if (!url.startsWith(iri))
			throw new InternalServerError("URL " + url
					+ " does not start with IRI " + iri);
		return rClass.getAllowedHeaders(m, url, iri.length());
	}

	public String getAccept(String req_method, String url) {
		String iri = target.getResource().stringValue();
		if (!url.startsWith(iri))
			throw new InternalServerError("URL " + url
					+ " does not start with IRI " + iri);
		return rClass.getAccept(req_method, url, iri.length());
	}

	public Method getHandlerMethod(HttpRequest request) {
		try {
			return rClass.getHandlerMethod(target.getResource().stringValue(),
					request);
		} catch (RepositoryException e) {
			throw new InternalServerError(e);
		} catch (ResponseException e) {
			return null;
		}
	}

	public HttpUriResponse head(HttpRequest request) throws IOException,
			HttpException {
		return rClass.head(target.getResource().stringValue(), request);
	}

	public HttpUriResponse invoke(HttpRequest request) throws IOException,
			HttpException {
		try {
			Request req = new Request(request, context);
			String iri = target.getResource().stringValue();
			Method method = rClass.getHandlerMethod(iri, req);
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
		return writer.consume(request.getEntity(), location, ftype);
	}

	private URI createURI(Request request, String uriSpec) {
		return vf.createURI(request.resolve(uriSpec));
	}

	private RDFObject getRequestedResource() {
		return target;
	}

	private HttpUriResponse invoke(Request req, Method method, boolean safe,
			ResponseBuilder builder) throws Exception {
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
		this.target = con.getObject(RDFObject.class, getRequestedResource()
				.getResource());
	}

	private Object[] getParameters(Request req, Method method, Fluid input)
			throws Exception {
		Class<?>[] ptypes = method.getParameterTypes();
		Type[] gtypes = method.getGenericParameterTypes();
		String iri = target.getResource().stringValue();
		Map<String, String> values = rClass.getPathVariables(method,
				req.getRequestURL(), iri.length());
		Object[] args = new Object[ptypes.length];
		for (int i = 0; i < args.length; i++) {
			Fluid entity = getParameter(req, method, i, values, input);
			if (entity != null) {
				String[] types = rClass.getParameterMediaTypes(method, i);
				args[i] = entity.as(new FluidType(gtypes[i], types));
			}
		}
		return args;
	}

	private Fluid getParameter(Request req, Method method, int i,
			Map<String, String> values, Fluid input) throws Exception {
		String[] names = rClass.getParameterNames(method, i);
		String[] headers = rClass.getHeaderNames(method, i);
		String[] types = rClass.getParameterMediaTypes(method, i);
		if (names == null && headers == null && types.length == 0) {
			return writer.media("*/*");
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
		FluidBuilder fb = writer;
		return fb.consume(array, req.getIRI(), ftype);
	}

	private String[] getParameterValues(Request req, String[] names,
			Map<String, String> values) {
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

	private Map<String, String[]> getParameterMap(Request req) {
		Map<String, String[]> map = new LinkedHashMap<String, String[]>();
		String uri = req.getRequestLine().getUri();
		int q = uri.indexOf('?');
		if (q < 0)
			return Collections.emptyMap();
		String qs = uri.substring(q + 1);
		for (NameValuePair pair : URLEncodedUtils.parse(qs, UTF8)) {
			if (map.containsKey(pair.getName())) {
				String[] previous = map.get(pair.getName());
				String[] values = new String[previous.length + 1];
				System.arraycopy(previous, 0, values, 0, previous.length);
				values[previous.length] = pair.getValue();
				map.put(pair.getName(), values);
			} else {
				map.put(pair.getName(), new String[] { pair.getValue() });
			}
		}
		return map;
	}

	private Fluid getHeader(Request request, String... names) {
		List<String> list = new ArrayList<String>();
		for (String name : names) {
			for (Header header : request.getHeaders(name)) {
				list.add(header.getValue());
			}
		}
		String[] values = list.toArray(new String[list.size()]);
		FluidType ftype = new FluidType(String[].class, "text/plain", "text/*",
				"*/*");
		FluidBuilder fb = FluidFactory.getInstance().builder(con);
		return fb.consume(values, request.getIRI(), ftype);
	}

	private Fluid getParameter(Request req, String[] names,
			Map<String, String> values) {
		String[] array = getParameterValues(req, names, values);
		FluidType ftype = new FluidType(String[].class, "text/plain", "text/*",
				"*/*");
		FluidBuilder fb = writer;
		return fb.consume(array, req.getIRI(), ftype);
	}

	private HttpUriResponse invoke(Request req, Method method, Object[] args,
			ResponseBuilder rbuilder) throws Exception {
		Object result = method.invoke(getRequestedResource(), args);
		if (result instanceof RDFObjectBehaviour) {
			result = ((RDFObjectBehaviour) result).getBehaviourDelegate();
		}
		return new HttpUriResponse(req.getRequestURL(), createResponse(req,
				result, method, rClass.getHandlerMediaTypes(method), rbuilder));
	}

	private HttpResponse createResponse(Request req, Object result,
			Method method, String[] responseTypes, ResponseBuilder rbuilder)
			throws IOException, FluidException {
		FluidBuilder builder = writer;
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
		Fluid writer = builder.consume(result, req.getRequestURL(), gtype,
				responseTypes);
		HttpEntity entity = writer.asHttpEntity(rClass.getResponseContentType(
				req, method));
		return rbuilder.content(200, "OK", entity);
	}

}