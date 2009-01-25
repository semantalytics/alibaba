package org.openrdf.repository.object;

import javax.interceptor.InvocationContext;

import junit.framework.Test;

import org.openrdf.repository.object.annotations.intercepts;
import org.openrdf.repository.object.annotations.rdf;
import org.openrdf.repository.object.base.ElmoManagerTestCase;

public class PrimitiveWrapperTest extends ElmoManagerTestCase {

	public static Test suite() throws Exception {
		return ElmoManagerTestCase.suite(PrimitiveWrapperTest.class);
	}

	public static class PrimitiveInterceptor {
		@intercepts
		public Object invoke(InvocationContext ctx) throws Exception {
			return ctx.proceed();
		}
	}

	@rdf("urn:PrimitiveBehaviour")
	public static interface PrimitiveBehaviour {
		public Boolean getBoolean();

		public Byte getByte();

		public Character getChar();

		public Double getDouble();

		public Float getFloat();

		public Integer getInt();

		public Short getShort();

		public String getString();

		public void setBoolean(Boolean value);

		public void setByte(Byte value);

		public void setChar(Character value);

		public void setDouble(Double value);

		public void setFloat(Float value);

		public void setInt(Integer value);

		public void setShort(Short value);

		public void setString(String value);
	}

	public static class PrimitiveBehaviourImpl1 implements PrimitiveBehaviour {

		private Boolean booleanValue;

		private Byte byteValue;

		private Character charValue;

		private Double doubleValue;

		private Float floatValue;

		private Integer intValue;

		private Short shortValue;

		private String stringValue;

		public Boolean getBoolean() {
			if (count++ % 2 == 0) {
				return booleanValue;
			}
			return null;
		}

		public Byte getByte() {
			if (count++ % 2 == 0) {
				return byteValue;
			}
			return null;
		}

		public Character getChar() {
			if (count++ % 2 == 0) {
				return charValue;
			}
			return null;
		}

		public Double getDouble() {
			if (count++ % 2 == 0) {
				return doubleValue;
			}
			return null;
		}

		public Float getFloat() {
			if (count++ % 2 == 0) {
				return floatValue;
			}
			return null;
		}

		public Integer getInt() {
			if (count++ % 2 == 0) {
				return intValue;
			}
			return null;
		}

		public Short getShort() {
			if (count++ % 2 == 0) {
				return shortValue;
			}
			return null;
		}

		public String getString() {
			if (count++ % 2 == 0) {
				return stringValue;
			}
			return null;
		}

		public void setBoolean(Boolean value) {
			booleanValue = value;
		}

		public void setByte(Byte value) {
			byteValue = value;
		}

		public void setChar(Character value) {
			charValue = value;
		}

		public void setDouble(Double value) {
			doubleValue = value;
		}

		public void setFloat(Float value) {
			floatValue = value;
		}

		public void setInt(Integer value) {
			intValue = value;
		}

		public void setShort(Short value) {
			shortValue = value;
		}

		public void setString(String value) {
			stringValue = value;
		}

	}

	public static class PrimitiveBehaviourImpl2 implements PrimitiveBehaviour {

		private Boolean booleanValue;

		private Byte byteValue;

		private Character charValue;

		private Double doubleValue;

		private Float floatValue;

		private Integer intValue;

		private Short shortValue;

		private String stringValue;

		public Boolean getBoolean() {
			if (count++ % 2 == 0) {
				return booleanValue;
			}
			return null;
		}

		public Byte getByte() {
			if (count++ % 2 == 0) {
				return byteValue;
			}
			return null;
		}

		public Character getChar() {
			if (count++ % 2 == 0) {
				return charValue;
			}
			return null;
		}

		public Double getDouble() {
			if (count++ % 2 == 0) {
				return doubleValue;
			}
			return null;
		}

		public Float getFloat() {
			if (count++ % 2 == 0) {
				return floatValue;
			}
			return null;
		}

		public Integer getInt() {
			if (count++ % 2 == 0) {
				return intValue;
			}
			return null;
		}

