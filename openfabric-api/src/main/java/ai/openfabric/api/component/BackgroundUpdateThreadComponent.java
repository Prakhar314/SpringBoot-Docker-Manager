package ai.openfabric.api.component;

import ai.openfabric.api.common.DockerEngineAPI;
import ai.openfabric.api.model.Worker;
import ai.openfabric.api.model.WorkerStatistics;
import ai.openfabric.api.repository.WorkerRepository;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.transport.DockerHttpClient;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Component
public class BackgroundUpdateThreadComponent implements Runnable {

    private final int UPDATE_INTERVAL = 2 * 1000; // 2 seconds

    private final WorkerRepository workerRepository;

    // -----------------------------------------------------------------------------------------------------------------
    // Source: https://stackoverflow.com/questions/57801167/best-approach-to-allocate-dedicated-background-thread-in-spring-boot
    private ExecutorService executorService;

    public BackgroundUpdateThreadComponent(WorkerRepository workerRepository) {
        this.workerRepository = workerRepository;
    }

    @PostConstruct
    public void init() {
        executorService = Executors.newSingleThreadExecutor();
        executorService.execute(this);

    }
    //------------------------------------------------------------------------------------------------------------------

    @PreDestroy
    public void shutdown() {
        executorService.shutdown();
    }

    @Override
    public void run() {
        // Update DB every UPDATE_INTERVAL
        while (true) {

            DockerHttpClient.Response response = DockerEngineAPI.getInstance().get("/containers/json?all=true");

            // store response into workerRepository
            ObjectMapper mapper = new ObjectMapper();
            try {
                List<WorkerJsonParser> workerJsonParserList = mapper.readValue(response.getBody(), mapper.getTypeFactory().constructCollectionType(List.class, WorkerJsonParser.class));

                // use with Worker
                for (WorkerJsonParser workerJsonParser : workerJsonParserList) {
                    String id = workerJsonParser.getId();

                    DockerHttpClient.Response response_stats = DockerEngineAPI.getInstance().get("/containers/" + id + "/stats?stream=false");
                    WorkerStatisticsJsonParser workerStatisticsJsonParser = mapper.readValue(response_stats.getBody(), WorkerStatisticsJsonParser.class);

                    // keep only the list of public ports
                    List<Integer> ports = workerJsonParser.getPorts().stream().map(port -> port.publicPort).collect(Collectors.toList());

                    long usedMemory = 0, cpu_delta = 0, system_cpu_delta = 0, network_in = 0, network_out = 0, block_in = 0, block_out = 0;
                    float cpu_percent = 0;
                    int pid_count = 0, num_cpus = 0;

                    if (workerJsonParser.state.equals("running")) {
                        // useful only when the container is running
                        usedMemory = workerStatisticsJsonParser.memory_stats.usage;
                        usedMemory -= workerStatisticsJsonParser.memory_stats.stats.cache;
                        cpu_delta = workerStatisticsJsonParser.cpu_stats.cpu_usage.total_usage - workerStatisticsJsonParser.precpu_stats.cpu_usage.total_usage;
                        system_cpu_delta = workerStatisticsJsonParser.cpu_stats.system_cpu_usage - workerStatisticsJsonParser.precpu_stats.system_cpu_usage;
                        num_cpus = workerStatisticsJsonParser.cpu_stats.online_cpus;
                        cpu_percent = 100.0f * cpu_delta / system_cpu_delta * num_cpus;
                        pid_count = workerStatisticsJsonParser.pids_stats.current;
                        for (NetworkDevice network : workerStatisticsJsonParser.networks.values()) {
                            network_in += network.rx_bytes;
                            network_out += network.tx_bytes;
                        }
                        List<BlkioStat> blkio_stats = workerStatisticsJsonParser.blkio_stats.io_service_bytes_recursive;

                        for (BlkioStat blkio_stat : blkio_stats) {
                            if (blkio_stat.op.equals("Read")) {
                                block_in += blkio_stat.value;
                            } else if (blkio_stat.op.equals("Write")) {
                                block_out += blkio_stat.value;
                            }
                        }
                    }

                    if(workerRepository.existsById(id)){
                        // update
                        Worker worker = workerRepository.findById(workerJsonParser.getId()).get();
                        worker.setName(workerJsonParser.getNames().get(0));
                        worker.setImage(workerJsonParser.getImage());
                        worker.setStatus(workerJsonParser.getStatus());
                        worker.setRunning(workerJsonParser.getState().equals("running"));
                        worker.setPorts(ports);

                        WorkerStatistics workerStatistics = worker.getWorkerStatistics();
                        workerStatistics.setMemoryUsage(usedMemory);
                        workerStatistics.setCpuUsage(cpu_percent);
                        workerStatistics.setPidCount(pid_count);
                        workerStatistics.setNetworkIn(network_in);
                        workerStatistics.setNetworkOut(network_out);
                        workerStatistics.setBlockIn(block_in);
                        workerStatistics.setBlockOut(block_out);

                        workerRepository.save(worker);
                    }
                    else{
                        // create
                        Worker worker = Worker.builder()
                                .id(workerJsonParser.getId())
                                .name(workerJsonParser.getNames().get(0))
                                .image(workerJsonParser.getImage())
                                .status(workerJsonParser.getStatus())
                                .isRunning(workerJsonParser.getState().equals("running"))
                                .ports(ports)
                                .build();

                        WorkerStatistics workerStatistics = WorkerStatistics.builder().memoryUsage(usedMemory).cpuUsage(cpu_percent).pidCount(pid_count).networkIn(network_in).networkOut(network_out).blockIn(block_in).blockOut(block_out).build();

                        worker.setWorkerStatistics(workerStatistics);
                        workerStatistics.setWorker(worker);

                        workerRepository.save(worker);

                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                Thread.sleep(UPDATE_INTERVAL);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    // For parsing JSON
    @Getter
    @Setter
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class Port{
        String IP;
        int privatePort;
        int publicPort;
        String type;
    }

    @Getter
    @Setter
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class WorkerJsonParser {
        String id;
        List<String> names;
        String image;

        @JsonFormat(shape = JsonFormat.Shape.NUMBER)
        Date created;

        List<Port> ports;
        String state;
        String status;
    }
    @Getter
    @Setter
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class NetworkDevice {
        long rx_bytes;
        long tx_bytes;
    }
    @Getter
    @Setter
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class MemoryStats {
        long cache;
    }
    @Getter
    @Setter
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class Memory {
        long usage;

        MemoryStats stats;
    }
    @Getter
    @Setter
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class CpuUsage {
        long total_usage;
    }
    @Getter
    @Setter
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class Cpu {
        int online_cpus;
        CpuUsage cpu_usage;
        long system_cpu_usage;
    }
    @Getter
    @Setter
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class BlkioStat {
        String op;
        long value;
    }
    @Getter
    @Setter
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class BlkioStats {
        List<BlkioStat> io_service_bytes_recursive;
    }
    @Getter
    @Setter
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class PidsStats {
        int current;
    }

    @Getter
    @Setter
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class WorkerStatisticsJsonParser {

        Map<String, NetworkDevice> networks;
        Memory memory_stats;
        Cpu cpu_stats;
        Cpu precpu_stats;
        PidsStats pids_stats;
        BlkioStats blkio_stats;
    }
}
