package com.mashape.unirest.http.options;

import java.util.HashMap;
import java.util.Map;

import local.org.apache.http.HttpHost;
import local.org.apache.http.client.config.RequestConfig;
import local.org.apache.http.conn.ssl.AllowAllHostnameVerifier;
import local.org.apache.http.impl.client.HttpClientBuilder;
import local.org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import local.org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import local.org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import local.org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import local.org.apache.http.nio.reactor.IOReactorException;

import com.mashape.unirest.http.async.utils.AsyncIdleConnectionMonitorThread;
import com.mashape.unirest.http.utils.SyncIdleConnectionMonitorThread;

public class Options {

	public static final long CONNECTION_TIMEOUT = 10000;
	private static final long SOCKET_TIMEOUT = 60000;
	public static final int MAX_TOTAL = 200;
	public static final int MAX_PER_ROUTE = 20;
	
	private static Map<Option, Object> options = new HashMap<Option, Object>();
	
	public static void setOption(Option option, Object value) {
		options.put(option, value);
	}
	
	public static Object getOption(Option option) {
		return options.get(option);
	}

	static {
		refresh();
	}
	
	public static void refresh() {
		// Load timeouts
		Object connectionTimeout = Options.getOption(Option.CONNECTION_TIMEOUT);
		if (connectionTimeout == null) connectionTimeout = CONNECTION_TIMEOUT;
		Object socketTimeout = Options.getOption(Option.SOCKET_TIMEOUT);
		if (socketTimeout == null) socketTimeout = SOCKET_TIMEOUT;
		
		// Load limits
		Object maxTotal = Options.getOption(Option.MAX_TOTAL);
		if (maxTotal == null) maxTotal = MAX_TOTAL;
		Object maxPerRoute = Options.getOption(Option.MAX_PER_ROUTE);
		if (maxPerRoute == null) maxPerRoute = MAX_PER_ROUTE;
		
		// Load proxy if set
		HttpHost proxy = (HttpHost) Options.getOption(Option.PROXY);
		
		// Create common default configuration
		RequestConfig clientConfig = RequestConfig.custom()
				.setConnectTimeout(((Long) connectionTimeout).intValue())
				.setSocketTimeout(((Long) socketTimeout).intValue())
				.setConnectionRequestTimeout(((Long)socketTimeout).intValue())
				.setProxy(proxy)
				.build();
	
		PoolingHttpClientConnectionManager syncConnectionManager = new PoolingHttpClientConnectionManager();
		syncConnectionManager.setMaxTotal((Integer) maxTotal);
		syncConnectionManager.setDefaultMaxPerRoute((Integer) maxPerRoute);
		
		HttpClientBuilder syncClientBuilder = HttpClientBuilder.create()
				.setDefaultRequestConfig(clientConfig)
				.setConnectionManager(syncConnectionManager);

		SyncIdleConnectionMonitorThread syncIdleConnectionMonitorThread = new SyncIdleConnectionMonitorThread(syncConnectionManager);
		setOption(Option.SYNC_MONITOR, syncIdleConnectionMonitorThread);
		syncIdleConnectionMonitorThread.start();
		
		DefaultConnectingIOReactor ioreactor;
		PoolingNHttpClientConnectionManager asyncConnectionManager;
		try {
			ioreactor = new DefaultConnectingIOReactor();
			asyncConnectionManager = new PoolingNHttpClientConnectionManager(ioreactor);
			asyncConnectionManager.setMaxTotal((Integer) maxTotal);
			asyncConnectionManager.setDefaultMaxPerRoute((Integer) maxPerRoute);
		} catch (IOReactorException e) {
			throw new RuntimeException(e);
		}
		
		HttpAsyncClientBuilder asyncClientBuilder = HttpAsyncClientBuilder.create()
				.setDefaultRequestConfig(clientConfig)
				.setConnectionManager(asyncConnectionManager);
		
		AllowAllHostnameVerifier hostNameVerifier = new AllowAllHostnameVerifier();
		
		Object allowAllHostName = Options.getOption(Option.ALLOW_ALL_HOST_NAMES);
		if(allowAllHostName != null){
			syncClientBuilder = syncClientBuilder.setHostnameVerifier(hostNameVerifier);
			asyncClientBuilder = asyncClientBuilder.setHostnameVerifier(hostNameVerifier);
		}
		
		// Create clients
		setOption(Option.HTTPCLIENT, syncClientBuilder.build());
		setOption(Option.ASYNCHTTPCLIENT, asyncClientBuilder.build());
		setOption(Option.ASYNC_MONITOR, new AsyncIdleConnectionMonitorThread(asyncConnectionManager));	
	}
	
}
