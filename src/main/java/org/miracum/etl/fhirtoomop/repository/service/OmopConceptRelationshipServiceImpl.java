package org.miracum.etl.fhirtoomop.repository.service;

import org.miracum.etl.fhirtoomop.model.omop.ConceptRelationship;
import org.miracum.etl.fhirtoomop.repository.ConceptRelationshipRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Transactional(transactionManager = "transactionManager")
@Service("OmopConceptRelationshipServiceImpl")
@CacheConfig(cacheManager = "caffeineCacheManager")
public class OmopConceptRelationshipServiceImpl {
    @Autowired
    CaffeineCacheManager cacheManager;

    @Autowired private ConceptRelationshipRepository conceptRelationshipRepository;

    @Cacheable(cacheNames = "valid-conceptRelations", sync = true)
    public ConceptRelationship findValidConceptRelationshipFromConceptId(Integer conceptId1){
        return conceptRelationshipRepository.findValidConceptRelationship(conceptId1);
    }
}
