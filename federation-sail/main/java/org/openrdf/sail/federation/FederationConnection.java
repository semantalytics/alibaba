/*
 * Copyright Aduna (http://www.aduna-software.com/) (c) 2008-2009.
 *
 * Licensed under the Aduna BSD-style license.
 */
package org.openrdf.sail.federation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.openrdf.cursor.CollectionCursor;
import org.openrdf.cursor.Cursor;
import org.openrdf.model.LiteralFactory;
import org.openrdf.model.Namespace;
import org.openrdf.model.Resource;
import org.openrdf.model.Statement;
import org.openrdf.model.URI;
import org.openrdf.model.URIFactory;
import org.openrdf.model.Value;
import org.openrdf.model.ValueFactory;
import org.openrdf.model.impl.BNodeFactoryImpl;
import org.openrdf.model.impl.ValueFactoryImpl;
import org.openrdf.query.BindingSet;
import org.openrdf.query.algebra.QueryModel;
import org.openrdf.query.algebra.TupleExpr;
import org.openrdf.query.algebra.evaluation.TripleSource;
import org.openrdf.query.algebra.evaluation.cursors.DistinctCursor;
import org.openrdf.query.algebra.evaluation.cursors.UnionCursor;
import org.openrdf.query.algebra.evaluation.impl.BindingAssigner;
import org.openrdf.query.algebra.evaluation.impl.CompareOptimizer;
import org.openrdf.query.algebra.evaluation.impl.ConjunctiveConstraintSplitter;
import org.openrdf.query.algebra.evaluation.impl.ConstantOptimizer;
import org.openrdf.query.algebra.evaluation.impl.DisjunctiveConstraintOptimizer;
import org.openrdf.query.algebra.evaluation.impl.EvaluationStrategyImpl;
import org.openrdf.query.algebra.evaluation.impl.FilterOptimizer;
import org.openrdf.query.algebra.evaluation.impl.QueryJoinOptimizer;
import org.openrdf.query.algebra.evaluation.impl.QueryModelPruner;
import org.openrdf.query.algebra.evaluation.impl.SameTermFilterOptimizer;
import org.openrdf.query.impl.EmptyBindingSet;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.http.helpers.PrefixHashSet;
import org.openrdf.result.ContextResult;
import org.openrdf.result.ModelResult;
import org.openrdf.result.NamespaceResult;
import org.openrdf.result.Result;
import org.openrdf.sail.SailConnection;
import org.openrdf.sail.federation.evaluation.FederationStatistics;
import org.openrdf.sail.federation.evaluation.FederationStrategy;
import org.openrdf.sail.federation.optimizers.EmptyPatternOptimizer;
import org.openrdf.sail.federation.optimizers.FederationJoinOptimizer;
import org.openrdf.sail.federation.optimizers.OwnedTupleExprPruner;
import org.openrdf.sail.federation.optimizers.PrepareOwnedTupleExpr;
import org.openrdf.sail.federation.signatures.BNodeSigner;
import org.openrdf.sail.federation.signatures.SignedConnection;
import org.openrdf.sail.helpers.SailConnectionBase;
import org.openrdf.store.Isolation;
import org.openrdf.store.StoreException;

/**
 * Unions the results from multiple {@link RepositoryConnection} into one
 * {@link SailConnection}.
 * 
 * @author James Leigh
 * @author Arjohn Kampman
 */
abstract class FederationConnection extends SailConnectionBase {

	private final Logger logger = LoggerFactory.getLogger(FederationConnection.class);

	private final Federation federation;

	private final ValueFactory vf;

	final List<SignedConnection> members;

	public FederationConnection(Federation federation, List<RepositoryConnection> members) {
		this.federation = federation;

		BNodeFactoryImpl bf = new BNodeFactoryImpl();
		URIFactory uf = federation.getURIFactory();
		LiteralFactory lf = federation.getLiteralFactory();
		vf = new ValueFactoryImpl(bf, uf, lf);

		this.members = new ArrayList<SignedConnection>(members.size());
		for (RepositoryConnection member : members) {
			BNodeSigner signer = new BNodeSigner(bf, member.getValueFactory());
			this.members.add(signer.sign(member));
		}
	}

