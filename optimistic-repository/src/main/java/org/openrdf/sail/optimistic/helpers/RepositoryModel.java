/*
 * Copyright (c) 2012, 3 Round Stones Inc. Some rights reserved.
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
package org.openrdf.sail.optimistic.helpers;

import info.aduna.iteration.CloseableIteration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.openrdf.model.Model;
import org.openrdf.model.Namespace;
import org.openrdf.model.Resource;
import org.openrdf.model.Statement;
import org.openrdf.model.URI;
import org.openrdf.model.Value;
import org.openrdf.model.impl.AbstractModel;
import org.openrdf.model.impl.EmptyModel;
import org.openrdf.model.impl.FilteredModel;
import org.openrdf.model.impl.NamespaceImpl;
import org.openrdf.model.util.ModelException;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.RepositoryResult;

/**
 * Model API for a {@link RepositoryConnection}. All {@link RepositoryException}
 * s are wrapped in a {@link ModelException}.
 * 
 * @author James Leigh
 * 
 */
public class RepositoryModel extends AbstractModel {
	private final class StatementIterator implements Iterator<Statement> {
		private final CloseableIteration<Statement, RepositoryException> stmts;
		private Statement last;

		private StatementIterator(
				CloseableIteration<Statement, RepositoryException> stmts) {
			this.stmts = stmts;
		}

		public boolean hasNext() {
			try {
				if (stmts.hasNext())
					return true;
				stmts.close();
				return false;
			} catch (RepositoryException e) {
				throw new ModelException(e);
			}
		}

		public Statement next() {
			try {
				last = stmts.next();
				if (last == null) {
					stmts.close();
				}
				return last;
			} catch (RepositoryException e) {
				throw new ModelException(e);
			}
		}

		public void remove() {
			if (last == null)
				throw new IllegalStateException("next() not yet called");
			RepositoryModel.this.remove(last);
			last = null;
		}
	}

	private final RepositoryConnection con;
	private int size;

	public RepositoryModel(RepositoryConnection con) {
		this.con = con;
	}

	@Override
	public void closeIterator(Iterator<?> iter) {
		super.closeIterator(iter);
		if (iter instanceof StatementIterator) {
			try {
				((StatementIterator) iter).stmts.close();
			} catch (RepositoryException e) {
				throw new ModelException(e);
			}
		}
	}

	public synchronized int size() {
		if (size < 0) {
			try {
				return size = (int) con.size();
			} catch (RepositoryException e) {
				throw new ModelException(e);
			}
		}
		return size;
	}

	public Set<Namespace> getNamespaces() {
		Map<String, Namespace> map = new HashMap<String, Namespace>();
		try {
			RepositoryResult<Namespace> spaces = con.getNamespaces();
			try {
				while (spaces.hasNext()) {
					Namespace ns = spaces.next();
					map.put(ns.getPrefix(), ns);
				}
			} finally {
				spaces.close();
			}
			return new HashSet<Namespace>(map.values());
		} catch (RepositoryException e) {
			throw new ModelException(e);
		}
	}

	public Namespace getNamespace(String prefix) {
		try {
			return new NamespaceImpl(prefix, con.getNamespace(prefix));
		} catch (RepositoryException e) {
			throw new ModelException(e);
		}
	}

	public Namespace setNamespace(String prefix, String name) {
		try {
			String ret = con.getNamespace(prefix);
			con.setNamespace(prefix, name);
			return new NamespaceImpl(prefix, ret);
		} catch (RepositoryException e) {
			throw new ModelException(e);
		}
	}

	public void setNamespace(Namespace namespace) {
		setNamespace(namespace.getPrefix(), namespace.getName());
	}

	public Namespace removeNamespace(String prefix) {
		try {
			String ret = con.getNamespace(prefix);
			con.removeNamespace(prefix);
			return new NamespaceImpl(prefix, ret);
		} catch (RepositoryException e) {
			throw new ModelException(e);
		}
	}

	public boolean contains(Resource subj, URI pred, Value obj,
			Resource... contexts) {
		try {
			if (!isResourceURI(subj, pred) || !isEmptyOrResourcePresent(contexts))
				return false;
			return con.hasStatement((Resource) subj, (URI) pred, obj, false,
					cast(contexts));
		} catch (RepositoryException e) {
			throw new ModelException(e);
		}
	}

