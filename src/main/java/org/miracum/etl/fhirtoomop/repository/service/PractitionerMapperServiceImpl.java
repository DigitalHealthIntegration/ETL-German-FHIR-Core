package org.miracum.etl.fhirtoomop.repository.service;

import org.miracum.etl.fhirtoomop.repository.ProviderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The PractitionerMapperServiceImpl class contains the specific implementation to access
 * data through OMOP repositories
 */

@Transactional("transactionManager")
@Service("PractitionerMapperServiceImpl")
public class PractitionerMapperServiceImpl {

    @Autowired ProviderRepository providerRepository;

    public void deleteExistingProviderByFhirLogicalId(String fhirLogicalId){
        providerRepository.deleteProviderByLogicId(fhirLogicalId);
    }
}
