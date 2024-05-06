package org.miracum.etl.fhirtoomop.mapper;

import ca.uhn.fhir.fhirpath.IFhirPath;
import com.google.common.base.Strings;
import io.micrometer.core.instrument.Counter;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.hl7.fhir.r4.model.ResourceType;
import org.miracum.etl.fhirtoomop.DbMappings;
import org.miracum.etl.fhirtoomop.config.FhirSystems;
import org.miracum.etl.fhirtoomop.mapper.helpers.MapperMetrics;
import org.miracum.etl.fhirtoomop.mapper.helpers.ResourceFhirReferenceUtils;
import org.miracum.etl.fhirtoomop.mapper.helpers.ResourceOmopReferenceUtils;
import org.miracum.etl.fhirtoomop.model.OmopModelWrapper;
import org.miracum.etl.fhirtoomop.model.PostProcessMap;
import org.miracum.etl.fhirtoomop.processor.QuestionnaireResponseProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.persistence.Index;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class QuestionnaireResponseMapper implements FhirMapper<QuestionnaireResponse>{

    private static final FhirSystems fhirSystems = new FhirSystems();

    private final IFhirPath fhirPath;
    private final Boolean bulkload;
    private final DbMappings dbMappings;

    @Autowired
    ResourceFhirReferenceUtils fhirReferenceUtils;

    @Autowired
    ResourceOmopReferenceUtils omopReferenceUtils;

    private static final Counter noFhirReferenceCounter = MapperMetrics.setNoFhirReferenceCounter("stepProcessQuestionnaireResponse");

    @Autowired
    public QuestionnaireResponseMapper(IFhirPath fhirPath, Boolean bulkload, DbMappings dbMappings){
        this.fhirPath = fhirPath;
        this.bulkload = bulkload;
        this.dbMappings = dbMappings;
    }

    @Override
    public OmopModelWrapper map(QuestionnaireResponse srcQuestionnaireResponse, boolean isDeleted) {
        var wrapper = new OmopModelWrapper();

        var questionnaireResponseLogicalId = fhirReferenceUtils.extractId(srcQuestionnaireResponse);
        if (Strings.isNullOrEmpty(questionnaireResponseLogicalId)) {
            log.warn("No [Identifier] or [Id] found. [QuestionnaireResponse] resource is invalid. Skip resource");
            noFhirReferenceCounter.increment();
            return null;
        }
        var appReasonList = srcQuestionnaireResponse.getItem().stream().filter(questionnaireResponseItemComponent -> questionnaireResponseItemComponent.getLinkId().equals("service-type")).toList();
        var encounterId = srcQuestionnaireResponse.getEncounter().getReferenceElement().getIdPart();
        var encounterLogicalId = fhirReferenceUtils.extractId(ResourceType.Encounter.name(),encounterId);
        var tagOfQuestionnaireResponse = srcQuestionnaireResponse.getMeta().getTag().stream().findFirst();
        if(tagOfQuestionnaireResponse.isEmpty()){
            return null;
        }
        var questionnaireResponseTagCode = tagOfQuestionnaireResponse.get().getCode();
        var mappedQuestionnaire = "";
        try{
            mappedQuestionnaire  = srcQuestionnaireResponse.getQuestionnaire();
            if(mappedQuestionnaire == null){
                mappedQuestionnaire = "";
            } else {
                mappedQuestionnaire = mappedQuestionnaire.split("/")[1];
            }
        }catch(IndexOutOfBoundsException e){
            log.warn(e.getLocalizedMessage());
            mappedQuestionnaire = "";
        }
        var combinedTagCodeAndQuestionnaire = questionnaireResponseTagCode.concat(",").concat(mappedQuestionnaire);
        var addQuestionnaireResponseToPostProcess = postProcessMapForQuestionnaireResponse(
                questionnaireResponseLogicalId,combinedTagCodeAndQuestionnaire,encounterLogicalId);
        var addAppointmentReasonToPostProcess = postProcessMapForAppointment(questionnaireResponseLogicalId, appReasonList);
        wrapper.getPostProcessMap().add(addQuestionnaireResponseToPostProcess);
        wrapper.getPostProcessMap().add(addAppointmentReasonToPostProcess);
        return wrapper;
    }

    private PostProcessMap postProcessMapForQuestionnaireResponse(
            String questionnaireResponseLogicalId,
            String combinedTagCodeAndQuestionnaire,
            String encounterLogicalId) {
        return PostProcessMap.builder()
                .dataOne(combinedTagCodeAndQuestionnaire)
                .dataTwo(encounterLogicalId)
                .type(Enumerations.ResourceType.QUESTIONNAIRERESPONSE.name())
                .fhirLogicalId(questionnaireResponseLogicalId)
                .build();
    }

    private PostProcessMap postProcessMapForAppointment(
            String questionnaireResponseLogicalId,
            List<QuestionnaireResponse.QuestionnaireResponseItemComponent> appReasonList
    ) {
        if (appReasonList.isEmpty())
            return null;
        var appReason = ((Coding) appReasonList.get(0).getAnswerFirstRep().getValue()).getDisplay();
        return PostProcessMap.builder()
                .dataOne("Appointment Reason")
                .dataTwo(appReason)
                .type(Enumerations.ResourceType.APPOINTMENT.name())
                .fhirLogicalId(questionnaireResponseLogicalId)
                .build();
    }
}
