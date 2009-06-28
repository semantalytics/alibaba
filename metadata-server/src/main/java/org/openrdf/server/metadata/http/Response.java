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
package org.openrdf.server.metadata.http;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.activation.MimeTypeParseException;

import org.openrdf.sail.optimistic.exceptions.ConcurrencyException;
import org.openrdf.server.metadata.concepts.RDFResource;

/**
 * Builds an HTTP response.
 * 
 * @author James Leigh
 */
public class Response {
	private Request req;
	private long lastModified;
	private Object entity;
	private Class<?> type;
	private boolean head;
	private Map<String, String> headers = new HashMap<String, String>();
	private int status = 204;

	public Response() {
		super();
	}

	public Response(Request req) {
		this();
		this.req = req;
	}

	public Response badRequest() {
		this.status = 400;
		return this;
	}

	public Response badRequest(Exception e) {
		this.status = 400;
		this.entity = e;
		return this;
	}

	public Response client(Exception error) {
		this.status = 400;
		this.entity = error;
		return this;
	}

	public Response entity(File entity) {
		this.status = 200;
		this.entity = entity;
		return lastModified(entity.lastModified());
	}

	public Response entity(Object entity) {
		if (entity == null)
			return noContent();
		this.status = 200;
		this.entity = entity;
		return this;
	}

	public Long getLastModified() {
		return lastModified;
	}

	public Object getEntity() {
		return entity;
	}

	public Class<?> getEntityType() {
		return type;
	}

	public void setEntityType(Class<?> type) {
		this.type = type;
	}

	public Set<String> getHeaderNames() throws MimeTypeParseException {
		if (req != null) {
			RDFResource target = req.getRequestedResource();
			lastModified(target.lastModified());
		}
		return headers.keySet();
	}

	public String getHeader(String header) {
		return headers.get(header);
	}

	public int getStatus() {
		return status;
	}

	public Response head() {
		head = true;
		return this;
	}

	public Response header(String header, String value) {
		if (value == null) {
			headers.remove(header);
		} else {
			String existing = headers.get(header);
			if (existing == null) {
				headers.put(header, value);
			} else {
				headers.put(header, existing + "," + value);
			}
		}
		return this;
	}

	public boolean isHead() {
		return head;
	}

	public boolean isNoContent() {
		return status == 204;
	}

	public boolean isOk() {
		return status == 200;
	}

	public Response lastModified(long lastModified) {
		if (lastModified <= 0)
			return this;
		lastModified = lastModified / 1000 * 1000;
		long pre = this.lastModified;
		if (pre >= lastModified)
			return this;
		this.lastModified = lastModified;
		return this;
	}

	public Response location(String location) {
		header("Location", location);
		return this;
	}

	public Response noContent() {
		this.status = 204;
		this.entity = null;
		return this;
	}

	public Response notFound() {
		this.status = 404;
		this.entity = null;
		return this;
	}

	public Response notModified() {
		this.status = 304;
		this.entity = null;
		return this;
	}

	public Response preconditionFailed() {
		this.status = 412;
		this.entity = null;
		return this;
	}

	public Response server(Exception error) {
		this.status = 500;
		this.entity = error;
		return this;
	}

	public Response status(int status) {
		this.status = status;
		return this;
	}

	public Response conflict(ConcurrencyException e) {
		this.status = 409;
		this.entity = e;
		return this;
	}

}
