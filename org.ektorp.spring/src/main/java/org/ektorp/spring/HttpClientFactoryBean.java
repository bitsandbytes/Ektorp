package org.ektorp.spring;

import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.util.Properties;

import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.ssl.SSLContextBuilder;
import org.ektorp.http.HttpClient;
import org.ektorp.http.RestTemplate;
import org.ektorp.http.StdHttpClient;
import org.ektorp.http.StdResponseHandler;
import org.ektorp.support.CouchDbRepositorySupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.DirectFieldAccessor;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

/**
 * FactoryBean that produces a HttpClient.
 * Configuration parameters are set through @Value annotations.
 * 
 * The application context must define properties along the line of:
 * <code>
 * <util:properties id="couchdbProperties" location="classpath:/couchdb.properties"/>
 * </code>
 * @author henrik lundgren
 *
 */
public class HttpClientFactoryBean implements FactoryBean<HttpClient>, InitializingBean, DisposableBean {

	private final static Logger LOG = LoggerFactory.getLogger(HttpClientFactoryBean.class);
	
	protected HttpClient client;
	protected HttpHost httpHost;
	
	public String url;
	public String host = "localhost";
	public int port = 5984;
	public int maxConnections = 20;
	public int connectionTimeout = 1000;
	public int socketTimeout = 10000;
	public boolean autoUpdateViewOnChange;
	public String username;
	public String password;
	public boolean testConnectionAtStartup;
	public boolean cleanupIdleConnections = true;
	public boolean enableSSL = false;
	public boolean relaxedSSLSettings;
	public boolean caching = true;
	public int maxCacheEntries = 1000;
	public int maxObjectSizeBytes = 8192;
	public boolean useExpectContinue = true;

	protected SSLSocketFactory sslSocketFactory;

	protected Properties couchDBProperties;
	
	@Override
	public HttpClient getObject() throws Exception {
		return client;
	}

	protected void configureAutoUpdateViewOnChange() {
		if (autoUpdateViewOnChange && !Boolean.getBoolean(CouchDbRepositorySupport.AUTO_UPDATE_VIEW_ON_CHANGE)) {
			System.setProperty(CouchDbRepositorySupport.AUTO_UPDATE_VIEW_ON_CHANGE, Boolean.TRUE.toString());
		}
	}

	protected void testConnect(HttpClient client) {
		try {
			RestTemplate rt = new RestTemplate(client);
			rt.head("/", new StdResponseHandler<Void>());
		} catch (Exception e) {
			throw new BeanCreationException(String.format("Cannot connect to CouchDb@%s:%s", host, port), e);
		}
	}

	@Override
	public Class<? extends HttpClient> getObjectType() {
		return HttpClient.class;
	}

	@Override
	public boolean isSingleton() {
		return true;
	}

	public void setAutoUpdateViewOnChange(boolean b) {
		this.autoUpdateViewOnChange = b;
	}
	
	public void setUsername(String user) {
		this.username = user;
	}
	
	public void setPassword(String s) {
		this.password = s;
	}
	
	public void setUseExpectContinue(boolean value) {
		this.useExpectContinue = value;
	}
	
	public void setTestConnectionAtStartup(boolean b) {
		this.testConnectionAtStartup = b;
	}
	
