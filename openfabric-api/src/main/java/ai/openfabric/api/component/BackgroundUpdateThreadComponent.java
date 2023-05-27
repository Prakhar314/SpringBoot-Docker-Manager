package ai.openfabric.api.component;

import ai.openfabric.api.model.Worker;
import ai.openfabric.api.model.WorkerStatistics;
import ai.openfabric.api.repository.WorkerRepository;
import com.github.dockerjava.transport.DockerHttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class BackgroundUpdateThreadComponent {

    public static final int UPDATE_INTERVAL = 2000;
    @Autowired
    private WorkerRepository workerRepository;

    @Autowired
    private JsonParserService jsonParserService;

    @Autowired
    private DockerAPIService dockerAPIService;

    private ScheduledExecutorService executorService;

    @PostConstruct
    public void init() {
        executorService = Executors.newSingleThreadScheduledExecutor();
        executorService.scheduleAtFixedRate(this::updateDB, 0, UPDATE_INTERVAL, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdown();
    }

    @Transactional
    private void updateDB() {
        DockerHttpClient.Response response = dockerAPIService.get("/containers/json?all=true");

        try {
            List<Worker> workers = jsonParserService.getWorkersFromResponse(response);
            workers.forEach(worker -> {
                DockerHttpClient.Response response_stats = dockerAPIService.get("/containers/" + worker.getId() + "/stats?stream=false");
                try {
                    WorkerStatistics workerStatistics = jsonParserService.getWorkerStatisticsFromResponse(response_stats, worker);
                    worker.setWorkerStatistics(workerStatistics);
                } catch (IOException e) {
                    throw new RuntimeException("Uncaught IOException", e);
                }
            });
            workerRepository.saveAll(workers);
        } catch (IOException e) {
            throw new RuntimeException("Uncaught IOException", e);
        }
    }
}
