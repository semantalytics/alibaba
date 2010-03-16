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

import java.lang.reflect.Method;

import javax.activation.MimeTypeParseException;

import org.openrdf.http.object.annotations.operation;
import org.openrdf.http.object.exceptions.BadRequest;
import org.openrdf.http.object.exceptions.MethodNotAllowed;
import org.openrdf.http.object.exceptions.NotAcceptable;
import org.openrdf.http.object.model.Handler;
import org.openrdf.http.object.model.ResourceOperation;
import org.openrdf.http.object.model.Response;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.repository.RepositoryException;

/**
 * If a GET request cannot be satisfied send a redirect to another operation.
 * 
 * @author James Leigh
 * 
 */
public class AlternativeHandler implements Handler {
	private final Handler delegate;

	public AlternativeHandler(Handler delegate) {
		this.delegate = delegate;
	}

	public Response verify(ResourceOperation req) throws Exception {
		try {
			return delegate.verify(req);
		} catch (MethodNotAllowed e) {
			Response rb = findAlternate(req);
			if (rb != null)
				return rb;
			return methodNotAllowed(req);
		} catch (NotAcceptable e) {
			Response rb = findAlternate(req);
			if (rb != null)
				return rb;
			return new Response().exception(e);
		} catch (BadRequest e) {
			Response rb = findAlternate(req);
			if (rb != null)
				return rb;
			return new Response().exception(e);
		}
	}

	public Response handle(ResourceOperation req) throws Exception {
		try {
			return delegate.handle(req);
		} catch (MethodNotAllowed e) {
			Response rb = findAlternate(req);
			if (rb != null)
				return rb;
			return methodNotAllowed(req);
		} catch (NotAcceptable e) {
			Response rb = findAlternate(req);
			if (rb != null)
				return rb;
			return new Response().exception(e);
		} catch (BadRequest e) {
			Response rb = findAlternate(req);
			if (rb != null)
				return rb;
			return new Response().exception(e);
		}
	}

	private Response methodNotAllowed(ResourceOperation req)
			throws RepositoryException, QueryEvaluationException {
		StringBuilder sb = new StringBuilder();
		sb.append("OPTIONS, TRACE");
		for (String method : req.getAllowedMethods()) {
			sb.append(", ").append(method);
		}
		return new Response().status(405, "Method Not Allowed").header("Allow",
				sb.toString());
	}

	private Response findAlternate(ResourceOperation req)
			throws MimeTypeParseException, RepositoryException,
			QueryEvaluationException {
		String m = req.getMethod();
		if (req.getOperation() != null
				|| !("GET".equals(m) || "HEAD".equals(m)))
			return null;
		Method operation;
		if ((operation = req.getOperationMethod("alternate")) != null) {
			String loc = req.getURI() + "?"
					+ operation.getAnnotation(operation.class).value()[0];
			return new Response().status(302, "Found").location(loc);
		} else if ((operation = req.getOperationMethod("describedby")) != null) {
			String loc = req.getURI() + "?"
					+ operation.getAnnotation(operation.class).value()[0];
			return new Response().status(303, "See Other").location(loc);
		}
		return null;
	}

}
