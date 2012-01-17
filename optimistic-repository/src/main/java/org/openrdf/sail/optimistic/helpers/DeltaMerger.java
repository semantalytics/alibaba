/*
 * Copyright (c) 2009, James Leigh All rights reserved.
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

		Model union = externalA.filter(added, bindings);
		Model minus = externalR.filter(removed, bindings);

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
