/*
 * Copyright (c) 2007-2009, James Leigh All rights reserved.
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
package org.openrdf.repository.object.composition.helpers;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Set;

import org.openrdf.model.URI;
import org.openrdf.model.ValueFactory;
import org.openrdf.model.impl.ValueFactoryImpl;
import org.openrdf.repository.object.annotations.inverseOf;
import org.openrdf.repository.object.annotations.localized;
import org.openrdf.repository.object.annotations.rdf;
import org.openrdf.repository.object.traits.InternalRDFObject;

/**
 * Creates {@link PropertySet} objects for a given predicate.
 * 
 * @author James Leigh
 */
public class PropertySetFactory {
	public static final String GET_NAME = "getName";
	public static final String CREATE = "createPropertySet";

	private static ValueFactory vf = ValueFactoryImpl.getInstance();

	private String name;

	private Class<?> type;

	private URI predicate;

	private boolean inverse;

	private boolean localized;

	private boolean readOnly;

	private PropertySetModifier modifier;

	public PropertySetFactory(Field field, String predicate) {
		localized = field.isAnnotationPresent(localized.class);
		rdf rdf = field.getAnnotation(rdf.class);
		inverseOf inv = field.getAnnotation(inverseOf.class);
		if (predicate != null) {
			setPredicate(predicate);
		} else if (rdf != null && rdf.value() != null) {
			setPredicate(rdf.value());
		} else if (inv != null && inv.value() != null) {
			setInversePredicate(inv.value());
		}
		assert this.predicate != null;
		name = field.getName();
		type = field.getType();
		if (Set.class.equals(type)) {
			Type t = field.getGenericType();
			if (t instanceof ParameterizedType) {
				ParameterizedType pt = (ParameterizedType) t;
				Type[] args = pt.getActualTypeArguments();
				if (args.length == 1 && args[0] instanceof Class) {
					type = (Class) args[0];
				}
			}
		}
	}

	public PropertySetFactory(PropertyDescriptor property, String predicate) {
		Method getter = property.getReadMethod();
		localized = getter.isAnnotationPresent(localized.class);
		readOnly = property.getWriteMethod() == null;
		rdf rdf = getter.getAnnotation(rdf.class);
		inverseOf inv = getter.getAnnotation(inverseOf.class);
		if (predicate != null) {
			setPredicate(predicate);
		} else if (rdf != null && rdf.value() != null) {
			setPredicate(rdf.value());
		} else if (inv != null && inv.value() != null) {
			setInversePredicate(inv.value());
		}
		assert this.predicate != null;
		name = property.getName();
		type = property.getPropertyType();
		if (Set.class.equals(type)) {
			Type t = property.getReadMethod().getGenericReturnType();
			if (t instanceof ParameterizedType) {
				ParameterizedType pt = (ParameterizedType) t;
				Type[] args = pt.getActualTypeArguments();
				if (args.length == 1 && args[0] instanceof Class) {
					type = (Class) args[0];
				}
			}
		}
	}

	public String getName() {
		return name;
	}

	public Class<?> getPropertyType() {
		return type;
	}

	public URI getPredicate() {
		return predicate;
	}

	public boolean isLocalized() {
		return localized;
	}

	public boolean isReadOnly() {
		return readOnly;
	}

	public boolean isInversed() {
		return inverse;
	}

	public PropertySet createPropertySet(InternalRDFObject bean) {
		CachedPropertySet property = createCachedPropertySet(bean);
		property.setPropertySetFactory(this);
		if (readOnly)
			return new UnmodifiableProperty(property);
		return property;
	}

	protected CachedPropertySet createCachedPropertySet(InternalRDFObject bean) {
		if (inverse)
			return new InversePropertySet(bean, modifier);
		if (localized)
			return new LocalizedPropertySet(bean, modifier);
		return new CachedPropertySet(bean, modifier);
	}

	private void setPredicate(String uri) {
		predicate = vf.createURI(uri);
		inverse = false;
		modifier = new PropertySetModifier(predicate);
	}

	private void setInversePredicate(String uri) {
		predicate = vf.createURI(uri);
		inverse = true;
		modifier = new InversePropertySetModifier(predicate);
	}

}