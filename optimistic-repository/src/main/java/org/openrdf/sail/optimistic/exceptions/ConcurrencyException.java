package org.openrdf.sail.optimistic.exceptions;

import org.openrdf.repository.RepositoryException;


public class ConcurrencyException extends RepositoryException {
	private static final long serialVersionUID = 6505874891312495635L;

	public ConcurrencyException() {
		super();
	}

	public ConcurrencyException(String msg, Throwable t) {
		super(msg, t);
	}

	public ConcurrencyException(String msg) {
		super(msg);
	}

	public ConcurrencyException(Throwable t) {
		super(t);
	}

}