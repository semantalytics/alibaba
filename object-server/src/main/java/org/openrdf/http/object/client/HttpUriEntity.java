/*
 * Copyright (c) 2013 3 Round Stones Inc., Some Rights Reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.openrdf.http.object.client;

import java.net.URI;

import org.apache.http.HttpEntity;
import org.apache.http.entity.HttpEntityWrapper;

public class HttpUriEntity extends HttpEntityWrapper {
	private final String systemId;

	public HttpUriEntity(String systemId, HttpEntity wrapped) {
		super(wrapped);
		this.systemId = systemId;
	}

	public String getSystemId() {
		return systemId;
	}

	public URI getURI() {
		return URI.create(systemId);
	}
}
