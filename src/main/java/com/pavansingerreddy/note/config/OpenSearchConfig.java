package com.pavansingerreddy.note.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Configuration
public class OpenSearchConfig {

    @Value("${opensearch.hosts:}")
    private String hosts;

    @Value("${opensearch.host:localhost}")
    private String host;

    @Value("${opensearch.port:9200}")
    private int port;

    @Value("${opensearch.scheme:https}")
    private String scheme;

    @Value("${opensearch.security-enabled:true}")
    private boolean securityEnabled;

    @Value("${opensearch.username:admin}")
    private String username;

    @Value("${opensearch.password:MySecret_OpenSearch_Pass123!}")
    private String password;

    @Value("${opensearch.trust-self-signed:true}")
    private boolean trustSelfSigned;

    private HttpHost[] resolveHttpHosts() {
        if (hosts != null && !hosts.isBlank()) {
            List<HttpHost> hostList = new ArrayList<>();
            for (String rawHost : hosts.split(",")) {
                String trimmed = rawHost.trim();
                if (!trimmed.isEmpty()) {
                    if (trimmed.contains(":")) {
                        String[] parts = trimmed.split(":");
                        String h = parts[0].trim();
                        int p = Integer.parseInt(parts[1].trim());
                        hostList.add(new HttpHost(scheme, h, p));
                    } else {
                        hostList.add(new HttpHost(scheme, trimmed, port));
                    }
                }
            }
            if (!hostList.isEmpty()) {
                return hostList.toArray(new HttpHost[0]);
            }
        }
        return new HttpHost[] { new HttpHost(scheme, host, port) };
    }

    @Bean
    public OpenSearchClient openSearchClient() {
        HttpHost[] httpHosts = resolveHttpHosts();
        log.info("Initializing OpenSearch Client pointing to {} node(s): {}", httpHosts.length, Arrays.toString(httpHosts));

        ApacheHttpClient5TransportBuilder builder = ApacheHttpClient5TransportBuilder.builder(httpHosts);

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
            if ("https".equalsIgnoreCase(scheme)) {
                try {
                    ClientTlsStrategyBuilder tlsBuilder = ClientTlsStrategyBuilder.create();
                    if (trustSelfSigned) {
                        SSLContext sslContext = SSLContextBuilder.create()
                                .loadTrustMaterial(null, (chains, authType) -> true)
                                .build();
                        tlsBuilder.setSslContext(sslContext)
                                .setHostnameVerifier(NoopHostnameVerifier.INSTANCE);
                        log.info(
                                "Configured OpenSearch TLS with self-signed certificate trust and NoopHostnameVerifier");
                    }
                    connectionManagerBuilder.setTlsStrategy(tlsBuilder.build());
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

            // 4. Security (Basic Auth for all target cluster nodes)
            if (securityEnabled) {
                BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                for (HttpHost targetHost : httpHosts) {
                    credentialsProvider.setCredentials(
                            new AuthScope(targetHost),
                            new UsernamePasswordCredentials(username, password.toCharArray()));
                }
                httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
            }

            return httpClientBuilder;
        });

        OpenSearchTransport transport = builder.build();
        return new OpenSearchClient(transport);
    }
}
