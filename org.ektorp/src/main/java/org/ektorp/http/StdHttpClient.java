package org.ektorp.http;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.ektorp.util.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Map;

/**
 * 
 * @author henrik lundgren
 * 
 */
public class StdHttpClient implements HttpClient {

	private final org.apache.http.client.HttpClient client;
	private final org.apache.http.client.HttpClient backend;
	private final HttpHost httpHost;
	private final static Logger LOG = LoggerFactory
			.getLogger(StdHttpClient.class);

	public StdHttpClient(org.apache.http.client.HttpClient hc, HttpHost httpHost) {
		this(hc, hc, httpHost);
	}
	public StdHttpClient(org.apache.http.client.HttpClient hc, 
			org.apache.http.client.HttpClient backend, HttpHost httpHost) {
		this.client = hc;
		this.backend = backend;
		this.httpHost = httpHost;
	}

	public org.apache.http.client.HttpClient getClient() {
		return client;
	}

	public org.apache.http.client.HttpClient getBackend() {
		return backend;
	}

	@Override
	public HttpResponse delete(String uri) {
		return executeRequest(new HttpDelete(uri));
	}

	@Override
	public HttpResponse get(String uri) {
		return executeRequest(new HttpGet(uri));
	}

	@Override
	public HttpResponse get(String uri, Map<String, String> headers) {
		return executeRequest(new HttpGet(uri), headers);
	}

	@Override
	public HttpResponse getUncached(String uri) {
		return executeRequest(new HttpGet(uri), true);
	}

	@Override
	public HttpResponse postUncached(String uri, String content) {
		return executePutPost(new HttpPost(uri), content, true);
	}

	@Override
	public HttpResponse post(String uri, String content) {
		return executePutPost(new HttpPost(uri), content, false);
	}

	@Override
	public HttpResponse post(String uri, InputStream content) {
		InputStreamEntity e = new InputStreamEntity(content, -1);
		e.setContentType("application/json");
		return post(uri, e);
	}

	@Override
	public HttpResponse post(String uri, HttpEntity httpEntity) {
		HttpPost post = new HttpPost(uri);
		post.setEntity(httpEntity);
		return executeRequest(post, true);
	}

	@Override
	public HttpResponse put(String uri, String content) {
		return executePutPost(new HttpPut(uri), content, false);
	}

	@Override
	public HttpResponse put(String uri) {
		return executeRequest(new HttpPut(uri));
	}

	@Override
	public HttpResponse put(String uri, InputStream data, String contentType,
			long contentLength) {
		InputStreamEntity e = new InputStreamEntity(data, contentLength);
		e.setContentType(contentType);
		return put(uri, e);
	}

	@Override
	public HttpResponse put(String uri, HttpEntity httpEntity) {
		HttpPut hp = new HttpPut(uri);
		hp.setEntity(httpEntity);
		return executeRequest(hp);
	}

	@Override
	public HttpResponse head(String uri) {
		return executeRequest(new HttpHead(uri));
	}

	protected HttpResponse executePutPost(HttpEntityEnclosingRequestBase request,
			String content, boolean useBackend) {
		try {
			LOG.trace("Content: {}", content);
			StringEntity e = new StringEntity(content, "UTF-8");
			e.setContentType("application/json");
			request.setEntity(e);
			return executeRequest(request, useBackend);
		} catch (Exception e) {
			throw Exceptions.propagate(e);
		}
	}



	protected HttpResponse executeRequest(HttpRequestBase request, Map<String, String> headers) {
		for(Map.Entry<String, String> header : headers.entrySet()) {
			request.setHeader(header.getKey(), header.getValue());
		}
		return executeRequest(request);
	}

	protected HttpResponse executeRequest(HttpUriRequest request, boolean useBackend) {
		try {
			AuthCache authCache = new BasicAuthCache();
			BasicScheme basicScheme = new BasicScheme();
			authCache.put(getHttpHost(), basicScheme);
			HttpClientContext context = HttpClientContext.create();
			context.setAuthCache(authCache);

			org.apache.http.HttpResponse rsp;
			if (useBackend) {
				rsp = backend.execute(request);
			} else {
				rsp = client.execute(getHttpHost(), request, context);
			}
			LOG.trace("{} {} {} {}", new Object[] { request.getMethod(), request.getURI(),
					rsp.getStatusLine().getStatusCode(), rsp.getStatusLine().getReasonPhrase() });
			return createHttpResponse(rsp, request);
		} catch (Exception e) {
			throw Exceptions.propagate(e);
		}		
	}

	protected HttpResponse createHttpResponse(org.apache.http.HttpResponse rsp, HttpUriRequest httpRequest) {
		return new StdHttpResponse(rsp.getEntity(), rsp.getStatusLine(), httpRequest, rsp.getFirstHeader("ETag"));
	}

	protected HttpResponse executeRequest(HttpRequestBase request) {
		return executeRequest(request, false);
	}

	@Override
	public HttpResponse copy(String sourceUri, String destination) {
		return executeRequest(new HttpCopyRequest(sourceUri, destination), true);
	}
	
	public void shutdown() {
		// noop for now...
		System.out.println("! StdHttpClient.shutdown() is a noop!");
	}

	protected HttpHost getHttpHost() {
		return this.httpHost;
	}
}
