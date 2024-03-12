package org.miracum.etl.fhirtoomop.repository;

import org.miracum.etl.fhirtoomop.model.omop.Provider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code ProviderRepository} is a Spring Data repository interface for CRUD operations on Provider entities.
 * It extends {@link org.springframework.data.jpa.repository.JpaRepository JpaRepository} and
 * {@link org.springframework.data.repository.PagingAndSortingRepository PagingAndSortingRepository}.
 */
@Repository
public interface ProviderRepository extends PagingAndSortingRepository<Provider, Long>, JpaRepository<Provider, Long> {

        @Transactional
        @Modifying
        @Query(value = "DELETE FROM provider WHERE fhir_logical_id = :fhir_logical_id", nativeQuery = true)
        void deleteProviderByLogicId(@Param("fhir_logical_id") String fhirLogicalId);
}