		public Short getShort() {
			if (count++ % 2 == 0) {
				return shortValue;
			}
			return null;
		}

		public String getString() {
			if (count++ % 2 == 0) {
				return stringValue;
			}
			return null;
		}

		public void setBoolean(Boolean value) {
			booleanValue = value;
		}

		public void setByte(Byte value) {
			byteValue = value;
		}

		public void setChar(Character value) {
			charValue = value;
		}

		public void setDouble(Double value) {
			doubleValue = value;
		}

		public void setFloat(Float value) {
			floatValue = value;
		}

		public void setInt(Integer value) {
			intValue = value;
		}

		public void setShort(Short value) {
			shortValue = value;
		}

		public void setString(String value) {
			stringValue = value;
		}

	}

	@rdf("urn:Primitive")
	public static interface PrimitiveConcept {
		@rdf("urn:boolean")
		public Boolean getBoolean();

		@rdf("urn:byte")
		public Byte getByte();

		@rdf("urn:character")
		public Character getChar();

		@rdf("urn:double")
		public Double getDouble();

		@rdf("urn:float")
		public Float getFloat();

		@rdf("urn:integer")
		public Integer getInt();

		@rdf("urn:short")
		public Short getShort();

		@rdf("urn:string")
		public String getString();

		public void setBoolean(Boolean value);

		public void setByte(Byte value);

		public void setChar(Character value);

		public void setDouble(Double value);

		public void setFloat(Float value);

		public void setInt(Integer value);

		public void setShort(Short value);

		public void setString(String value);
	}

	@rdf("urn:PrimitiveClass")
	public static class PrimitiveConceptClass {
		@rdf("urn:boolean")
		private Boolean bool;

		@rdf("urn:byte")
		private Byte b;

		@rdf("urn:char")
		private Character chr;

		@rdf("urn:double")
		private Double doub;

		@rdf("urn:float")
		private Float flo;

		@rdf("urn:int")
		private Integer in;

		@rdf("urn:short")
		private Short shor;

		@rdf("urn:string")
		private String string;

		public Boolean getBool() {
			return bool;
		}

		public void setBool(Boolean bool) {
			this.bool = bool;
		}

		public Byte getB() {
			return b;
		}

		public void setB(Byte b) {
			this.b = b;
		}

		public Character getChr() {
			return chr;
		}

		public void setChr(Character chr) {
			this.chr = chr;
		}

		public Double getDoub() {
			return doub;
		}

		public void setDoub(Double doub) {
			this.doub = doub;
		}

		public Float getFlo() {
			return flo;
		}

		public void setFlo(Float flo) {
			this.flo = flo;
		}

		public Integer getIn() {
			return in;
		}

		public void setIn(Integer in) {
			this.in = in;
		}

		public Short getShor() {
			return shor;
		}

		public void setShor(Short shor) {
			this.shor = shor;
		}

		public String getString() {
			return string;
		}

		public void setString(String string) {
			this.string = string;
		}
	}

	private static final Boolean booleanValue = true;

	private static final Byte byteValue = 1;

	private static final Character charValue = '1';

	static int count;

	private static final Double doubleValue = 1d;

	private static final Float floatValue = 1f;

	private static final Integer intValue = 1;

	private static final Short shortValue = 1;

	private static final String stringValue = "1";

	private PrimitiveConceptClass conceptClass;

	private PrimitiveConcept concept;

	private PrimitiveBehaviour behaviour;

	@Override
	protected void setUp() throws Exception {
		module.addBehaviour(PrimitiveInterceptor.class, "urn:Primitive");
		module.addBehaviour(PrimitiveInterceptor.class, "urn:PrimitiveBehaviour");
		module.addConcept(PrimitiveConcept.class);
		module.addConcept(PrimitiveConceptClass.class);
		module.addConcept(PrimitiveBehaviour.class);
		module.addBehaviour(PrimitiveBehaviourImpl1.class);
		module.addBehaviour(PrimitiveBehaviourImpl2.class);
		super.setUp();
		conceptClass = manager.addType(manager.getObjectFactory().createBlankObject(), PrimitiveConceptClass.class);
		concept = manager.addType(manager.getObjectFactory().createBlankObject(), PrimitiveConcept.class);
		behaviour = manager.addType(manager.getObjectFactory().createBlankObject(), PrimitiveBehaviour.class);
	}

