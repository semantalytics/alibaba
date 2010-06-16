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
package org.openrdf.http.object.handlers;

import java.io.Closeable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerConfigurationException;

import org.openrdf.http.object.annotations.expect;
import org.openrdf.http.object.model.Handler;
import org.openrdf.http.object.model.ResourceOperation;
import org.openrdf.http.object.model.Response;
import org.openrdf.http.object.model.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executes a Java Method on the request target and response with the result.
 * 
 * @author James Leigh
 * 
 */
public class InvokeHandler implements Handler {
	private Logger logger = LoggerFactory.getLogger(InvokeHandler.class);

	public Response verify(ResourceOperation request) throws Exception {
		Method method = request.getJavaMethod();
		assert method != null;
		return null;
	}

	public Response handle(ResourceOperation request) throws Exception {
		Method method = request.getJavaMethod();
		assert method != null;
		return invoke(request, method, false);
	}

	private Response invoke(ResourceOperation req, Method method, boolean safe)
			throws Exception {
		try {
			Object[] args;
			try {
				args = req.getParameters(method, req.getBody());
			} catch (ParserConfigurationException e) {
				throw e;
			} catch (TransformerConfigurationException e) {
				throw e;
			} catch (Exception e) {
				return new Response().badRequest(e);
			}
			try {
				ResponseEntity entity = req.invoke(method, args, true);
				if (!safe) {
					req.flush();
				}
				return createResponse(req, method, entity);
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
		}
	}

	private Response createResponse(ResourceOperation req, Method method,
			ResponseEntity entity) throws Exception {
		Response rb = new Response();
		if (entity.isNoContent()) {
			rb = rb.noContent();
		}
		if (method.isAnnotationPresent(expect.class)) {
			String expect = method.getAnnotation(expect.class).value();
			String[] values = expect.split("-");
			try {
				StringBuilder sb = new StringBuilder();
				for (int i = 1; i < values.length; i++) {
					sb.append(values[i].substring(0, 1).toUpperCase());
					sb.append(values[i].substring(1));
					sb.append(" ");
				}
				if (sb.length() > 1) {
					int code = Integer.parseInt(values[0]);
					String phrase = sb.toString().trim();
					rb = rb.status(code, phrase);
					if (code > 300 && code < 400) {
						String location = entity.getLocation();
						if (location != null) {
							return rb.location(location);
						}
					}
				}
			} catch (NumberFormatException e) {
				logger.error(expect, e);
			} catch (IndexOutOfBoundsException e) {
				logger.error(expect, e);
			}
		}
		return rb.entity(entity);
	}

}
