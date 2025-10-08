package com.hmdp.config;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class ElasticsearchConfig {

    @Value("${spring.elasticsearch.uris}")
    private String host;
    
    @Value("${spring.elasticsearch.username:}")
    private String username;
    
    @Value("${spring.elasticsearch.password:}")
    private String password;

    @Bean
    public RestHighLevelClient restHighLevelClient() {
        // 从URL中提取主机名和端口
        String hostInfo = host.replace("http://", "");
        String[] parts = hostInfo.split(":");
        String hostname = parts[0];
        int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 9200;
        
        // 创建 RestClientBuilder
        RestClientBuilder builder = RestClient.builder(new HttpHost(hostname, port, "http"));
        
        // 如果提供了用户名和密码，则设置基本认证
        if (StringUtils.hasText(username) && StringUtils.hasText(password)) {
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY, 
                new UsernamePasswordCredentials(username, password));
            builder.setHttpClientConfigCallback(httpClientBuilder -> 
                httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider));
        }
        
        return new RestHighLevelClient(builder);
    }
}