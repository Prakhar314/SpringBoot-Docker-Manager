package ai.openfabric.api.model;


import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Setter
@Entity()
@NoArgsConstructor
@AllArgsConstructor
public class Worker extends Datable implements Serializable {

    @Id
    @Column(columnDefinition = "character varying(255)")
    private String id;

    private String name;
    private String ports;
    private String status;
    private String state;
    private String image;
    private String imageId;
    private String command;
    private Date created;


    @OneToOne(mappedBy = "worker", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @PrimaryKeyJoinColumn
    @JoinColumn(name = "workerStatistics")
    @JsonIgnore
    private WorkerStatistics workerStatistics;

    public List<Integer> getPorts() {
        if (ports.length() == 0) {
            return Collections.emptyList();
        }
        // from comma separated list of ports to list of integers
        return Arrays.stream(ports.split(",")).map(Integer::parseInt).collect(Collectors.toList());
    }

    public void setPorts(List<Integer> ports) {
        // to comma separated list of ports
        this.ports = ports.stream().map(Object::toString).collect(Collectors.joining(","));
    }
}
