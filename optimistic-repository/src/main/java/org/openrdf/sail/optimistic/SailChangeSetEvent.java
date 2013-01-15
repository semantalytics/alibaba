/*
 * Copyright (c) 2011, 3 Round Stones Inc. Some rights reserved.
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

package org.openrdf.sail.optimistic;

import java.util.ArrayList;
import java.util.List;

import org.openrdf.model.Model;
import org.openrdf.sail.Sail;
import org.openrdf.sail.helpers.DefaultSailChangedEvent;
import org.openrdf.sail.optimistic.helpers.UnionModel;


/**
 * Specialized Sail event that provides change-set detail
 * 
 * @author Steve Battle
 *
 */

class SailChangeSetEvent extends DefaultSailChangedEvent {
	private final List<Model> added = new ArrayList<Model>();
	private final List<Model> removed = new ArrayList<Model>();
	private long time ;

	public SailChangeSetEvent(Sail sail) {
		super(sail);
	}

	public Model getAddedModel() {
		return new UnionModel(added.toArray(new Model[added.size()]));
	}

	public void unionAddedModel(Model added) {
		this.added.add(added);
	}

	public Model getRemovedModel() {
		return new UnionModel(removed.toArray(new Model[removed.size()]));
	}

	public void unionRemovedModel(Model removed) {
		this.removed.add(removed);
	}

	public long getTime() {
		return time;
	}

	public void setTime(long time) {
		this.time = time;
	}


}
