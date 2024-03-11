package org.miracum.etl.fhirtoomop.model.omop;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDate;


@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "concept_relationship")
public class ConceptRelationship {
    @Id
    @Column(name = "concept_id_1")
    private int conceptId1;

    @Column(name = "concept_id_2")
    private int conceptId2;

    @Column(name = "relationship_id")
    private String relationshipId;

    @Column(name = "valid_start_date")
    private LocalDate validStartDate;

    @Column(name = "valid_end_date")
    private LocalDate validEndDate;

    @Column(name = "invalid_reason")
    private String invalidReason;



}
