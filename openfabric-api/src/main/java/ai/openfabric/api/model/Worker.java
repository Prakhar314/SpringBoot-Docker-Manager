package ai.openfabric.api.model;


import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Entity()
public class Worker extends Datable implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY, generator = "of-uuid")
    @GenericGenerator(name = "of-uuid", strategy = "ai.openfabric.api.model.IDGenerator")
    @Getter
    @Setter
    private String id;

    @Getter
    @Setter
    private String name;

    private String ports;
    public List<Integer> getPorts() {
        return Arrays.stream(ports.split(",")).map(Integer::parseInt).collect(Collectors.toList());
    }
    public void setPorts(List<Integer> ports) {
        this.ports = ports.stream().map(Object::toString).collect(Collectors.joining(","));
    }

    @Getter
    @Setter
    private String status;

    @Getter
    @Setter
    private boolean isRunning;

    @Getter
    @Setter
    private String image;

    @OneToOne(mappedBy = "worker", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @PrimaryKeyJoinColumn
    @JoinColumn(name = "workerStatistics")
    private WorkerStatistics workerStatistics;
}
