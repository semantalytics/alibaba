/*
 * Copyright (c) 2012 3 Round Stones Inc., Some rights reserved.
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
package org.openrdf.sail.optimistic;

import org.openrdf.model.Resource;
import org.openrdf.model.URI;
import org.openrdf.model.Value;
import org.openrdf.query.BindingSet;
import org.openrdf.query.Dataset;
import org.openrdf.query.algebra.UpdateExpr;
import org.openrdf.sail.SailConnection;
import org.openrdf.sail.SailException;
import org.openrdf.sail.helpers.SailConnectionWrapper;

public class TransactionalSailConnectionWrapper extends SailConnectionWrapper
		implements TransactionalSailConnection {
	private final TransactionalSailConnection transactional;

	public TransactionalSailConnectionWrapper(SailConnection wrappedCon) {
		super(wrappedCon);
		while (true) {
			if (wrappedCon instanceof TransactionalSailConnection) {
				transactional = (TransactionalSailConnection) wrappedCon;
				break;
			} else if (wrappedCon instanceof SailConnectionWrapper) {
				wrappedCon = ((SailConnectionWrapper) wrappedCon)
						.getWrappedConnection();
			} else {
				transactional = null;
				break;
			}
		}
	}

	public boolean isActive() {
		if (transactional == null)
			return false;
		return transactional.isActive();
	}

	public void begin() throws SailException {
		try {
			if (transactional != null) {
				transactional.begin();
			}
		} catch (SailException e) {
			end(false);
			throw e;
		} catch (RuntimeException e) {
			end(false);
			throw e;
		} catch (Error e) {
			end(false);
			throw e;
		}
	}

	public void prepare() throws SailException {
		try {
			if (transactional != null) {
				transactional.prepare();
			}
		} catch (SailException e) {
			end(false);
			throw e;
		} catch (RuntimeException e) {
			end(false);
			throw e;
		} catch (Error e) {
			end(false);
			throw e;
		}
	}

	@Override
	public void close() throws SailException {
		try {
			super.close();
		} finally {
			end(false);
		}
	}

	@Override
	public void commit() throws SailException {
		boolean success = false;
		try {
			super.commit();
			success = true;
		} finally {
			end(success);
		}
	}

	@Override
	public void rollback() throws SailException {
		try {
			super.rollback();
		} finally {
			end(false);
		}
	}

	public void executeInsert(UpdateExpr updateExpr, Dataset dataset,
			BindingSet bindings, Resource subj, URI pred, Value obj,
			Resource... contexts) throws SailException {
		if (transactional == getWrappedConnection()) {
			transactional.executeInsert(updateExpr, dataset, bindings, subj, pred, obj, contexts);
		} else {
			super.addStatement(subj, pred, obj, contexts);
		}
	}

	public void executeDelete(UpdateExpr updateExpr, Dataset dataset,
			BindingSet bindings, Resource subj, URI pred, Value obj,
			Resource... contexts) throws SailException {
		if (transactional == getWrappedConnection()) {
			transactional.executeDelete(updateExpr, dataset, bindings, subj, pred, obj, contexts);
		} else {
			super.removeStatements(subj, pred, obj, contexts);
		}
	}

	/**
	 * Called from a method that might affect the transaction state, but when
	 * there is must be no active transaction, such as when the transaction has
	 * ended or an exception is being thrown from a delegate.
	 */
	protected void end(boolean commit) {
	}

}
