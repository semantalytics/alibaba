package org.openrdf.repository.object;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import junit.framework.Test;

import org.openrdf.repository.object.RDFObject;
import org.openrdf.repository.object.annotations.rdf;
import org.openrdf.repository.object.base.ElmoManagerTestCase;
import org.openrdf.repository.object.traits.Mergeable;

public class ConceptClassTest extends ElmoManagerTestCase {

	public static Test suite() throws Exception {
		return ElmoManagerTestCase.suite(ConceptClassTest.class);
	}

	@rdf("urn:test:Throwable")
	public interface IThrowable {
		@rdf("urn:test:cause")
		IThrowable getCause();

		void setCause(IThrowable cause);

		@rdf("urn:test:message")
		String getMessage();

		void setMessage(String message);

		@rdf("urn:test:stackTrace")
		List<StackTraceItem> getStackTraceItems();

		void setStackTraceItems(List<StackTraceItem> list);
	}

	public abstract static class ThrowableMerger implements IThrowable,
			Mergeable, RDFObject {
		public void merge(Object source) {
			if (source instanceof Throwable) {
				Throwable t = (Throwable) source;
				setMessage(t.getMessage());
				setStackTraceItems((List) Arrays.asList(t.getStackTrace()));
				setCause((IThrowable) getObjectConnection().merge(t.getCause()));
			}
		}
	}

	@rdf("urn:test:StackTrace")
	public interface StackTraceItem {
		@rdf("urn:test:className")
		String getClassName();

		void setClassName(String value);

		@rdf("urn:test:fileName")
		String getFileName();

		void setFileName(String value);

		@rdf("urn:test:lineNumber")
		int getLineNumber();

		void setLineNumber(int value);

		@rdf("urn:test:methodName")
		String getMethodName();

		void setMethodName(String value);

		@rdf("urn:test:nativeMethod")
		boolean isNativeMethod();

		void setNativeMethod(boolean value);
	}

	public abstract static class StackTraceItemMereger implements
			StackTraceItem, Mergeable {
		public void merge(Object source) {
			if (source instanceof StackTraceElement) {
				StackTraceElement e = (StackTraceElement) source;
				setClassName(e.getClassName());
				setFileName(e.getFileName());
				setLineNumber(e.getLineNumber());
				setMethodName(e.getMethodName());
				setNativeMethod(e.isNativeMethod());
			}
		}
	}

	@rdf("urn:test:CodeException")
	public static class CodeException extends Exception {
		private static final long serialVersionUID = 6831592297981512051L;
		private int code;

		public CodeException() {
			super();
		}

		public CodeException(String message, Throwable cause) {
			super(message, cause);
		}

		public CodeException(String message) {
			super(message);
		}

		public CodeException(int code) {
			this.code = code;
		}

		@rdf("urn:test:code")
		public int getCode() {
			return code;
		}
	}

	@rdf("urn:test:Person")
	public static class Person {
		private String surname;
		private Set<String> givenNames = new HashSet<String>();
		private Person spouse;

		@rdf("urn:test:surname")
		public String getSurname() {
			return surname;
		}

		public void setSurname(String surname) {
			this.surname = surname;
		}

		@rdf("urn:test:givenNames")
		public Set<String> getGivenNames() {
			return givenNames;
		}

		public void setGivenNames(Set<String> givenNames) {
			this.givenNames = givenNames;
		}

		@rdf("urn:test:spouse")
		public Person getSpouse() {
			return spouse;
		}

		public void setSpouse(Person spouse) {
			this.spouse = spouse;
		}

		public boolean isMarried() {
			return getSpouse() != null;
		}

		public String toString() {
			return getGivenNames() + " " + getSurname();
		}
	}

	@rdf("urn:test:Compnay")
	public static class Company {
		private String name;
		private Set<Person> employees = new HashSet<Person>();

		@rdf("urn:test:name")
		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		boolean isNamePresent() {
			return this.name != null;
		}

		@rdf("urn:test:employees")
		public Set<Person> getEmployees() {
			return employees;
		}

		public void setEmployees(Set<Person> employees) {
			this.employees = employees;
		}

		public boolean isEmployed(Person employee) {
			return getEmployees().contains(employee);
		}
	
		public Person findByGivenName(String given) {
			Person found = null;
			for (Person person : getEmployees()) {
				if (person.getGivenNames().contains(given)) {
					found = person;
				}
			}
			return found;
		}
	}

	public void testException() throws Exception {
		CodeException e1 = new CodeException(47);
		Exception e = new Exception("my message", e1);
		RDFObject bean = (RDFObject) manager.merge(e);
		Method method = bean.getClass().getMethod("getMessage");
		assertEquals("my message", method.invoke(bean));
		method = bean.getClass().getMethod("getStackTraceItems");
		List list = (List) method.invoke(bean);
		Object st = list.get(0);
		assertTrue(st instanceof StackTraceItem);
		method = st.getClass().getMethod("getClassName");
		assertEquals(getClass().getName(), method.invoke(st));
	}

	public void test_company() throws Exception {
		Company c = new Company();
		Person p = new Person();
		Person w = new Person();
		c.setName("My Company");
		p.getGivenNames().add("me");
		w.getGivenNames().add("my");
		w.setSurname("wife");
		p.setSpouse(w);
		c.getEmployees().add(p);
		c = manager.merge(c);
		p = c.findByGivenName("me");
		w = p.getSpouse();
		assertTrue(p.isMarried());
		assertEquals(Collections.singleton("me"), p.getGivenNames());
		assertEquals("wife", w.getSurname());
		assertTrue(c.isEmployed(p));
		assertFalse(c.isNamePresent());
		c.setName(c.getName());
		assertTrue(c.isNamePresent());
		assertEquals("my wife", w.toString());
	}

	@Override
	protected void setUp() throws Exception {
		module.addConcept(Throwable.class, "urn:test:Throwable");
		module.addConcept(StackTraceElement.class, "urn:test:StackTrace");
		module.addConcept(Exception.class, "urn:test:Exception");
		module.addConcept(CodeException.class);
		module.addConcept(IThrowable.class);
		module.addBehaviour(ThrowableMerger.class);
		module.addConcept(StackTraceItem.class);
		module.addBehaviour(StackTraceItemMereger.class);
		module.addConcept(Person.class);
		module.addConcept(Company.class);
		super.setUp();
	}

}