package org.openrdf.http.object.helpers;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;

import org.apache.http.HttpConnection;
import org.apache.http.HttpException;
import org.apache.http.HttpInetConnection;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;

public class ObjectContextInterceptor implements HttpRequestInterceptor {
	private final InetAddress LOCALHOST = getLocalHost();
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

	public InetAddress getLocalHost() {
		try {
			return InetAddress.getByName(null);
		} catch (UnknownHostException e) {
			try {
				final Enumeration<NetworkInterface> interfaces = NetworkInterface
						.getNetworkInterfaces();
				while (interfaces != null && interfaces.hasMoreElements()) {
					final Enumeration<InetAddress> addresses = interfaces
							.nextElement().getInetAddresses();
					while (addresses != null && addresses.hasMoreElements()) {
						InetAddress address = addresses.nextElement();
						if (address != null && address.isLoopbackAddress()) {
							return address;
						}
					}
				}
			} catch (SocketException se) {
			}
			throw new AssertionError("Unknown hostname: add the hostname of the machine to your /etc/hosts file.");
		}
	}

}
