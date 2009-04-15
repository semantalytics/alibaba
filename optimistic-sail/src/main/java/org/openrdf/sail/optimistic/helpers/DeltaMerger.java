package org.openrdf.sail.optimistic.helpers;

import org.openrdf.model.Model;
import org.openrdf.model.impl.LinkedHashModel;
import org.openrdf.query.BindingSet;
import org.openrdf.query.Dataset;
import org.openrdf.query.algebra.Difference;
import org.openrdf.query.algebra.StatementPattern;
import org.openrdf.query.algebra.TupleExpr;
import org.openrdf.query.algebra.Union;
import org.openrdf.query.algebra.evaluation.QueryOptimizer;
import org.openrdf.query.algebra.helpers.QueryModelVisitorBase;

/**
 * Move delta changes into the query model.
 * 
 * @author James Leigh
 * 
 */
public class DeltaMerger extends QueryModelVisitorBase<RuntimeException>
		implements QueryOptimizer {
	private Model added;
	private Model removed;
	private Dataset dataset;
	private BindingSet bindings;
	private BindingSet additional;
	private boolean modified;

	public DeltaMerger(Model added, Model removed) {
		this.added = added;
		this.removed = removed;
	}

	public DeltaMerger(Model added,
			BindingSet additional) {
		this(added, new LinkedHashModel());
		this.additional = additional;
	}

	public boolean isModified() {
		return modified;
	}

	public void optimize(TupleExpr query, Dataset dataset,
			BindingSet bindings) {
		this.dataset = dataset;
		this.modified = false;
		this.bindings = bindings;
		query.visit(this);
	}

	@Override
	public void meet(StatementPattern sp) throws RuntimeException {
		super.meet(sp);
		ExternalModel externalA = new ExternalModel(sp, dataset, additional);
		ExternalModel externalR = new ExternalModel(sp, dataset, additional);

		Model union = new LinkedHashModel(externalA.filter(added, bindings));
		Model minus = new LinkedHashModel(externalR.filter(removed, bindings));

		TupleExpr node = sp;
		if (!union.isEmpty()) {
			modified = true;
			externalA.setModel(union);
			Union rpl = new Union(externalA, node.clone());
			node.replaceWith(rpl);
			node = rpl;
		}
		if (!minus.isEmpty()) {
			modified = true;
			externalR.setModel(minus);
			Difference rpl = new Difference(node.clone(), externalR);
			node.replaceWith(rpl);
			node = rpl;
		}
	}

}
