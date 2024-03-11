package org.miracum.etl.fhirtoomop.mapper.helpers;

import lombok.extern.slf4j.Slf4j;
import org.miracum.etl.fhirtoomop.model.omop.ConceptRelationship;
import org.miracum.etl.fhirtoomop.repository.ConceptRelationshipRepository;
import org.miracum.etl.fhirtoomop.repository.service.OmopConceptRelationshipServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FindOmopConceptRelationship {
    @Autowired
    OmopConceptRelationshipServiceImpl omopConceptRelationshipService;

    public ConceptRelationship getConceptRelationShip(Integer conceptId){
        return omopConceptRelationshipService
                .findValidConceptRelationshipFromConceptId(conceptId);
    }
}
