package ai.generalcontracts.startup.transfer;

import java.util.List;
import org.springframework.data.repository.Repository;

/** Append-only audit store: exposes insert-style save and reads, never update or delete. */
public interface TransferAuditRepository extends Repository<TransferAuditRecord, String> {
    <S extends TransferAuditRecord> S save(S record);

    List<TransferAuditRecord> findByInstructionIdOrderByOccurredAtAsc(String instructionId);
}