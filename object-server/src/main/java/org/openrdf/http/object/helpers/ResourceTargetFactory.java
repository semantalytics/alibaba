package org.openrdf.http.object.helpers;

import java.lang.ref.SoftReference;
import java.util.WeakHashMap;

import org.apache.http.protocol.HttpContext;
import org.openrdf.repository.object.RDFObject;

public class ResourceTargetFactory {
	private final WeakHashMap<Class<?>, SoftReference<ResourceClass>> cached = new WeakHashMap<Class<?>, SoftReference<ResourceClass>>();

	public ResourceTarget createResourceTarget(RDFObject target,
			HttpContext context) {
		ResourceClass rClass = createResourceClass(target.getClass());
		return new ResourceTarget(rClass, target, context);
	}

	private synchronized ResourceClass createResourceClass(Class<?> targetClass) {
		ResourceClass rc = null;
		SoftReference<ResourceClass> ref = cached.get(targetClass);
		if (ref != null) {
			rc = ref.get();
		}
		if (rc != null)
			return rc;
		rc = new ResourceClass(targetClass);
		cached.put(targetClass, new SoftReference<ResourceClass>(rc));
		return rc;
	}
}