	public void setSslSocketFactory(SSLSocketFactory sslSocketFactory) {
		this.sslSocketFactory = sslSocketFactory;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public void setMaxConnections(int maxConnections) {
		this.maxConnections = maxConnections;
	}

	public void setConnectionTimeout(int connectionTimeout) {
		this.connectionTimeout = connectionTimeout;
	}

	public void setSocketTimeout(int socketTimeout) {
		this.socketTimeout = socketTimeout;
	}

	public void setCleanupIdleConnections(boolean cleanupIdleConnections) {
		this.cleanupIdleConnections = cleanupIdleConnections;
	}

	public void setEnableSSL(boolean enableSSL) {
		this.enableSSL = enableSSL;
	}

	public void setRelaxedSSLSettings(boolean relaxedSSLSettings) {
		this.relaxedSSLSettings = relaxedSSLSettings;
	}

	public void setCaching(boolean caching) {
		this.caching = caching;
	}

	public void setMaxCacheEntries(int maxCacheEntries) {
		this.maxCacheEntries = maxCacheEntries;
	}

	public void setMaxObjectSizeBytes(int maxObjectSizeBytes) {
		this.maxObjectSizeBytes = maxObjectSizeBytes;
	}
	
	public void setUrl(String url) {
		this.url = url;
	}
	
	public void setProperties(Properties p) {
		this.couchDBProperties = p;
	}

	/**
	 * Shutdown the HTTP Client when destroying the bean
	 */
	@Override
	public void destroy() throws Exception {
		LOG.info("Stopping couchDb connector...");
		if (client != null) {
			client.shutdown();
		}
	}

	/**
	 * Create the couchDB connection when starting the bean factory
	 */
	@Override
	public void afterPropertiesSet() throws Exception {
		if (couchDBProperties != null) {
			new DirectFieldAccessor(this).setPropertyValues(couchDBProperties);
		}
		LOG.info("Starting couchDb connector on {}:{}...",  new Object[]{host,port});
		LOG.debug("host: {}", host);
		LOG.debug("port: {}", port);
		LOG.debug("url: {}", url);
		LOG.debug("maxConnections: {}", maxConnections);
		LOG.debug("connectionTimeout: {}", connectionTimeout);
		LOG.debug("socketTimeout: {}", socketTimeout);
		LOG.debug("autoUpdateViewOnChange: {}", autoUpdateViewOnChange);
		LOG.debug("testConnectionAtStartup: {}", testConnectionAtStartup);
		LOG.debug("cleanupIdleConnections: {}", cleanupIdleConnections);
		LOG.debug("enableSSL: {}", enableSSL);
		LOG.debug("relaxedSSLSettings: {}", relaxedSSLSettings);
		LOG.debug("useExpectContinue: {}", useExpectContinue);

		httpHost = new HttpHost("md-020.trinityalps.org", 6984, "https");
		client = new StdHttpClient(setupHttpClient(httpHost), httpHost);
		
		if (testConnectionAtStartup) {
			testConnect(client);
		}
		
		configureAutoUpdateViewOnChange();
	}

	public static org.apache.http.client.HttpClient setupHttpClient(HttpHost httpHost) {
		HttpClientBuilder clientBuilder = HttpClientBuilder.create();
		CredentialsProvider credsProvider = new BasicCredentialsProvider();
		credsProvider.setCredentials(
				new AuthScope(httpHost.getHostName(), httpHost.getPort()),
				new UsernamePasswordCredentials("admin", "RunT0Th3Hills!"));
		clientBuilder.setDefaultCredentialsProvider(credsProvider);
		try {
			SSLContextBuilder sslBuilder = SSLContextBuilder.create();
			String trustStoreFile = System.getProperty("mobdata.trustStore");
			String trustStorePassword = System.getProperty("mobdata.trustStorePassword");
			if (StringUtils.isNotBlank(trustStoreFile) && StringUtils.isNotBlank(
					trustStorePassword)) {
				sslBuilder.loadTrustMaterial(new File(trustStoreFile),
						trustStorePassword.toCharArray(), new TrustSelfSignedStrategy());
			}

			String keyStoreFile = System.getProperty("mobdata.keyStore");
			String keyStorePassword = System.getProperty("mobdata.keyStorePassword");
			if (StringUtils.isNotBlank(keyStoreFile) && StringUtils.isNotBlank(keyStorePassword)) {
				KeyStore keyStore = KeyStore.getInstance("JKS");
				FileInputStream fis = new FileInputStream(keyStoreFile);
				keyStore.load(fis, keyStorePassword.toCharArray());
				sslBuilder.loadKeyMaterial(keyStore, keyStorePassword.toCharArray());
			}

			SSLConnectionSocketFactory sslFactory =
					new SSLConnectionSocketFactory(sslBuilder.build(), new String[]{"TLSv1.2"},
							null, new HostnameVerifier() {
						@Override
						public boolean verify(final String hostname, final SSLSession session) {
							return true;
						}
					});
			clientBuilder.setSSLSocketFactory(sslFactory);
		} catch (Exception e) {
			throw new IllegalArgumentException("Error configuring server certificates.", e);
		}
		return clientBuilder.build();
	}

}