	private boolean isResourceURI(Value subj, Value pred) {
		return (subj == null || subj instanceof Resource)
				&& (pred == null || pred instanceof URI);
	}

	public synchronized boolean add(Resource subj, URI pred, Value obj,
			Resource... contexts) {
		if (subj == null || pred == null || obj == null)
			throw new UnsupportedOperationException("Incomplete statement");
		try {
			if (contains(subj, pred, obj, contexts))
				return false;
			if (size >= 0) {
				size++;
			}
			con.add(subj, pred, obj, contexts);
			return true;
		} catch (RepositoryException e) {
			throw new ModelException(e);
		}
	}

	public synchronized boolean clear(Resource... contexts) {
		try {
			if (contains(null, null, null, contexts)) {
				con.clear(cast(contexts));
				size = -1;
				return true;
			}
		} catch (RepositoryException e) {
			throw new ModelException(e);
		}
		return false;
	}

	public synchronized boolean remove(Resource subj, URI pred, Value obj,
			Resource... contexts) {
		try {
			if (contains(subj, pred, obj, contexts)) {
				size = -1;
				con.remove((Resource) subj, (URI) pred, obj, cast(contexts));
				return true;
			}
		} catch (RepositoryException e) {
			throw new ModelException(e);
		}
		return false;
	}

	@Override
	public Iterator<Statement> iterator() {
		try {
			return new StatementIterator(con.getStatements(null, null, null,
					false));
		} catch (RepositoryException e) {
			throw new ModelException(e);
		}
	}

	public Model filter(final Resource subj, final URI pred, final Value obj,
			final Resource... contexts) {
		if (!isResourceURI(subj, pred) || !isEmptyOrResourcePresent(contexts))
			return new EmptyModel(this);
		return new FilteredModel(this, subj, pred, obj, contexts) {

			@Override
			public int size() {
				if (subj == null && pred == null && obj == null) {
					try {
						return (int) con.size(cast(contexts));
					} catch (RepositoryException e) {
						throw new ModelException(e);
					}
				}
				return super.size();
			}

			@Override
			public Iterator<Statement> iterator() {
				try {
					return new StatementIterator(con.getStatements(
							(Resource) subj, (URI) pred, obj, false,
							cast(contexts)));
				} catch (RepositoryException e) {
					throw new ModelException(e);
				}
			}

			@Override
			protected void removeFilteredTermIteration(
					Iterator<Statement> iter, Resource subj, URI pred,
					Value obj, Resource... contexts) {
				RepositoryModel.this.removeTermIteration(iter, subj, pred, obj, contexts);
			}
		};
	}

	@Override
	public synchronized void removeTermIteration(Iterator<Statement> iter,
			Resource subj, URI pred, Value obj, Resource... contexts) {
		try {
			con.remove(subj, pred, obj, contexts);
			size = -1;
		} catch (RepositoryException e) {
			throw new ModelException(e);
		}
	}

	private boolean isEmptyOrResourcePresent(Value[] contexts) {
		if (contexts instanceof Resource[])
			return true;
		if (contexts == null)
			return true;
		if (contexts.length == 0)
			return true;
		Resource[] result = new Resource[contexts.length];
		for (int i = 0; i < result.length; i++) {
			if (contexts[i] == null || contexts[i] instanceof Resource)
				return true;
		}
		return false;
	}

	private Resource[] cast(Value[] contexts) {
		if (contexts instanceof Resource[])
			return (Resource[]) contexts;
		if (contexts == null)
			return new Resource[] { null };
		if (contexts.length == 0)
			return new Resource[0];
		Resource[] result = new Resource[contexts.length];
		for (int i=0;i<result.length;i++) {
			if (contexts[i] == null || contexts[i] instanceof Resource) {
				result[i] = (Resource) contexts[i];
			} else {
				List<Resource> list = new ArrayList<Resource>();
				for (Value v : contexts) {
					if (v == null || v instanceof Resource) {
						list.add((Resource) v);
					}
				}
				return list.toArray(new Resource[list.size()]);
			}
		}
		return result;
	}

}
