package org.miracum.etl.fhirtoomop.repository.service;

import org.miracum.etl.fhirtoomop.repository.CareSiteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The OrganizationServiceImpl class contains the specific implementation to access
 * data through OMOP repositories
 */

@Transactional("transactionManager")
@Service("OrganizationServiceImpl")
public class OrganizationServiceImpl {

    @Autowired CareSiteRepository careSiteRepository;

    public void deleteExistingCareSiteByFhirLogicalId(String fhirLogicalId){
        careSiteRepository.deleteByFhirLogicalId(fhirLogicalId);
    }
}
