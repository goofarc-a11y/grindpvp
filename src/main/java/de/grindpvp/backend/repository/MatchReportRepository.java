package de.grindpvp.backend.repository;

import de.grindpvp.backend.model.MatchReport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchReportRepository extends JpaRepository<MatchReport, Long> {
}
