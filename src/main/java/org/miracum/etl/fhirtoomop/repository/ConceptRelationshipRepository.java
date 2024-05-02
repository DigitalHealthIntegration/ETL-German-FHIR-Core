package org.miracum.etl.fhirtoomop.repository;

import org.jetbrains.annotations.NotNull;
import org.miracum.etl.fhirtoomop.model.omop.ConceptRelationship;
import org.springframework.data.repository.PagingAndSortingRepository;

import javax.persistence.criteria.CriteriaBuilder;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public interface ConceptRelationshipRepository extends PagingAndSortingRepository<ConceptRelationship, Long> {
   ConceptRelationship findConceptRelationshipByConceptId1(Integer conceptId1);

    @NotNull
    List<ConceptRelationship> findAll();

    default ConceptRelationship findValidConceptRelationship(Integer conceptId1){
//        return findAll().stream().collect(Collectors.groupingBy(ConceptRelationship::getConceptId1));
        return findConceptRelationshipByConceptId1(conceptId1);
    }
}