	public ValueFactory getValueFactory() {
		return vf;
	}

	@Override
	public void close()
		throws StoreException
	{
		excute(new Procedure() {

			public void run(RepositoryConnection con)
				throws StoreException
			{
				con.close();
			}
		});

		super.close();
	}

	@Override
	public Isolation getTransactionIsolation()
		throws StoreException
	{
		Isolation compatible = Isolation.NONE;
		for (Isolation isolation : Isolation.values()) {
			for (RepositoryConnection member : members) {
				Isolation currently = member.getTransactionIsolation();
				if (!currently.isCompatibleWith(isolation)) {
					return compatible;
				}
			}
			compatible = isolation;
		}
		return compatible;
	}

	@Override
	public void setTransactionIsolation(final Isolation isolation)
		throws StoreException
	{
		super.setTransactionIsolation(isolation);
		excute(new Procedure() {

			public void run(RepositoryConnection con)
				throws StoreException
			{
				con.setTransactionIsolation(isolation);
			}
		});
	}

	public Cursor<? extends Resource> getContextIDs()
		throws StoreException
	{
		Cursor<? extends Resource> cursor = union(new Function<Resource>() {

			public ContextResult call(RepositoryConnection member)
				throws StoreException
			{
				return member.getContextIDs();
			}
		});

		cursor = new DistinctCursor<Resource>(cursor);

		return cursor;
	}

	public String getNamespace(String prefix)
		throws StoreException
	{
		String namespace = null;

		for (RepositoryConnection member : members) {
			String ns = member.getNamespace(prefix);

			if (namespace == null) {
				namespace = ns;
			}
			else if (ns != null && !ns.equals(namespace)) {
				return null;
			}
		}

		return namespace;
	}

	public Cursor<? extends Namespace> getNamespaces()
		throws StoreException
	{
		Map<String, Namespace> namespaces = new HashMap<String, Namespace>();
		Set<String> prefixes = new HashSet<String>();

		for (RepositoryConnection member : members) {
			NamespaceResult ns = member.getNamespaces();
			try {
				while (ns.hasNext()) {
					Namespace next = ns.next();
					String prefix = next.getPrefix();

					if (prefixes.add(prefix)) {
						namespaces.put(prefix, next);
					}
					else if (!next.equals(namespaces.get(prefix))) {
						namespaces.remove(prefix);
					}
				}
			}
			finally {
				ns.close();
			}
		}

		return new CollectionCursor<Namespace>(namespaces.values());
	}

	public long size(Resource subj, URI pred, Value obj, boolean includeInferred, Resource... contexts)
		throws StoreException
	{
		PrefixHashSet hash = federation.getLocalPropertySpace();

		if (federation.isDistinct() || pred != null && hash != null && hash.match(pred.stringValue())) {
			long size = 0;
			for (RepositoryConnection member : members) {
				size += member.sizeMatch(subj, pred, obj, includeInferred, contexts);
			}
			return size;
		}
		else {
			Cursor<? extends Statement> cursor = getStatements(subj, pred, obj, includeInferred, contexts);
			try {
				long size = 0;
				while (cursor.next() != null) {
					size++;
				}
				return size;
			}
			finally {
				cursor.close();
			}
		}
	}

	public Cursor<? extends Statement> getStatements(final Resource subj, final URI pred, final Value obj,
			final boolean includeInferred, final Resource... contexts)
		throws StoreException
	{
		Cursor<? extends Statement> cursor = union(new Function<Statement>() {

			public ModelResult call(RepositoryConnection member)
				throws StoreException
			{
				return member.match(subj, pred, obj, includeInferred, contexts);
			}
		});

		if (!federation.isDistinct() && !isLocal(pred)) {
			// Filter any duplicates
			cursor = new DistinctCursor<Statement>(cursor);
		}

		return cursor;
	}

	public Cursor<? extends BindingSet> evaluate(QueryModel query, BindingSet bindings, boolean includeInferred)
		throws StoreException
	{
		TripleSource tripleSource = new FederationTripleSource(includeInferred);
		EvaluationStrategyImpl strategy = new FederationStrategy(federation, tripleSource, query);
		TupleExpr qry = optimize(query, bindings, strategy);
		return strategy.evaluate(qry, EmptyBindingSet.getInstance());
	}

