/*
 * Copyright (c) 2007-2009, James Leigh All rights reserved.
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
package org.openrdf.repository.object.composition.helpers;

import org.openrdf.cursor.ConvertingCursor;
import org.openrdf.cursor.Cursor;
import org.openrdf.model.Resource;
import org.openrdf.model.Statement;
import org.openrdf.model.Value;
import org.openrdf.repository.contextaware.ContextAwareConnection;
import org.openrdf.repository.object.exceptions.ObjectPersistException;
import org.openrdf.repository.object.exceptions.ObjectStoreException;
import org.openrdf.repository.object.traits.ManagedRDFObject;
import org.openrdf.result.ModelResult;
import org.openrdf.store.StoreException;

/**
 * A set for a given getResource(), predicate.
 * 
 * @author James Leigh
 * 
 * @param <E>
 */
public class InversePropertySet extends CachedPropertySet {

	public InversePropertySet(ManagedRDFObject bean, PropertySetModifier property) {
		super(bean, property);
	}

	@Override
	public boolean contains(Object o) {
		ContextAwareConnection conn = getObjectConnection();
		try {
			Value val = getValue(o);
			return conn.hasMatch((Resource) val, getURI(), getResource());
		} catch (StoreException e) {
			throw new ObjectPersistException(e);
		}
	}

	@Override
	public int size() {
		try {
			ContextAwareConnection conn = getObjectConnection();
			return (int) conn.sizeMatch(null, getURI(), getResource());
		} catch (StoreException e) {
			throw new ObjectStoreException(e);
		}
	}

	@Override
	protected ModelResult getStatements() throws StoreException {
		ContextAwareConnection conn = getObjectConnection();
		return conn.match(null, getURI(), getResource());
	}

	protected Cursor<Value> getValues() throws StoreException {
		return new ConvertingCursor<Statement, Value>(getStatements()) {
			@Override
			protected Value convert(Statement st)
					throws StoreException {
				return st.getSubject();
			}
		};
	}

	@Override
	void remove(ContextAwareConnection conn, Statement stmt)
			throws StoreException {
		assert stmt.getPredicate().equals(getURI());
		remove(conn, (Resource) stmt.getObject(), stmt.getSubject());
	}

}
