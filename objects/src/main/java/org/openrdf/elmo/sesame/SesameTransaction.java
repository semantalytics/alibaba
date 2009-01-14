/*
 * Copyright (c) 2007, James Leigh All rights reserved.
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
package org.openrdf.elmo.sesame;

import javax.persistence.EntityTransaction;
import javax.persistence.PersistenceException;
import javax.persistence.RollbackException;

import org.openrdf.repository.RepositoryConnection;
import org.openrdf.store.StoreException;

/**
 * JPA EntityTransaction interface for SesameBeanManager.
 * 
 * @author James Leigh
 * 
 */
public class SesameTransaction implements EntityTransaction {

	private RepositoryConnection conn;

	private boolean rollbackOnly = false;

	public SesameTransaction(RepositoryConnection conn) {
		this.conn = conn;
	}

	public void begin() {
		try {
			if (isActive())
				throw new IllegalStateException("Transaction already started");
			conn.setAutoCommit(false);
		} catch (StoreException e) {
			throw new PersistenceException(e);
		}
	}

	public void commit() {
		try {
			if (!isActive())
				throw new IllegalStateException("Transaction has not been started");
			conn.commit();
			conn.setAutoCommit(true);
			rollbackOnly = false;
		} catch (StoreException e) {
			throw new RollbackException(e);
		}
	}

	public void rollback() {
		try {
			if (!isActive())
				throw new IllegalStateException("Transaction has not been started");
			conn.rollback();
			conn.setAutoCommit(true);
			rollbackOnly = false;
		} catch (StoreException e) {
			throw new PersistenceException(e);
		}
	}

	public void setRollbackOnly() {
		if (!isActive())
			throw new IllegalStateException("Transaction has not been started");
		rollbackOnly = true;
	}

	public boolean getRollbackOnly() {
		if (!isActive())
			throw new IllegalStateException("Transaction has not been started");
		return rollbackOnly;
	}

	public boolean isActive() {
		try {
			return !conn.isAutoCommit();
		} catch (StoreException e) {
			throw new PersistenceException(e);
		}
	}

}