	private class FederationTripleSource implements TripleSource {

		private final boolean includeInferred;

		public FederationTripleSource(boolean includeInferred) {
			this.includeInferred = includeInferred;
		}

		public Cursor<? extends Statement> getStatements(Resource subj, URI pred, Value obj,
				Resource... contexts)
			throws StoreException
		{
			return FederationConnection.this.getStatements(subj, pred, obj, includeInferred, contexts);
		}

		public ValueFactory getValueFactory() {
			return vf;
		}
	}

	private QueryModel optimize(QueryModel parsed, BindingSet bindings, EvaluationStrategyImpl strategy)
		throws StoreException
	{
		logger.trace("Incoming query model:\n{}", parsed.toString());

		// Clone the tuple expression to allow for more aggressive optimisations
		QueryModel query = parsed.clone();

		new BindingAssigner().optimize(query, bindings);
		new ConstantOptimizer(strategy).optimize(query, bindings);
		new CompareOptimizer().optimize(query, bindings);
		new ConjunctiveConstraintSplitter().optimize(query, bindings);
		new DisjunctiveConstraintOptimizer().optimize(query, bindings);
		new SameTermFilterOptimizer().optimize(query, bindings);
		new QueryModelPruner().optimize(query, bindings);

		FederationStatistics statistics = new FederationStatistics(federation, members, query);
		new QueryJoinOptimizer(statistics).optimize(query, bindings);
		new FilterOptimizer().optimize(query, bindings);

		new EmptyPatternOptimizer(members).optimize(query, bindings);
		boolean distinct = federation.isDistinct();
		PrefixHashSet local = federation.getLocalPropertySpace();
		new FederationJoinOptimizer(members, distinct, local).optimize(query, bindings);
		new OwnedTupleExprPruner().optimize(query, bindings);
		new QueryModelPruner().optimize(query, bindings);
		new QueryJoinOptimizer(statistics).optimize(query, bindings);
		statistics.await(); // let statistics throw any exceptions it has

		new PrepareOwnedTupleExpr(federation.getMetaData()).optimize(query, bindings);

		logger.trace("Optimized query model:\n{}", query.toString());
		return query;
	}

	interface Procedure {

		public void run(RepositoryConnection member)
			throws StoreException;
	}

	void excute(Procedure operation)
		throws StoreException
	{
		StoreException storeExc = null;
		RuntimeException runtimeExc = null;

		for (RepositoryConnection member : members) {
			try {
				operation.run(member);
			}
			catch (StoreException e) {
				logger.error("Failed to execute procedure on federation members", e);
				if (storeExc == null) {
					storeExc = e;
				}
			}
			catch (RuntimeException e) {
				logger.error("Failed to execute procedure on federation members", e);
				if (runtimeExc == null) {
					runtimeExc = e;
				}
			}
		}

		if (storeExc != null) {
			throw storeExc;
		}
		else if (runtimeExc != null) {
			throw runtimeExc;
		}
	}

	private interface Function<E> {

		public Result<E> call(RepositoryConnection member)
			throws StoreException;
	}

	private <E> Cursor<? extends E> union(Function<E> function)
		throws StoreException
	{
		List<Cursor<? extends E>> cursors = new ArrayList<Cursor<? extends E>>(members.size());

		try {
			for (RepositoryConnection member : members) {
				cursors.add(function.call(member));
			}
			return new UnionCursor<E>(cursors);
		}
		catch (StoreException e) {
			closeAll(cursors);
			throw e;
		}
		catch (RuntimeException e) {
			closeAll(cursors);
			throw e;
		}
	}

	private boolean isLocal(URI pred) {
		if (pred == null) {
			return false;
		}

		PrefixHashSet hash = federation.getLocalPropertySpace();
		if (hash == null) {
			return false;
		}

		return hash.match(pred.stringValue());
	}

	private void closeAll(Iterable<? extends Cursor<?>> cursors) {
		for (Cursor<?> cursor : cursors) {
			try {
				cursor.close();
			}
			catch (StoreException e) {
				logger.error("Failed to close cursor", e);
			}
		}
	}
}
