/*
 * Copyright Aduna (http://www.aduna-software.com/) (c) 2008.
 *
 * Licensed under the Aduna BSD-style license.
 */
package org.openrdf.sail.federation;

import java.util.List;

import org.openrdf.model.Resource;
import org.openrdf.model.URI;
import org.openrdf.model.Value;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.sail.SailReadOnlyException;
import org.openrdf.store.StoreException;

/**
 * Finishes the {@link FederationConnection} by throwing
 * {@link SailReadOnlyException}s for all write operations.
 * 
 * @author James Leigh
 */
class ReadOnlyConnection extends FederationConnection {

	public ReadOnlyConnection(Federation federation, List<RepositoryConnection> members) {
		super(federation, members);
	}

	public void setNamespace(String prefix, String name)
		throws StoreException
	{
		throw new SailReadOnlyException();
	}

	public void clearNamespaces()
		throws StoreException
	{
		throw new SailReadOnlyException();
	}

	public void removeNamespace(String prefix)
		throws StoreException
	{
		throw new SailReadOnlyException();
	}

	public void addStatement(Resource subj, URI pred, Value obj, Resource... contexts)
		throws StoreException
	{
		throw new SailReadOnlyException();
	}

	public void removeStatements(Resource subj, URI pred, Value obj, Resource... context)
		throws StoreException
	{
		throw new SailReadOnlyException();
	}
}
