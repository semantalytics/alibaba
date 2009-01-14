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
package org.openrdf.elmo.sesame.iterators;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.persistence.NoResultException;
import javax.persistence.NonUniqueResultException;

import org.openrdf.repository.object.exceptions.ElmoIOException;
import org.openrdf.repository.object.exceptions.ElmoPersistException;
import org.openrdf.result.Result;
import org.openrdf.store.StoreException;

/**
 * A general purpose iteration wrapping Sesame's iterations. This class converts
 * the results, converts the Exceptions into ElmoRuntimeExeptions, and ensures
 * that the iteration is closed when all values have been read (on {
 * {@link #next()}).
 * 
 * @author James Leigh
 * 
 * @param <S>
 *            Type of the delegate (Statement)
 * @param <E>
 *            Type of the result
 */
public abstract class ElmoIteration<S, E> implements Iterator<E>, Closeable {

	public static void close(Iterator<?> iter) {
		try {
			if (iter instanceof Closeable)
				((Closeable) iter).close();
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new ElmoIOException(e);
		}
	}

	private Result<? extends S> delegate;

	private S element;

	public ElmoIteration(Result<? extends S> delegate) {
		this.delegate = delegate;
		if (!hasNext())
			close();
	}

	public boolean hasNext() {
		try {
			return delegate.hasNext();
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new ElmoIOException(e);
		}
	}

	public E next() {
		try {
			S next = element = delegate.next();
			if (next == null) {
				close();
				return null;
			}
			return convert(next);
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new ElmoIOException(e);
		}
	}

	public void remove() {
		try {
			remove(element);
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new ElmoPersistException(e);
		}
	}

	public void close() {
		try {
			delegate.close();
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new ElmoIOException(e);
		}
	}

	protected abstract E convert(S element) throws Exception;

	protected void remove(S element) throws Exception {
		throw new UnsupportedOperationException();
	}

	public E getSingle() throws StoreException {
		try {
			E next = next();
			if (next == null)
				throw new NoResultException("No result");
			if (next() != null)
				throw new NonUniqueResultException("More than one result");
			return next;
		} finally {
			close();
		}
	}

	public List<E> asList() throws StoreException {
		return addTo(new ArrayList<E>());
	}

	public Set<E> asSet() throws StoreException {
		return addTo(new HashSet<E>());
	}

	public <C extends Collection<? super E>> C addTo(C collection)
			throws StoreException {
		try {
			E next;
			while ((next = next()) != null) {
				collection.add(next);
			}

			return collection;
		} finally {
			close();
		}
	}
}
