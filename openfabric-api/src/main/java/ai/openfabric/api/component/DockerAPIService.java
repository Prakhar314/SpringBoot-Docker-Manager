package ai.openfabric.api.component;

import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.Duration;

@Component
public class DockerAPIService {

    private static DockerAPIService instance;
    private DockerHttpClient httpClient;

    @PostConstruct
    public void init() {
        Dotenv dotenv = Dotenv.load();
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(
                        dotenv.get("DOCKER_HOST", "tcp://localhost:2375")
                ).build();

        httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .connectionTimeout(Duration.ofSeconds(5))
                .responseTimeout(Duration.ofSeconds(5))
                .build();
    }

    private DockerHttpClient.Response execute(String endpoint, DockerHttpClient.Request.Method method) {
        DockerHttpClient.Request request = DockerHttpClient.Request.builder().method(method).path(endpoint).build();
        return httpClient.execute(request);
    }

    public DockerHttpClient.Response get(String endpoint) {
        return execute(endpoint, DockerHttpClient.Request.Method.GET);
    }

    public DockerHttpClient.Response post(String endpoint) {
        return execute(endpoint, DockerHttpClient.Request.Method.POST);
    }
}
