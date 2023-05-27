package ai.openfabric.api.component;

import ai.openfabric.api.model.Worker;
import ai.openfabric.api.model.WorkerStatistics;
import ai.openfabric.api.repository.WorkerRepository;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.command.StatsCmd;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Statistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class BackgroundUpdateThreadComponent {

    public static final int UPDATE_INTERVAL = 2000;
    @Autowired
    private WorkerRepository workerRepository;

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
        Logger logger = LoggerFactory.getLogger(BackgroundUpdateThreadComponent.class);
        try (ListContainersCmd listContainersCmd = DockerAPIService.getClient().listContainersCmd()) {
            List<Container> containers = listContainersCmd.withShowAll(true).exec();

            try {
                // to track how many callbacks are left
                CountDownLatch latch = new CountDownLatch(containers.size());
                ArrayList<Worker> workers = new ArrayList<>();
                containers.forEach(container -> {
                    Optional<Worker> optionalWorker = workerRepository.findById(container.getId());

                    Worker worker = optionalWorker.orElse(new Worker());
                    DockerAPIService.extractInfo(container, worker);

                    // get stats in callback
                    try (StatsCmd statsCmd = DockerAPIService.getClient().statsCmd(worker.getId())) {

                        statsCmd.withNoStream(true).exec(new ResultCallback<Statistics>() {
                            @Override
                            public void onStart(Closeable closeable) {

                            }

                            @Override
                            public void onNext(Statistics object) {

                                WorkerStatistics workerStatistics = Optional.ofNullable(worker.getWorkerStatistics()).orElse(new WorkerStatistics());

                                DockerAPIService.extractStats(object, workerStatistics);

                                workerStatistics.setWorker(worker);
                                worker.setWorkerStatistics(workerStatistics);
                            }

                            @Override
                            public void onError(Throwable throwable) {
                                latch.countDown();
                                throw new RuntimeException("Uncaught error in callback", throwable);
                            }

                            @Override
                            public void onComplete() {
                                latch.countDown();
                            }

                            @Override
                            public void close() throws IOException {

                            }
                        });
                    }
                    workers.add(worker);
                });
                // wait till callbacks finish
                latch.await();
                workerRepository.saveAll(workers);
            } catch (InterruptedException e) {
                throw new RuntimeException("Uncaught InterruptedException", e);
            }
        }
    }
}
