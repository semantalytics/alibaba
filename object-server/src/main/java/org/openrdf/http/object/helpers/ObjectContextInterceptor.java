package org.openrdf.http.object.helpers;

import java.io.IOException;
import java.net.InetAddress;

import org.apache.http.HttpConnection;
import org.apache.http.HttpException;
import org.apache.http.HttpInetConnection;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;
import org.openrdf.http.object.util.DomainNameSystemResolver;

public class ObjectContextInterceptor implements HttpRequestInterceptor {
	private static final InetAddress LOCALHOST = DomainNameSystemResolver
			.getInstance().getLocalHost();
	private final String protocol;

	public ObjectContextInterceptor(String protocol) {
		this.protocol = protocol;
	}

	@Override
	public void process(HttpRequest request, HttpContext context)
			throws HttpException, IOException {
		ObjectContext cc = ObjectContext.adapt(context);
		cc.setReceivedOn(System.currentTimeMillis());
		cc.setClientAddr(getRemoteAddress(context));
		cc.setProtocolScheme(protocol);
	}

	private InetAddress getRemoteAddress(HttpContext context) {
		if (context == null)
			return LOCALHOST;
		HttpConnection con = HttpCoreContext.adapt(context).getConnection();
		if (con instanceof HttpInetConnection) {
			InetAddress remoteAddress = ((HttpInetConnection) con).getRemoteAddress();
			if (remoteAddress != null)
				return remoteAddress;
		}
		return LOCALHOST;
	}

}
