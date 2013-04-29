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
package org.openrdf.sail.optimistic.helpers;

import java.util.Iterator;
import java.util.Set;

import org.openrdf.model.Model;
import org.openrdf.model.Namespace;
import org.openrdf.model.Resource;
import org.openrdf.model.Statement;
import org.openrdf.model.URI;
import org.openrdf.model.Value;
import org.openrdf.model.impl.AbstractModel;
import org.openrdf.model.impl.EmptyModel;
import org.openrdf.model.impl.LinkedHashModel;

/**
 * Treats multiple models as a read-only combined model.
 */
public class UnionModel extends AbstractModel {
	private static final long serialVersionUID = -2735528661997692589L;

	private class UnionIterator implements Iterator<Statement> {
		private final Model[] models;
		Iterator<Statement> iter;
		int idx = -1;

		public UnionIterator(Model[] models) {
			this.models = models;
		}

		public boolean hasNext() {
			if (iter == null) {
				iter = models[++idx].iterator();
			}
			while (!iter.hasNext() && idx < models.length - 1) {
				iter = models[++idx].iterator();
			}
			return iter.hasNext();
		}

		public Statement next() {
			hasNext();
			return iter.next();
		}

		public void remove() {
			throw new UnsupportedOperationException();
		}
	}

	private final Model[] models;

	public UnionModel(Model... unionOf) {
		if (unionOf == null || unionOf.length == 0) {
			models = new Model[] { new EmptyModel(new LinkedHashModel()) };
		} else {
			models = unionOf;
		}
	}

	@Override
	public Model unmodifiable() {
		Model[] unmodifiables = new Model[models.length];
		for (int i=0; i<unmodifiables.length; i++) {
			unmodifiables[i] = models[i].unmodifiable();
		}
		return new UnionModel(unmodifiables);
	}

	public Set<Namespace> getNamespaces() {
		return models[0].getNamespaces();
	}

	public Namespace getNamespace(String prefix) {
		return models[0].getNamespace(prefix);
	}

	public Namespace setNamespace(String prefix, String name) {
		throw new UnsupportedOperationException();
	}

	public void setNamespace(Namespace namespace) {
		throw new UnsupportedOperationException();
	}

	public Namespace removeNamespace(String prefix) {
		Namespace ret = null;
		for (Model model : models) {
			ret = model.removeNamespace(prefix);
		}
		return ret;
	}

	public boolean contains(Resource subj, URI pred, Value obj,
			Resource... contexts) {
		for (Model model : models) {
			if (model.contains(subj, pred, obj, contexts))
				return true;
		}
		return false;
	}

	public boolean add(Resource subj, URI pred, Value obj, Resource... contexts) {
		throw new UnsupportedOperationException();
	}

	public boolean remove(Resource subj, URI pred, Value obj,
			Resource... contexts) {
		boolean modified = false;
		for (Model model : models) {
			modified |= model.remove(subj, pred, obj, contexts);
		}
		return modified;
	}

	public Model filter(Resource subj, URI pred, Value obj,
			Resource... contexts) {
		final Model[] filter = new Model[models.length];
		for (int i = 0; i < filter.length; i++) {
			filter[i] = models[i].filter(subj, pred, obj, contexts);
		}
		return new UnionModel(filter);
	}

	@Override
	public Iterator<Statement> iterator() {
		return new UnionIterator(models);
	}

	@Override
	public int size() {
		int size = 0;
		for (Model model : models) {
			size += model.size();
		}
		return size;
	}

	@Override
	public void removeTermIteration(Iterator<Statement> union, Resource subj,
			URI pred, Value obj, Resource... contexts) {
		throw new UnsupportedOperationException();
	}

}
