package org.miracum.etl.fhirtoomop.mapper;

import ca.uhn.fhir.fhirpath.IFhirPath;
import com.google.common.base.Strings;
import io.micrometer.core.instrument.Counter;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Organization;
import org.miracum.etl.fhirtoomop.DbMappings;
import org.miracum.etl.fhirtoomop.config.FhirSystems;
import org.miracum.etl.fhirtoomop.mapper.helpers.FindOmopConcepts;
import org.miracum.etl.fhirtoomop.mapper.helpers.MapperMetrics;
import org.miracum.etl.fhirtoomop.mapper.helpers.ResourceFhirReferenceUtils;
import org.miracum.etl.fhirtoomop.model.OmopModelWrapper;
import org.miracum.etl.fhirtoomop.model.omop.CareSite;
import org.miracum.etl.fhirtoomop.repository.OmopRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Random;

@Slf4j
@Component
public class OrganizationMapper implements FhirMapper<Organization> {

    private static final FhirSystems fhirSystems = new FhirSystems();
    private final IFhirPath fhirPath;
    private final Boolean bulkload;
    private final DbMappings dbMappings;
    private final OmopRepository repositories;
    @Autowired
    ResourceFhirReferenceUtils fhirReferenceUtils;
    @Autowired
    FindOmopConcepts findOmopConcepts;

    private static final Counter noFhirReferenceCounter =
            MapperMetrics.setNoFhirReferenceCounter("stepProcessOrganization");
    private static final Counter deletedFhirReferenceCounter =
            MapperMetrics.setDeletedFhirRessourceCounter("stepProcessOrganization");

    /**
     * Constructor for objects of the class OrganizationMapper.
     *
     * @param fhirPath     FhirPath engine to evaluate path expressions over FHIR resources
     * @param bulkload     parameter which indicates whether the Job should be run as bulk load or
     *                     incremental load
     * @param dbMappings   collections for the intermediate storage of data from OMOP CDM in RAM
     * @param repositories
     */
    @Autowired
    public OrganizationMapper(IFhirPath fhirPath, Boolean bulkload, DbMappings dbMappings, OmopRepository repositories) {

        this.fhirPath = fhirPath;
        this.bulkload = bulkload;
        this.dbMappings = dbMappings;
        this.repositories = repositories;
    }

    @Override
    public OmopModelWrapper map(Organization srcOrganization, boolean isDeleted) {
        var wrapper = new OmopModelWrapper();
        var organizationIdentifier = fhirReferenceUtils.extractIdentifier(srcOrganization,"MR");
        var organizationLogicId = fhirReferenceUtils.extractId(srcOrganization);
        if (Strings.isNullOrEmpty(organizationLogicId) && Strings.isNullOrEmpty(organizationIdentifier)) {
            log.warn("No [Identifier] or [Id] found. [Organization] resource is invalid. Skip resource");
            noFhirReferenceCounter.increment();
            return null;
        }

        String organizationId = "";
        if(!Strings.isNullOrEmpty(organizationLogicId)){
            organizationId = srcOrganization.getId();
        }

        if (bulkload.equals(Boolean.FALSE)){
            repositories.getCareSiteRepository().deleteCareSiteByLogicalId(organizationLogicId);
            if (isDeleted){
                return null;
            }
        }
        var organizationName = srcOrganization.getName();
        var sourceValue  = srcOrganization.getIdElement().getIdPart();
        var organizationTag = srcOrganization.getMeta().getTag().stream().findFirst();
        if (organizationTag.isEmpty()) {
            return null;
        }
        var organizationMetaCoding =organizationTag.get();
        var concept = findOmopConcepts.getConcepts(organizationMetaCoding, null,bulkload,dbMappings,organizationId);
        if(concept == null){
            log.warn("concept is not present for Organization {}",organizationId);
            noFhirReferenceCounter.increment();
            return null;
        }
        var placeOfService = concept.getConceptId();
        Random random = new Random();
        int generatedPositiveLong = Math.abs(random.nextInt());

        var newCareSite = CareSite.builder()
                        .careSiteId((long) generatedPositiveLong)
                .careSiteName(organizationName)
                .careSiteSourceValue(sourceValue)
                .placeOfServiceConceptId(placeOfService)
                .fhirLogicalId(organizationLogicId)
                .fhirIdentifier(organizationIdentifier)
                .build();
        if (!dbMappings.getFindCareSiteId().containsKey(sourceValue)){
            wrapper.setCareSite(newCareSite);
        }
        return wrapper;
    }
}
