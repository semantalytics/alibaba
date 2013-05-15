/*
 * Copyright (c) 2010, Zepheira LLC, Some rights reserved.
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
package org.openrdf.sail.optimistic.config;

import org.openrdf.model.Graph;
import org.openrdf.model.Literal;
import org.openrdf.model.Resource;
import org.openrdf.model.URI;
import org.openrdf.model.ValueFactory;
import org.openrdf.model.impl.URIImpl;
import org.openrdf.model.util.GraphUtil;
import org.openrdf.model.util.GraphUtilException;
import org.openrdf.sail.config.DelegatingSailImplConfigBase;
import org.openrdf.sail.config.SailConfigException;
import org.openrdf.sail.config.SailImplConfig;

/**
 * Reads and writes the snapshot and serializable state to configuration graph.
 * 
 * @author James Leigh
 * 
 */
public class OptimisticConfig extends DelegatingSailImplConfigBase {
	public static final URI READSNAPSHOT = new URIImpl(
			"http://www.openrdf.org/config/sail/optimistic#readSnapshot");

	public static final URI SNAPSHOT = new URIImpl(
			"http://www.openrdf.org/config/sail/optimistic#snapshot");

	public static final URI SERIALIZABLE = new URIImpl(
			"http://www.openrdf.org/config/sail/optimistic#serializable");

	private boolean readSnapshot = true;
	private boolean snapshot;
	private boolean serializable;

	public OptimisticConfig() {
		super(OptimisticFactory.SAIL_TYPE);
	}

	public OptimisticConfig(SailImplConfig delegate) {
		super(OptimisticFactory.SAIL_TYPE, delegate);
	}

	public boolean isReadSnapshot() {
		return readSnapshot;
	}

	public void setReadSnapshot(boolean readSnapshot) {
		this.readSnapshot = readSnapshot;
	}

	public boolean isSnapshot() {
		return snapshot;
	}

	public void setSnapshot(boolean snapshot) {
		this.snapshot = snapshot;
	}

	public boolean isSerializable() {
		return serializable;
	}

	public void setSerializable(boolean serializable) {
		this.serializable = serializable;
	}

	@Override
	public Resource export(Graph graph) {
		Resource subj = super.export(graph);
		ValueFactory vf = graph.getValueFactory();
		graph.add(subj, READSNAPSHOT, vf.createLiteral(readSnapshot));
		graph.add(subj, SNAPSHOT, vf.createLiteral(snapshot));
		graph.add(subj, SERIALIZABLE, vf.createLiteral(serializable));
		return subj;
	}

	@Override
	public void parse(Graph graph, Resource subj)
 throws SailConfigException {
		super.parse(graph, subj);
		try {
			Literal lit;
			lit = GraphUtil.getOptionalObjectLiteral(graph, subj, READSNAPSHOT);
			if (lit != null) {
				setReadSnapshot(lit.booleanValue());
			}
			lit = GraphUtil.getOptionalObjectLiteral(graph, subj, SNAPSHOT);
			if (lit != null) {
				setSnapshot(lit.booleanValue());
			}
			lit = GraphUtil.getOptionalObjectLiteral(graph, subj, SERIALIZABLE);
			if (lit != null) {
				setSerializable(lit.booleanValue());
			}
		} catch (GraphUtilException e) {
			throw new SailConfigException(e.toString(), e);
		}
	}

}
