package org.miracum.etl.fhirtoomop.processor;

import ca.uhn.fhir.parser.IParser;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Provenance;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.miracum.etl.fhirtoomop.mapper.FhirMapper;
import org.miracum.etl.fhirtoomop.model.FhirPsqlResource;
import org.miracum.etl.fhirtoomop.model.OmopModelWrapper;

import static org.miracum.etl.fhirtoomop.Constants.PROCESSING_RESOURCES_LOG;

@Slf4j
public class ProvenanceProcessor extends ResourceProcessor<Provenance> {


    /**
     * Constructor for objects of the class ResourceProcessor.
     *
     * @param mapper     mapper which maps FHIR resources to OMOP CDM
     * @param fhirParser parser which converts between the HAPI FHIR model/structure objects and their
     *                   respective String wire format (JSON)
     */
    public ProvenanceProcessor(FhirMapper<Provenance> mapper, IParser fhirParser) {
        super(mapper, fhirParser);
    }

    @Override
    public OmopModelWrapper process(FhirPsqlResource fhirPsqlResource) throws Exception {
        var r = fhirParser.parseResource(Provenance.class,fhirPsqlResource.getData());
        log.debug(PROCESSING_RESOURCES_LOG, r.getResourceType(),r.getId());
        return mapper.map(
                r,
                fhirPsqlResource.getIsDeleted() == null? Boolean.FALSE: fhirPsqlResource.getIsDeleted()
        );
    }
}
