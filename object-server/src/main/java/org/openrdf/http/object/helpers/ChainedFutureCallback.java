package org.openrdf.http.object.helpers;

import org.apache.http.HttpResponse;
import org.apache.http.concurrent.BasicFuture;
import org.apache.http.concurrent.FutureCallback;

public class ChainedFutureCallback implements FutureCallback<HttpResponse> {

    private final BasicFuture<HttpResponse> wrapped;

    public ChainedFutureCallback(final BasicFuture<HttpResponse> delegate) {
        this.wrapped = delegate;
    }

    @Override
    public void completed(final HttpResponse result) {
        this.wrapped.completed(result);
    }

    @Override
    public void failed(final Exception ex) {
        this.wrapped.failed(ex);
    }

    @Override
    public void cancelled() {
        this.wrapped.cancel();
    }

}
