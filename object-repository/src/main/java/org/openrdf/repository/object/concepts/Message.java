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
package org.openrdf.repository.object.concepts;

import java.util.Set;

import org.openrdf.repository.object.annotations.parameterTypes;
import org.openrdf.repository.object.annotations.rdf;
import org.openrdf.repository.object.vocabulary.OBJ;

/**
 * Invocation context for behaviour methods. Can be used in conjunction with
 * 
 * @{link {@link parameterTypes} to intersect method invocations.
 * 
 * @author James Leigh
 * 
 */
@rdf(OBJ.NAMESPACE + "Message")
public interface Message {

	/** The parameter values used in this message. */
	Object[] getParameters();

	/** The parameter values used in this message. */
	void setParameters(Object[] objParameters);

	/** Called to allow the message to proceed to the next implementation method. */
	@rdf(OBJ.NAMESPACE + "proceed")
	void proceed();

	/** Single return value of this message. */
	@rdf(OBJ.NAMESPACE + "functionalLiteralResponse")
	Object getFunctionalLiteralResponse();

	/** Single return value of this message. */
	@rdf(OBJ.NAMESPACE + "functionalLiteralResponse")
	void setFunctionalLiteralResponse(Object functionalLiteralResponse);

	/** Single return value of this message. */
	@rdf(OBJ.NAMESPACE + "functionalObjectResponse")
	Object getFunctionalObjectResponse();

	/** Single return value of this message. */
	@rdf(OBJ.NAMESPACE + "functionalObjectResponse")
	void setFunctionalObjectResponse(Object functionalObjectResponse);

	/** The return value of this message. */
	@rdf(OBJ.NAMESPACE + "literalResponse")
	Set<Object> getLiteralResponse();

	/** The return value of this message. */
	@rdf(OBJ.NAMESPACE + "literalResponse")
	void setLiteralResponse(Set<?> literalResponse);

	/** The return value of this message. */
	@rdf(OBJ.NAMESPACE + "objectResponse")
	Set<Object> getObjectResponse();

	/** The return value of this message. */
	@rdf(OBJ.NAMESPACE + "objectResponse")
	void setObjectResponse(Set<?> objectResponse);

	/** The receiver of this message. */
	@rdf(OBJ.NAMESPACE + "target")
	Object getTarget();

	/** The receiver of this message. */
	@rdf(OBJ.NAMESPACE + "target")
	void setTarget(Object target);
}
