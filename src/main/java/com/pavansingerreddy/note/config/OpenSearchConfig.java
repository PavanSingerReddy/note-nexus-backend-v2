package com.pavansingerreddy.note.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.util.Timeout;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLContext;

@Slf4j
@Configuration
public class OpenSearchConfig {

    @Value("${opensearch.host:localhost}")
    private String host;

    @Value("${opensearch.port:9200}")
    private int port;

    @Value("${opensearch.scheme:http}")
    private String scheme;

    @Value("${opensearch.security-enabled:false}")
    private boolean securityEnabled;

    @Value("${opensearch.username:admin}")
    private String username;

    @Value("${opensearch.password:admin}")
    private String password;

    @Value("${opensearch.trust-self-signed:true}")
    private boolean trustSelfSigned;

    @Bean
    public OpenSearchClient openSearchClient() {
        HttpHost httpHost = new HttpHost(scheme, host, port);
        log.info("Initializing OpenSearch Client pointing to: {}://{}:{}", scheme, host, port);

        ApacheHttpClient5TransportBuilder builder = ApacheHttpClient5TransportBuilder.builder(httpHost);

        builder.setHttpClientConfigCallback(httpClientBuilder -> {
            // 1. Connection Config & Pooling
            ConnectionConfig connectionConfig = ConnectionConfig.custom()
                    .setConnectTimeout(Timeout.ofSeconds(3))
                    .build();

            PoolingAsyncClientConnectionManagerBuilder connectionManagerBuilder = PoolingAsyncClientConnectionManagerBuilder
                    .create()
                    .setDefaultConnectionConfig(connectionConfig)
                    .setMaxConnTotal(100)
                    .setMaxConnPerRoute(30);

            // 2. SSL / TLS Strategy for HTTPS
            if ("https".equalsIgnoreCase(scheme) && trustSelfSigned) {
                try {
                    SSLContext sslContext = SSLContextBuilder.create()
                            .loadTrustMaterial(null, (chains, authType) -> true)
                            .build();
                    connectionManagerBuilder.setTlsStrategy(ClientTlsStrategyBuilder.create()
                            .setSslContext(sslContext)
                            .build());
                } catch (Exception e) {
                    log.error("Failed to setup SSL context for OpenSearch Client", e);
                }
            }

            httpClientBuilder.setConnectionManager(connectionManagerBuilder.build());

            // 3. Response timeouts
            RequestConfig requestConfig = RequestConfig.custom()
                    .setResponseTimeout(Timeout.ofSeconds(5))
                    .build();
            httpClientBuilder.setDefaultRequestConfig(requestConfig);

            // 4. Security (Basic Auth if enabled)
            if (securityEnabled) {
                BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                credentialsProvider.setCredentials(
                        new AuthScope(httpHost),
                        new UsernamePasswordCredentials(username, password.toCharArray()));
                httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
            }

            return httpClientBuilder;
        });

        OpenSearchTransport transport = builder.build();
        return new OpenSearchClient(transport);
    }
}
