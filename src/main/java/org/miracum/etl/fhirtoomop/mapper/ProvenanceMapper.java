package org.miracum.etl.fhirtoomop.mapper;

import ca.uhn.fhir.fhirpath.IFhirPath;
import com.google.common.base.Strings;
import io.micrometer.core.instrument.Counter;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.Provenance;
import org.miracum.etl.fhirtoomop.DbMappings;
import org.miracum.etl.fhirtoomop.config.FhirSystems;
import org.miracum.etl.fhirtoomop.mapper.helpers.MapperMetrics;
import org.miracum.etl.fhirtoomop.mapper.helpers.ResourceFhirReferenceUtils;
import org.miracum.etl.fhirtoomop.model.OmopModelWrapper;
import org.miracum.etl.fhirtoomop.model.PostProcessMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.hl7.fhir.r4.model.Reference;

@Slf4j
@Component
public class ProvenanceMapper implements FhirMapper<Provenance>{

    private static final FhirSystems fhirSystems = new FhirSystems();

    private final IFhirPath fhirPath;
    private final Boolean bulkload;
    private final DbMappings dbMappings;

    @Autowired
    ResourceFhirReferenceUtils fhirReferenceUtils;

    private static final Counter noFhirReferenceCounter =
            MapperMetrics.setNoFhirReferenceCounter("stepProcessProvenance");
    private static final Counter noTargetFoundCounter =
            MapperMetrics.setNoTargetReferenceFoundCounter("stepProcessProvenance");

    @Autowired
    public ProvenanceMapper(IFhirPath fhirPath, Boolean bulkload, DbMappings dbMappings){
        this.fhirPath = fhirPath;
        this.bulkload = bulkload;
        this.dbMappings = dbMappings;
    }

    @Override
    public OmopModelWrapper map(Provenance srcProvenance, boolean isDeleted) {
        var wrapper = new OmopModelWrapper();
        var provenanceLogicalId = fhirReferenceUtils.extractId(srcProvenance);
        var questionnaireResponseLogicalId = fhirReferenceUtils.extractId(srcProvenance.getEntityFirstRep().getWhat().getReferenceElement().getResourceType(),srcProvenance.getEntityFirstRep().getWhat().getReferenceElement().getIdPart());
        if (Strings.isNullOrEmpty(provenanceLogicalId)){
            log.warn("No [Identifier] or [Id] found. [Provenance] resource is invalid. Skip resource");
            noFhirReferenceCounter.increment();
            return null;
        }
        if (!srcProvenance.hasTarget()){
            log.warn("No target found for the [Provenance] resource with id {}", provenanceLogicalId);
            noTargetFoundCounter.increment();
            return null;
        }
        var provenanceTargets = srcProvenance.getTarget();

        for (Reference provenanceTarget : provenanceTargets) {
            var resourceType = provenanceTarget.getReferenceElement().getResourceType();
            var resourceId = provenanceTarget.getReferenceElement().getIdPart();
            var resourceLogicalId = fhirReferenceUtils.extractId(resourceType, resourceId);
            var combinedId = resourceLogicalId.concat(";").concat(questionnaireResponseLogicalId);
            var addProvenanceToPostProcess = postProcessMapForProvenance(provenanceLogicalId, combinedId, resourceType, Enumerations.ResourceType.PROVENANCE.name());
            wrapper.getPostProcessMap().add(addProvenanceToPostProcess);
        }
        return wrapper;
    }

    private PostProcessMap postProcessMapForProvenance(
            String provenanceResourceId,
            String resourceReferenceId,
            String resourceType,
            String parentResourceType
    ){
        return PostProcessMap.builder()
                .dataOne(resourceType)
                .dataTwo(resourceReferenceId)
                .type(parentResourceType)
                .fhirLogicalId(provenanceResourceId)
                .build();
    }
}