	public void testBoolean() {
		assertEquals(null, conceptClass.getBool());
		conceptClass.setBool(booleanValue);
		assertEquals(booleanValue, conceptClass.getBool());

		assertEquals(null, concept.getBoolean());
		concept.setBoolean(booleanValue);
		assertEquals(booleanValue, concept.getBoolean());

		assertEquals(null, behaviour.getBoolean());
		behaviour.setBoolean(booleanValue);
		assertEquals(booleanValue, behaviour.getBoolean());
	}

	public void testByte() {
		assertEquals(null, conceptClass.getB());
		conceptClass.setB(byteValue);
		assertEquals(byteValue, conceptClass.getB());

		assertEquals(null, concept.getByte());
		concept.setByte(byteValue);
		assertEquals(byteValue, concept.getByte());

		assertEquals(null, behaviour.getByte());
		behaviour.setByte(byteValue);
		assertEquals(byteValue, behaviour.getByte());
	}

	public void testCharacter() {
		assertEquals(null, conceptClass.getChr());
		conceptClass.setChr(charValue);
		assertEquals(charValue, conceptClass.getChr());

		assertEquals(null, concept.getChar());
		concept.setChar(charValue);
		assertEquals(charValue, concept.getChar());

		assertEquals(null, behaviour.getChar());
		behaviour.setChar(charValue);
		assertEquals(charValue, behaviour.getChar());
	}

	public void testDouble() {
		assertEquals(null, conceptClass.getDoub());
		conceptClass.setDoub(doubleValue);
		assertEquals(doubleValue, conceptClass.getDoub());

		assertEquals(null, concept.getDouble());
		concept.setDouble(doubleValue);
		assertEquals(doubleValue, concept.getDouble());

		assertEquals(null, behaviour.getDouble());
		behaviour.setDouble(doubleValue);
		assertEquals(doubleValue, behaviour.getDouble());
	}

	public void testFloat() {
		assertEquals(null, conceptClass.getFlo());
		conceptClass.setFlo(floatValue);
		assertEquals(floatValue, conceptClass.getFlo());

		assertEquals(null, concept.getFloat());
		concept.setFloat(floatValue);
		assertEquals(floatValue, concept.getFloat());

		assertEquals(null, behaviour.getFloat());
		behaviour.setFloat(floatValue);
		assertEquals(floatValue, behaviour.getFloat());
	}

	public void testInteger() {
		assertEquals(null, conceptClass.getIn());
		conceptClass.setIn(intValue);
		assertEquals(intValue, conceptClass.getIn());

		assertEquals(null, concept.getInt());
		concept.setInt(intValue);
		assertEquals(intValue, concept.getInt());

		assertEquals(null, behaviour.getInt());
		behaviour.setInt(intValue);
		assertEquals(intValue, behaviour.getInt());
	}

	public void testShort() {
		assertEquals(null, conceptClass.getShor());
		conceptClass.setShor(shortValue);
		assertEquals(shortValue, conceptClass.getShor());

		assertEquals(null, concept.getShort());
		concept.setShort(shortValue);
		assertEquals(shortValue, concept.getShort());

		assertEquals(null, behaviour.getShort());
		behaviour.setShort(shortValue);
		assertEquals(shortValue, behaviour.getShort());
	}

	public void testString() {
		assertEquals(null, conceptClass.getString());
		conceptClass.setString(stringValue);
		assertEquals(stringValue, conceptClass.getString());

		assertEquals(null, concept.getString());
		concept.setString(stringValue);
		assertEquals(stringValue, concept.getString());

		assertEquals(null, behaviour.getString());
		behaviour.setString(stringValue);
		assertEquals(stringValue, behaviour.getString());
	}
}
