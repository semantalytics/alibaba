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
package org.openrdf.http.object.traits;

import java.util.Set;

import org.openrdf.http.object.concepts.HTTPFileObject;
import org.openrdf.repository.object.annotations.iri;

public interface DigestRealm extends Realm {

	/**
	 * A string to be displayed to users so they know which username and
	 * password to use. This string should contain at least the name of the host
	 * performing the authentication and might additionally indicate the
	 * collection of users who might have access. An example might be
	 * "registered_users@gotham.news.com".
	 */
	@iri("http://www.openrdf.org/rdf/2009/httpobject#realmAuth")
	String getRealmAuth();

	@iri("http://www.openrdf.org/rdf/2009/httpobject#realmAuth")
	void setRealmAuth(String realmAuth);

	/**
	 * Identifies the security contexts that caused the user agent to initiate
	 * an HTTP request.
	 */
	@iri("http://www.openrdf.org/rdf/2009/httpobject#origin")
	Set<HTTPFileObject> getOrigins();

	@iri("http://www.openrdf.org/rdf/2009/httpobject#origin")
	void setOrigins(Set<HTTPFileObject> origins);
}