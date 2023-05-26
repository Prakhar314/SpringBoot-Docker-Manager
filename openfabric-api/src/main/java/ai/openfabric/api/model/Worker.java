package ai.openfabric.api.model;


import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Setter
@Builder
@Entity()
@NoArgsConstructor
@AllArgsConstructor
public class Worker extends Datable implements Serializable {

    @Id
    private String id;

    private String name;

    private String ports;

    public static class WorkerBuilder {
        public WorkerBuilder ports(List<Integer> ports) {
            this.ports = ports.stream().map(Object::toString).collect(Collectors.joining(","));
            return this;
        }
    }

    public List<Integer> getPorts() {
        if (ports.length() == 0){
            return Collections.emptyList();
        }
        return Arrays.stream(ports.split(",")).map(Integer::parseInt).collect(Collectors.toList());
    }
    public void setPorts(List<Integer> ports) {
        this.ports = ports.stream().map(Object::toString).collect(Collectors.joining(","));
    }

    private String status;

    private boolean isRunning;
    private String image;

    @OneToOne(mappedBy = "worker", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @PrimaryKeyJoinColumn
    @JoinColumn(name = "workerStatistics")
    @JsonIgnore
    private WorkerStatistics workerStatistics;
}
