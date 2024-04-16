package org.miracum.etl.fhirtoomop.model.omop;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Id;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Column;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "Appointment", schema = "cds_cdm")
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "appointment_id")
    private Long appointmentId;

    @Column(name = "care_site_id", nullable = true)
    private Long careSiteId;

    @Column(name = "person_id", nullable = true)
    private Long personId;

    @Column(name = "visit_occurrence_id", nullable = true)
    private Long visitOccurrenceId;

    @Column(name = "scheduled_datetime")
    private LocalDateTime scheduledDateTime;

    @Column(name = "created_datetime")
    private LocalDateTime createdDateTime;

    @Column(name = "appointment_status")
    private String appointmentStatus;

    @Column(name = "fhir_logical_id", nullable = true)
    private String fhirLogicalId;
}
