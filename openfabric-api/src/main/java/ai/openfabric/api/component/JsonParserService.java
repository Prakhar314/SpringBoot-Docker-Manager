package ai.openfabric.api.component;

import ai.openfabric.api.model.Worker;
import ai.openfabric.api.model.WorkerStatistics;
import ai.openfabric.api.repository.WorkerRepository;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.transport.DockerHttpClient;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class JsonParserService {

    @Autowired
    private WorkerRepository workerRepository;

    private ObjectMapper mapper;

    @PostConstruct
    public void init() {
        mapper = new ObjectMapper();
    }


    public List<Worker> getWorkersFromResponse(DockerHttpClient.Response response) throws IOException {
        ArrayList<Worker> workers = new ArrayList<>();

        List<WorkerJsonParser> workerJsonParserList = mapper.readValue(response.getBody(), mapper.getTypeFactory().constructCollectionType(List.class, WorkerJsonParser.class));
        for (WorkerJsonParser workerJsonParser : workerJsonParserList) {
            String id = workerJsonParser.getId();

            // keep only the list of public ports
            List<Integer> ports = workerJsonParser.getPorts().stream().map(port -> port.publicPort).collect(Collectors.toList());

            Optional<Worker> optionalWorker = workerRepository.findById(id);
            Worker worker;

            if (optionalWorker.isPresent()) {
                // use worker already in DB
                worker = optionalWorker.get();
            } else {
                // create a new entry
                worker = new Worker();
                worker.setId(id);
            }

            worker.setName(workerJsonParser.getNames().get(0));
            worker.setImage(workerJsonParser.getImage());
            worker.setImageId(workerJsonParser.getImageId());
            worker.setCommand(workerJsonParser.getCommand());
            worker.setStatus(workerJsonParser.getStatus());
            worker.setState(workerJsonParser.getState());
            worker.setPorts(ports);

            // convert unix timestamp in seconds to milliseconds
            Date with_ms = new Date(workerJsonParser.getCreated().getTime() * 1000);
            worker.setCreated(with_ms);

            workers.add(worker);
        }
        return workers;
    }

    public WorkerStatistics getWorkerStatisticsFromResponse(DockerHttpClient.Response response, Worker worker) throws IOException {
        WorkerStatisticsJsonParser workerStatisticsJsonParser = mapper.readValue(response.getBody(), WorkerStatisticsJsonParser.class);

        long usedMemory = 0, network_in = 0, network_out = 0, block_in = 0, block_out = 0;
        float cpu_percent = 0;
        int pid_count = 0;

        try {
            // calculate stats
            // Source: https://docs.docker.com/engine/api/v1.43/#operation/ContainerStats
            usedMemory = workerStatisticsJsonParser.memory_stats.usage;
            usedMemory -= workerStatisticsJsonParser.memory_stats.stats.cache;
            long cpu_delta = workerStatisticsJsonParser.cpu_stats.cpu_usage.total_usage - workerStatisticsJsonParser.precpu_stats.cpu_usage.total_usage;
            long system_cpu_delta = workerStatisticsJsonParser.cpu_stats.system_cpu_usage - workerStatisticsJsonParser.precpu_stats.system_cpu_usage;
            int num_cpus = workerStatisticsJsonParser.cpu_stats.online_cpus;
            cpu_percent = 100.0f * cpu_delta / system_cpu_delta * num_cpus;
            pid_count = workerStatisticsJsonParser.pids_stats.current;
            for (NetworkDevice network : workerStatisticsJsonParser.networks.values()) {
                network_in += network.rx_bytes;
                network_out += network.tx_bytes;
            }
            List<BlkioStat> blkio_stats = workerStatisticsJsonParser.blkio_stats.io_service_bytes_recursive;

            for (BlkioStat blkio_stat : blkio_stats) {
                if (blkio_stat.op.equalsIgnoreCase("Read")) {
                    block_in += blkio_stat.value;
                } else if (blkio_stat.op.equalsIgnoreCase("Write")) {
                    block_out += blkio_stat.value;
                }
            }
        } catch (NullPointerException e) {
            // do nothing if missing fields (likely missing because container not running)
        }

        WorkerStatistics workerStatistics = worker.getWorkerStatistics();

        if (workerStatistics == null) {
            workerStatistics = new WorkerStatistics();
            worker.setWorkerStatistics(workerStatistics);
            workerStatistics.setWorker(worker);
        }

        workerStatistics.setMemoryUsage(usedMemory);
        workerStatistics.setCpuUsage(cpu_percent);
        workerStatistics.setPidCount(pid_count);
        workerStatistics.setNetworkIn(network_in);
        workerStatistics.setNetworkOut(network_out);
        workerStatistics.setBlockIn(block_in);
        workerStatistics.setBlockOut(block_out);

        return workerStatistics;
    }

    // For parsing JSON
    @Getter
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class Port {
        String IP;
        int privatePort;
        int publicPort;
        String type;
    }

    @Getter
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class WorkerJsonParser {
        String id;
        List<String> names;
        String image;
        List<Port> ports;
        String state;
        String status;
        String imageId;
        String command;

        @JsonFormat(shape = JsonFormat.Shape.NUMBER)
        Date created;
    }

    @Getter
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class NetworkDevice {
        long rx_bytes;
        long tx_bytes;
    }

    @Getter
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class MemoryStats {
        long cache;
    }

    @Getter
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class Memory {
        long usage;

        MemoryStats stats;
    }

    @Getter
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class CpuUsage {
        long total_usage;
    }

    @Getter
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class Cpu {
        int online_cpus;
        CpuUsage cpu_usage;
        long system_cpu_usage;
    }

    @Getter
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class BlkioStat {
        String op;
        long value;
    }

    @Getter
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class BlkioStats {
        List<BlkioStat> io_service_bytes_recursive;
    }

    @Getter
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class PidsStats {
        int current;
    }

    @Getter
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
