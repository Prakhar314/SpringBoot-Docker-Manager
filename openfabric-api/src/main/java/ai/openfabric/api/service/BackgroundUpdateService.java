package ai.openfabric.api.service;

import ai.openfabric.api.model.Worker;
import ai.openfabric.api.model.WorkerStatistics;
import ai.openfabric.api.repository.WorkerRepository;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.command.StatsCmd;
import com.github.dockerjava.api.exception.InternalServerErrorException;
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

/**
 * A component that runs in the background to update the database periodically
 */
@Component
public class BackgroundUpdateService {

    public static final int UPDATE_INTERVAL = 2000;  // 2 seconds
    @Autowired
    private WorkerRepository workerRepository;

    @Autowired
    private DockerAPIService dockerAPIService;

    private ScheduledExecutorService executorService;

    @PostConstruct
    public void init() {
        // start a thread to update the database periodically
        executorService = Executors.newSingleThreadScheduledExecutor();
        executorService.scheduleAtFixedRate(this::updateDB, 0, UPDATE_INTERVAL, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdown();
    }

    /**
     * Update the database with the latest information from Docker Engine API
     */
    @Transactional
    public void updateDB() {
        Logger logger = LoggerFactory.getLogger(BackgroundUpdateService.class);
        try (ListContainersCmd listContainersCmd = dockerAPIService.getClient().listContainersCmd()) {
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
                    try (StatsCmd statsCmd = dockerAPIService.getClient().statsCmd(worker.getId())) {

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
                boolean finished = latch.await(5, TimeUnit.SECONDS);
                if (!finished) {
                    logger.warn("Timeout waiting for callbacks to finish");
                    return;
                }
                workerRepository.saveAll(workers);
            } catch (InterruptedException e) {
                throw new RuntimeException("Uncaught InterruptedException", e);
            }
        } catch (InternalServerErrorException e) {
            // Docker API failed, try again next time
        } catch (Exception e) {
            throw new RuntimeException("Uncaught exception", e);
        }
    }
}
