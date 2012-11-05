package org.openrdf.model.impl;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

import org.openrdf.model.Model;
import org.openrdf.model.Resource;
import org.openrdf.model.Statement;
import org.openrdf.model.URI;
import org.openrdf.model.Value;

/**
 * Enhances the {@link MemoryOverflowModel} by ensuring that the result of
 * {@link #unmodifiable()} is not effected by changes to this primary Model.
 * 
 * @author James Leigh
 * 
 */
public class CopyOnWriteModel extends AbstractModel {
	private static final long serialVersionUID = 526844526087387710L;
	private Collection<Reference<Model>> references;
	private AbstractModel delegate;

	public CopyOnWriteModel() {
		this.delegate = copyModel(null);
		this.references = new ArrayList<Reference<Model>>();
		synchronized (references) {
			references.add(new WeakReference<Model>(this));
		}
	}

	private CopyOnWriteModel(AbstractModel delegate,
			Collection<Reference<Model>> references) {
		this.delegate = delegate;
		this.references = references;
		synchronized (references) {
			references.add(new WeakReference<Model>(this));
		}
	}

	public Model unmodifiable() {
		return new CopyOnWriteModel(new UnmodifiableModel(getSharedDelegate()),
				references);
	}

	public Map<String, String> getNamespaces() {
		return Collections.unmodifiableMap(getSharedDelegate().getNamespaces());
	}

	public String getNamespace(String prefix) {
		return getSharedDelegate().getNamespace(prefix);
	}

	public String setNamespace(String prefix, String name) {
		return getWritableDelegate().setNamespace(prefix, name);
	}

	public String removeNamespace(String prefix) {
		return getWritableDelegate().removeNamespace(prefix);
	}

	public boolean contains(Value subj, Value pred, Value obj,
			Value... contexts) {
		return getSharedDelegate().contains(subj, pred, obj, contexts);
	}

	public int size() {
		return getSharedDelegate().size();
	}

	public boolean clear(Value... contexts) {
		return getWritableDelegate().clear(contexts);
	}

	public boolean add(Resource subj, URI pred, Value obj, Resource... contexts) {
		return getWritableDelegate().add(subj, pred, obj, contexts);
	}

	public boolean remove(Value subj, Value pred, Value obj, Value... contexts) {
		return getWritableDelegate().remove(subj, pred, obj, contexts);
	}

	public Iterator<Statement> iterator() {
		return new DelegatingIterator(getSharedDelegate().iterator());
	}

	public Model filter(final Value subj, final Value pred, final Value obj,
			final Value... contexts) {
		return new FilteredModel(this, subj, pred, obj, contexts) {
			private static final long serialVersionUID = -475666402618133101L;

			@Override
			public Model unmodifiable() {
				return CopyOnWriteModel.this.unmodifiable().filter(subj, pred,
						obj, contexts);
			}

			@Override
			public int size() {
				return getSharedDelegate().filter(subj, pred, obj, contexts)
						.size();
			}

			@Override
			public Iterator<Statement> iterator() {
				return new DelegatingIterator(getSharedDelegate().filter(subj,
						pred, obj, contexts).iterator());
			}
		};
	}

	@Override
	protected synchronized void removeIteration(Iterator<Statement> iter,
			Resource subj, URI pred, Value obj, Resource... contexts) {
		Iterator<Statement> i = ((DelegatingIterator) iter).getDelegate();
		AbstractModel delegate = getWritableDelegate();
		delegate.removeIteration(i, subj, pred, obj, contexts);
	}

	@Override
	protected synchronized void closeIterator(Iterator<?> iter) {
		Iterator<Statement> i = ((DelegatingIterator) iter).getDelegate();
		AbstractModel delegate = getWritableDelegate();
		delegate.closeIterator(i);
	}

	protected AbstractModel copyModel(AbstractModel model) {
		if (model == null)
			return new MemoryOverflowModel();
		return new MemoryOverflowModel(model);
	}

	synchronized AbstractModel getSharedDelegate() {
		return delegate;
	}

	private synchronized AbstractModel getWritableDelegate() {
		synchronized (references) {
			Iterator<Reference<Model>> iter = references.iterator();
			while (iter.hasNext()) {
				if (iter.next().get() == null)
					iter.remove();
			}
			if (references.size() == 1)
				return delegate;
		}
		this.references = new ArrayList<Reference<Model>>();
		synchronized (references) {
			references.add(new WeakReference<Model>(this));
		}
		return delegate = copyModel(delegate);
	}

	private class DelegatingIterator implements Iterator<Statement> {
		private final Iterator<Statement> delegate;

		public DelegatingIterator(Iterator<Statement> delegate) {
			this.delegate = delegate;
		}

		public Iterator<Statement> getDelegate() {
			return delegate;
		}

		public boolean hasNext() {
			return delegate.hasNext();
		}

		public Statement next() {
			return delegate.next();
		}

		public void remove() {
			delegate.remove();
		}

		public String toString() {
			return delegate.toString();
		}
	}

}
