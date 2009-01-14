/*
 * Copyright (c) 2007, James Leigh All rights reserved.
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
package org.openrdf.elmo.sesame;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.namespace.QName;

import junit.framework.Test;

import org.openrdf.elmo.impl.ElmoEntityCompositor;
import org.openrdf.elmo.impl.ElmoMapperClassFactory;
import org.openrdf.elmo.sesame.base.RepositoryTestCase;
import org.openrdf.elmo.sesame.concepts.Person;
import org.openrdf.model.impl.URIImpl;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.object.ObjectConnection;
import org.openrdf.repository.object.ObjectRepository;
import org.openrdf.repository.object.config.ObjectRepositoryConfig;
import org.openrdf.repository.object.config.ObjectRepositoryFactory;
import org.openrdf.rio.RDFFormat;

public class PropertyTest extends RepositoryTestCase {
	private static final String FOAF_BIRTHDAY = "http://xmlns.com/foaf/0.1/birthday";

	public static Test suite() throws Exception {
		return RepositoryTestCase.suite(PropertyTest.class);
	}

	private ObjectRepository factory;

	private ObjectConnection manager;

	public static final QName jbroeksURI = new QName("http://emlo.openrdf.org/model/ElmoSessionTest/","jbroeks");

	@Override
	protected void setUp() throws Exception {
		enableLogging(ElmoMapperClassFactory.class);
		enableLogging(ElmoEntityCompositor.class);
		super.setUp();
		factory = new ObjectRepositoryFactory().createRepository(repository);
		RepositoryConnection conn = repository.getConnection();
		conn.add(getClass().getResourceAsStream("/testcases/sesame-foaf.rdf"), "",
				RDFFormat.RDFXML);
		conn.close();
		this.manager = factory.getConnection();
	}

	@Override
	protected void tearDown() throws Exception {
		manager.close();
		factory.shutDown();
		super.tearDown();
	}

	private void enableLogging(Class clazz) {
		Logger logger = Logger.getLogger(clazz.getName());
		ConsoleHandler handler = new ConsoleHandler();
		logger.addHandler(handler);
		handler.setLevel(Level.FINEST);
		logger.setLevel(Level.FINEST);
	}

	protected <T> T first(Collection<T> set) throws IOException {
		Iterator<T> iter = set.iterator();
		try {
			return iter.next();
		} finally {
			manager.close(iter);
		}
	}

	public void testGetResource() throws Exception {
		Object jbroeks = manager.find(jbroeksURI);
		assertTrue(jbroeks instanceof Person);
		assertNotNull(jbroeks);
	}

	public void testAddLiteralRollback() throws Exception {
		Person jbroeks = (Person) manager.find(jbroeksURI);
		assertNotNull(jbroeks);
		manager.setAutoCommit(false);
		jbroeks.setFoafBirthday("01-01");
		manager.rollback();
		manager.setAutoCommit(true);
		manager.refresh(jbroeks);
		assertEquals(null, jbroeks.getFoafBirthday());
		jbroeks = (Person) manager.find(jbroeksURI);
		assertEquals(null, jbroeks.getFoafBirthday());
	}

	public void testAddLiteral() throws Exception {
		Person jbroeks = (Person) manager.find(jbroeksURI);
		assertNotNull(jbroeks);
		jbroeks.setFoafBirthday("01-01");
		assertEquals("01-01", jbroeks.getFoafBirthday());
		jbroeks.setFoafBirthday("01-01");
	}

	public void testRemoveLiteral() throws Exception {
		Person jbroeks = (Person) manager.find(jbroeksURI);
		assertNotNull(jbroeks);
		jbroeks.setFoafBirthday("01-01");
		assertEquals("01-01", jbroeks.getFoafBirthday());
		RepositoryConnection connection = manager;
		connection.remove(new URIImpl(jbroeksURI.getNamespaceURI() + jbroeksURI.getLocalPart()), new URIImpl(FOAF_BIRTHDAY),
				null);
		manager.refresh(jbroeks);
		assertEquals(null, jbroeks.getFoafBirthday());
	}

	public void testRemoveAddResource() throws Exception {
		Person jbroeks = (Person) manager.find(jbroeksURI);
		assertNotNull(jbroeks);
		assertEquals(27, jbroeks.getFoafKnows().size());
		Person friend = first(jbroeks.getFoafKnows());
		jbroeks.getFoafKnows().remove(friend);
		assertEquals(26, jbroeks.getFoafKnows().size());
		assertFalse(jbroeks.getFoafKnows().contains(friend));
		manager.setAutoCommit(false);
		assertEquals(26, jbroeks.getFoafKnows().size());
		assertFalse(jbroeks.getFoafKnows().contains(friend));
		jbroeks.setFoafKnows(Collections.singleton(friend));
		manager.rollback();
		manager.setAutoCommit(true);
		manager.refresh(jbroeks);
		assertEquals(26, jbroeks.getFoafKnows().size());
		assertFalse(jbroeks.getFoafKnows().contains(friend));
	}
}
