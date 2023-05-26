package ai.openfabric.api.common;

import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;

public class DockerEngineAPI {

    private final DockerHttpClient httpClient;

    private DockerEngineAPI() {
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost("tcp://localhost:2375")
                .build();

        httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .build();
    }

    public static DockerEngineAPI getInstance() {
        return new DockerEngineAPI();
    }

    public DockerHttpClient getHttpClient() {
        return httpClient;
    }

    private DockerHttpClient.Response execute(String endpoint, DockerHttpClient.Request.Method method) {
        DockerHttpClient.Request request = DockerHttpClient.Request.builder()
                .method(method)
                .path(endpoint)
                .build();
        DockerHttpClient.Response response = httpClient.execute(request);
        return response;
    }

    public DockerHttpClient.Response get(String endpoint) {
        return execute(endpoint, DockerHttpClient.Request.Method.GET);
    }

    public DockerHttpClient.Response post(String endpoint) {
        return execute(endpoint, DockerHttpClient.Request.Method.POST);
    }
}
