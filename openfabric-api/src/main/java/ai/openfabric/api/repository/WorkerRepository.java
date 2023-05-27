package ai.openfabric.api.repository;

import ai.openfabric.api.model.Worker;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;

public interface WorkerRepository extends PagingAndSortingRepository<Worker, String> {

    @Query(value = "SELECT w.id as id, w.name as name, w.state as state, w.status as status FROM Worker w", countQuery = "SELECT count(w.id) FROM Worker w", nativeQuery = true)
    Page<BasicWorkerInfo> findAllMinimal(Pageable pageable);

    interface BasicWorkerInfo {
        String getId();

        String getName();

        String getState();

        String getStatus();
    }

}
