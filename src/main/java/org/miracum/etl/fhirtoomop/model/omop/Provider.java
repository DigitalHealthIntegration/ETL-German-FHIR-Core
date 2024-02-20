package org.miracum.etl.fhirtoomop.model.omop;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "provider", schema = "cds_cdm")
public class Provider {

    @Id
    @Column(name = "provider_id")
    private Integer providerId;

    @Column(name = "provider_name")
    private String providerName;

    @Column(name = "npi")
    private String npi;

    @Column(name = "dea")
    private String dea;

    @Column(name = "specialty_concept_id")
    private Integer specialtyConceptId;

    @Column(name = "care_site_id")
    private Integer careSiteId;

    @Column(name = "year_of_birth")
    private Integer yearOfBirth;

    @Column(name = "gender_concept_id")
    private Integer genderConceptId;

    @Column(name = "provider_source_value")
    private String providerSourceValue;

    @Column(name = "specialty_source_value")
    private String specialtySourceValue;

    @Column(name = "specialty_source_concept_id")
    private Integer specialtySourceConceptId;

    @Column(name = "gender_source_value")
    private String genderSourceValue;

    @Column(name = "gender_source_concept_id")
    private Integer genderSourceConceptId;

    /** The logical id of the FHIR resource. */
    @Column(name = "fhir_logical_id", nullable = true)
    private String fhirLogicalId;


    // Constructors, getters, setters, etc.

    @Override
    public String toString() {
        return "Provider{" +
                "providerId=" + providerId +
                ", providerName='" + providerName + '\'' +
                ", npi='" + npi + '\'' +
                ", dea='" + dea + '\'' +
                ", specialtyConceptId=" + specialtyConceptId +
                ", careSiteId=" + careSiteId +
                ", yearOfBirth=" + yearOfBirth +
                ", genderConceptId=" + genderConceptId +
                ", providerSourceValue='" + providerSourceValue + '\'' +
                ", specialtySourceValue='" + specialtySourceValue + '\'' +
                ", specialtySourceConceptId=" + specialtySourceConceptId +
                ", genderSourceValue='" + genderSourceValue + '\'' +
                ", genderSourceConceptId=" + genderSourceConceptId +
                '}';
    }

}

