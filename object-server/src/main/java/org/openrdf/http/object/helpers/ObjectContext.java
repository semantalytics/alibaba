/*
 * Copyright 2013, 3 Round Stones Inc., Some rights reserved.
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
package org.openrdf.http.object.helpers;

import java.net.InetAddress;
import java.util.LinkedList;
import java.util.Queue;

import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.nio.NHttpConnection;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;

public class ObjectContext implements HttpContext {

	private static final String PROTOCOL_SCHEME = "http.protocol.scheme";
	private static final String NS = ObjectContext.class.getName() + "#";
    private static final String CLIENT_ATTR = NS + "clientAddr";
    private static final String EXCHANGE_ATTR = NS + "exchange";
	private static final String PROCESSING_ATTR = NS + "processing";
	private static final String RECEIVED_ATTR = NS + "receivedOn";
	private static final String TRANSACTION_ATTR = NS + "resourceTransaction";
	private static final String HEAD_ATTR = NS + "headResponse";
	private static final String ORIG_REQUEST_ATTR = NS + "originalRequest";

    public static ObjectContext adapt(HttpContext context) {
        if (context instanceof ObjectContext) {
            return (ObjectContext) context;
        } else {
            return new ObjectContext(context);
        }
    }

    public static ObjectContext fork(HttpContext context) {
    	ObjectContext forked = adapt(new BasicHttpContext(context));
    	forked.setClientAddr(forked.getClientAddr());
    	forked.setExchange(forked.getExchange());
    	forked.setProtocolScheme(forked.getProtocolScheme());
    	forked.setReceivedOn(forked.getReceivedOn());
    	forked.setResourceTarget(forked.getResourceTarget());
    	return forked;
    }

    public static ObjectContext create() {
        return new ObjectContext(new BasicHttpContext());
    }

    private final HttpContext context;

    public ObjectContext() {
        this.context = new BasicHttpContext();
    }

    public ObjectContext(final HttpContext context) {
        this.context = context;
    }

    public String toString() {
    	return context.toString();
    }

	@Override
	public Object getAttribute(String id) {
		return context.getAttribute(id);
	}

	@Override
	public Object removeAttribute(String id) {
		return context.removeAttribute(id);
	}

	@Override
	public void setAttribute(String id, Object obj) {
		context.setAttribute(id, obj);
	}

	public String getProtocolScheme() {
		return getAttribute(PROTOCOL_SCHEME, String.class);
	}

	public void setProtocolScheme(String scheme) {
		setAttribute(PROTOCOL_SCHEME, scheme);
	}

	public long getReceivedOn() {
		Long ret = getAttribute(RECEIVED_ATTR, Long.class);
		return ret == null ? 0 : ret;
	}

	public void setReceivedOn(long received) {
		setAttribute(RECEIVED_ATTR, received);
	}

	public InetAddress getClientAddr() {
		return getAttribute(CLIENT_ATTR, InetAddress.class);
	}

	public void setClientAddr(InetAddress addr) {
		setAttribute(CLIENT_ATTR, addr);
	}

	public ResourceTarget getResourceTarget() {
		return getAttribute(TRANSACTION_ATTR, ResourceTarget.class);
	}

	public void setResourceTarget(ResourceTarget trans) {
		setAttribute(TRANSACTION_ATTR, trans);
	}

	public HttpResponse getDerivedFromHeadResponse() {
		return getAttribute(HEAD_ATTR, HttpResponse.class);
	}

	public void setDerivedFromHeadResponse(HttpResponse head) {
		setAttribute(HEAD_ATTR, head);
	}

	public HttpRequest getOriginalRequest() {
		return getAttribute(ORIG_REQUEST_ATTR, HttpRequest.class);
	}

	public void setOriginalRequest(HttpRequest request) {
		setAttribute(ORIG_REQUEST_ATTR, request);
	}

	public Exchange getExchange() {
		return getAttribute(EXCHANGE_ATTR, Exchange.class);
	}

	public void setExchange(Exchange exchange) {
		if (exchange == null) {
			removeAttribute(EXCHANGE_ATTR);
		} else {
			setAttribute(EXCHANGE_ATTR, exchange);
		}
	}

	public Exchange[] getPendingExchange() {
		NHttpConnection conn = (NHttpConnection) context.getAttribute("http.connection");
		HttpContext ctx = conn == null ? context : conn.getContext();
		Queue<Exchange> queue = (Queue<Exchange>) ctx.getAttribute(PROCESSING_ATTR);
		if (queue == null)
			return null;
		synchronized (queue) {
			return queue.toArray(new Exchange[queue.size()]);
		}
	}

	public Queue<Exchange> getOrCreateProcessingQueue() {
		NHttpConnection conn = (NHttpConnection) context.getAttribute("http.connection");
		HttpContext ctx = conn == null ? context : conn.getContext();
		Queue<Exchange> queue = (Queue<Exchange>) ctx.getAttribute(PROCESSING_ATTR);
		if (queue == null) {
			ctx.setAttribute(PROCESSING_ATTR, queue = new LinkedList<Exchange>());
		}
		return queue;
	}

	private <T> T getAttribute(final String attribname, final Class<T> clazz) {
	    final Object obj = getAttribute(attribname);
	    if (obj == null) {
	        return null;
	    }
	    return clazz.cast(obj);
	}
}
