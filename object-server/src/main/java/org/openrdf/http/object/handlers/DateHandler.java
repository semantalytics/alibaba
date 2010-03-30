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

import org.apache.http.protocol.HttpDateGenerator;
import org.openrdf.http.object.model.Handler;
import org.openrdf.http.object.model.ResourceOperation;
import org.openrdf.http.object.model.Response;

/**
 * Adds the HTTP Date Header.
 * 
 * @author James Leigh
 * 
 */
public class DateHandler implements Handler {
	private static final HttpDateGenerator DATE_GENERATOR = new HttpDateGenerator();
	private final Handler delegate;

	public DateHandler(Handler delegate) {
		this.delegate = delegate;
	}

	public Response verify(ResourceOperation request) throws Exception {
		Response resp = delegate.verify(request);
		if (resp != null) {
			resp.header("Date", DATE_GENERATOR.getCurrentDate());
		}
		return resp;
	}

	public Response handle(ResourceOperation request) throws Exception {
		return delegate.handle(request).header("Date",
				DATE_GENERATOR.getCurrentDate());
	}

}
