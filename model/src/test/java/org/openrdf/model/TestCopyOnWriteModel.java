package org.openrdf.model;

import java.util.Iterator;

import junit.framework.Test;

import org.openrdf.model.impl.CopyOnWriteModel;
import org.openrdf.model.impl.StatementImpl;
import org.openrdf.model.impl.ValueFactoryImpl;

public class TestCopyOnWriteModel extends TestModel {

	public static Test suite() throws Exception {
		return TestModel.suite(TestCopyOnWriteModel.class);
	}

	public TestCopyOnWriteModel(String name) {
		super(name);
	}

	public Model makeEmptyModel() {
		return new CopyOnWriteModel();
	}

	public void testCopyOnWrite() {
		ValueFactory vf = ValueFactoryImpl.getInstance();
		Model model = new CopyOnWriteModel();
		for (int i = 0; i < 100; i++) {
			model.add(new StatementImpl(vf.createBNode(), vf
					.createURI("urn:test:pred"), vf.createBNode()));
		}
		Iterator<Statement> iter = model.unmodifiable().iterator();
		iter.next();
		for (int i = 0; i < 100; i++) {
			model.add(new StatementImpl(vf.createBNode(), vf
					.createURI("urn:test:pred"), vf.createBNode()));
		}
		iter.next();
	}
}
