package org.miracum.etl.fhirtoomop.mapper;

import static org.miracum.etl.fhirtoomop.Constants.CONCEPT_EHR;
import static org.miracum.etl.fhirtoomop.Constants.CONCEPT_NO_MATCHING_CONCEPT;
import static org.miracum.etl.fhirtoomop.Constants.FHIR_RESOURCE_GECCO_OBSERVATION_ACCEPTABLE_VALUE_CODE;
import static org.miracum.etl.fhirtoomop.Constants.FHIR_RESOURCE_GECCO_OBSERVATION_BLOOD_PRESSURE_CODES;
import static org.miracum.etl.fhirtoomop.Constants.FHIR_RESOURCE_GECCO_OBSERVATION_ECRF_PARAMETER_DOMAIN_OBSERVATION;
import static org.miracum.etl.fhirtoomop.Constants.FHIR_RESOURCE_GECCO_OBSERVATION_IN_MEASUREMENT_DOMAIN_CODES;
import static org.miracum.etl.fhirtoomop.Constants.FHIR_RESOURCE_GECCO_OBSERVATION_SOFA_CODES;
import static org.miracum.etl.fhirtoomop.Constants.FHIR_RESOURCE_OBSERVATION_ACCEPTABLE_STATUS_LIST;
import static org.miracum.etl.fhirtoomop.Constants.FHIR_RESOURCE_OBSERVATION_HISTORY_OF_TRAVEL_CODES;
import static org.miracum.etl.fhirtoomop.Constants.MAX_SOURCE_VALUE_LENGTH;
import static org.miracum.etl.fhirtoomop.Constants.OMOP_DOMAIN_CONDITION;
import static org.miracum.etl.fhirtoomop.Constants.OMOP_DOMAIN_GENDER;
import static org.miracum.etl.fhirtoomop.Constants.OMOP_DOMAIN_MEASUREMENT;
import static org.miracum.etl.fhirtoomop.Constants.OMOP_DOMAIN_OBSERVATION;
import static org.miracum.etl.fhirtoomop.Constants.OMOP_DOMAIN_PROCEDURE;
import static org.miracum.etl.fhirtoomop.Constants.SOURCE_VOCABULARY_FRAILTY_SCORE;
import static org.miracum.etl.fhirtoomop.Constants.SOURCE_VOCABULARY_ID_ECRF_PARAMETER;
import static org.miracum.etl.fhirtoomop.Constants.SOURCE_VOCABULARY_ID_GENDER;
import static org.miracum.etl.fhirtoomop.Constants.SOURCE_VOCABULARY_ID_LAB_INTERPRETATION;
import static org.miracum.etl.fhirtoomop.Constants.SOURCE_VOCABULARY_ID_LAB_RESULT;
import static org.miracum.etl.fhirtoomop.Constants.SOURCE_VOCABULARY_ID_OBSERVATION_CATEGORY;
import static org.miracum.etl.fhirtoomop.Constants.SOURCE_VOCABULARY_SOFA_CATEGORY;
import static org.miracum.etl.fhirtoomop.Constants.VOCABULARY_ICD10GM;
import static org.miracum.etl.fhirtoomop.Constants.VOCABULARY_IPRD;
import static org.miracum.etl.fhirtoomop.Constants.VOCABULARY_LOINC;
import static org.miracum.etl.fhirtoomop.Constants.VOCABULARY_WHO;

import com.google.common.base.Strings;
import io.micrometer.core.instrument.Counter;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Observation.ObservationComponentComponent;
import org.hl7.fhir.r4.model.Observation.ObservationReferenceRangeComponent;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Type;
import org.miracum.etl.fhirtoomop.DbMappings;
import org.miracum.etl.fhirtoomop.config.FhirSystems;
import org.miracum.etl.fhirtoomop.mapper.helpers.FindOmopConcepts;
import org.miracum.etl.fhirtoomop.mapper.helpers.MapperMetrics;
import org.miracum.etl.fhirtoomop.mapper.helpers.ResourceCheckDataAbsentReason;
import org.miracum.etl.fhirtoomop.mapper.helpers.ResourceFhirReferenceUtils;
import org.miracum.etl.fhirtoomop.mapper.helpers.ResourceOmopReferenceUtils;
import org.miracum.etl.fhirtoomop.mapper.helpers.ResourceOnset;
import org.miracum.etl.fhirtoomop.model.IcdSnomedDomainLookup;
import org.miracum.etl.fhirtoomop.model.StandardDomainLookup;
import org.miracum.etl.fhirtoomop.model.OmopModelWrapper;
import org.miracum.etl.fhirtoomop.model.PostProcessMap;
import org.miracum.etl.fhirtoomop.model.omop.Concept;
import org.miracum.etl.fhirtoomop.model.omop.ConditionOccurrence;
import org.miracum.etl.fhirtoomop.model.omop.Measurement;
import org.miracum.etl.fhirtoomop.model.omop.OmopObservation;
import org.miracum.etl.fhirtoomop.model.omop.ProcedureOccurrence;
import org.miracum.etl.fhirtoomop.model.omop.SourceToConceptMap;
import org.miracum.etl.fhirtoomop.repository.service.EncounterDepartmentCaseMapperServiceImpl;
import org.miracum.etl.fhirtoomop.repository.service.ObservationMapperServiceImpl;
import org.miracum.etl.fhirtoomop.repository.service.OmopConceptServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.hl7.fhir.r4.model.ResourceType;


/**
 * The ObservationMapper class describes the business logic of transforming a FHIR Observation
 * resource to OMOP CDM.
 *
 * @author Elisa Henke
 * @author Yuan Peng
 */
@Slf4j
@Component
public class ObservationMapper implements FhirMapper<Observation> {

  private static final FhirSystems fhirSystems = new FhirSystems();
  private final ResourceFhirReferenceUtils referenceUtils;
  private final Boolean bulkload;
  private final DbMappings dbMappings;

  @Autowired OmopConceptServiceImpl omopConceptService;
  @Autowired ResourceOmopReferenceUtils omopReferenceUtils;
  @Autowired ObservationMapperServiceImpl observationService;
  @Autowired ResourceFhirReferenceUtils fhirReferenceUtils;
  @Autowired ResourceCheckDataAbsentReason checkDataAbsentReason;
  @Autowired FindOmopConcepts findOmopConcepts;
  @Autowired
  EncounterDepartmentCaseMapperServiceImpl departmentCaseMapperService;

  private static final Counter noStartDateCounter =
      MapperMetrics.setNoStartDateCounter("stepProcessObservations");
  private static final Counter noPersonIdCounter =
      MapperMetrics.setNoPersonIdCounter("stepProcessObservations");
  private static final Counter invalidCodeCounter =
      MapperMetrics.setInvalidCodeCounter("stepProcessObservations");
  private static final Counter noCodeCounter =
      MapperMetrics.setNoCodeCounter("stepProcessObservations");
  private static final Counter noFhirReferenceCounter =
      MapperMetrics.setNoFhirReferenceCounter("stepProcessObservations");
  private static final Counter deletedFhirReferenceCounter =
      MapperMetrics.setDeletedFhirRessourceCounter("stepProcessObservations");
  private static final Counter statusErrorCounter =
          MapperMetrics.setStatusErrorCounter("stepProcessObservations");
  private static final Counter noMatchingEncounterCounter =
          MapperMetrics.setNoMatchingEncounterCount("stepProcessObservations");
  private static final Counter noResultHistoryOfTravelCounter =
          MapperMetrics.setNoResultHistoryTravelFoundCount("stepProcessObservations");
  private static final Counter noAcceptableResultHistoryOfTravelCounter =
          MapperMetrics.setNoAcceptableResultHistoryOfTravelFoundCount("stepProcessObservations");
  private static final Counter noAvailableInfoHistoryOfTravelCounter =
          MapperMetrics.setAvailableInfoHistoryOfTravelNotFoundCount("stepProcessObservations");
  private static final Counter noValueCounter =
          MapperMetrics.setValueNotFoundCount("stepProcessObservations");
  private static final Counter noInterpretationCounter =
          MapperMetrics.setNoInterpretationFoundCount("stepProcessObservations");
  private static final Counter noReferenceRangeCounter =
          MapperMetrics.setNoReferenceRangeFoundCount("stepProcessObservations");
  private static final Counter missingHighRangeCounter =
          MapperMetrics.setMissingHighRangeCount("stepProcessObservations");
  private static final Counter missingLowRangeCounter =
          MapperMetrics.setMissingLowRangeCount("stepProcessObservations");
  private static final Counter noCategoryCount =
          MapperMetrics.setCategoryNotFoundCount("stepProcessObservations");

  /**
   * Constructor for objects of the class ObservationMapper.
   *
   * @param referenceUtils utilities for the identification of FHIR resource references
   * @param bulkload parameter which indicates whether the Job should be run as bulk load or
   *     incremental load
   * @param dbMappings collections for the intermediate storage of data from OMOP CDM in RAM
   */
  @Autowired
  public ObservationMapper(
      ResourceFhirReferenceUtils referenceUtils, Boolean bulkload, DbMappings dbMappings) {

    this.referenceUtils = referenceUtils;
    this.bulkload = bulkload;
    this.dbMappings = dbMappings;
  }

  /**
   * Maps a FHIR Observation resource to several OMOP CDM tables.
   *
   * @param srcObservation FHIR Observation resource
   * @param isDeleted a flag, whether the FHIR resource is deleted in the source
   * @return OmopModelWrapper cache of newly created OMOP CDM records from the FHIR Observation
   *     resource
   */
  @Override
  public OmopModelWrapper map(Observation srcObservation, boolean isDeleted) {
    var wrapper = new OmopModelWrapper();

    var observationLogicId = fhirReferenceUtils.extractId(srcObservation);
//    var result = Objects.equals(observationLogicId, "obs-4283158f-7e61-436e-bb3d-67558e677cb5");
//    if(!result){
//      return null;
//    }
    var observationSourceIdentifier =
        fhirReferenceUtils.extractResourceFirstIdentifier(srcObservation);
    if (StringUtils.isBlank(observationLogicId)
        && StringUtils.isBlank(observationSourceIdentifier)) {
      log.warn("No [Identifier] or [Id] found. [Observation] resource is invalid. Skip resource");
      noFhirReferenceCounter.increment();
      return null;
    }

    String observationId = "";
    if (!Strings.isNullOrEmpty(observationLogicId)) {
      observationId = srcObservation.getId();
    }

    if (bulkload.equals(Boolean.FALSE)) {
      deleteExistingLabObservations(observationLogicId, observationSourceIdentifier);
      if (isDeleted) {
        deletedFhirReferenceCounter.increment();
        log.info(
            "Found a deleted [Observation] resource {}. Deleting from OMOP DB.", observationId);
        return null;
      }
    }

    var statusElement = srcObservation.getStatusElement();
    var statusValue = checkDataAbsentReason.getValue(statusElement);
    if (Strings.isNullOrEmpty(statusValue)
        || !FHIR_RESOURCE_OBSERVATION_ACCEPTABLE_STATUS_LIST.contains(statusValue)) {
      statusValue  = "final";
      log.error(
          "The [status]: {} of {} is not acceptable for writing into OMOP CDM. Skip resource.",
          statusValue,
          observationId);
//      return null;
    }

    var personId = getPersonId(srcObservation, observationLogicId, observationId);
    if (personId == null) {
      log.warn("No matching [Person] found for [Observation]: {}. Skip resource", observationId);
      noPersonIdCounter.increment();
      return null;
    }

    var visitOccId = getVisitOccId(srcObservation, personId, observationId);

    var observationCoding = getObservationCoding(srcObservation);
    if (observationCoding == null) {
      log.warn("No [Code] found in [Observation]: {}. Skip resource", observationId);
      noCodeCounter.increment();
      return null;
    }

    var effectiveDateTime = getObservationOnset(srcObservation);
    if (effectiveDateTime.getStartDateTime() == null
        && !FHIR_RESOURCE_OBSERVATION_HISTORY_OF_TRAVEL_CODES.contains(
            observationCoding.getCode())) {
      log.warn("No [EffectiveDateTime] found for [Observation]: {}. Skip resource", observationId);
      noStartDateCounter.increment();
      return null;
    }

    setLabData(
        wrapper,
        observationCoding,
        srcObservation,
        personId,
        visitOccId,
        effectiveDateTime.getStartDateTime(),
        observationLogicId,
        observationSourceIdentifier,
        observationId);

    return wrapper;
  }

  /**
   * Returns the person_id of the referenced FHIR Patient resource for the processed FHIR
   * Observation resource.
   *
   * @param srcObservation FHIR Observation resource
   * @param observationLogicId logical id of the FHIR Observation resource
   * @return person_id of the referenced FHIR Patient resource from person table in OMOP CDM
   */
  private Long getPersonId(
      Observation srcObservation, String observationLogicId, String observationId) {
    var patientReferenceIdentifier = referenceUtils.getSubjectReferenceIdentifier(srcObservation);
    var patientReferenceLogicalId = referenceUtils.getSubjectReferenceLogicalId(srcObservation);

    return omopReferenceUtils.getPersonId(
        patientReferenceIdentifier, patientReferenceLogicalId, observationLogicId, observationId);
  }

  /**
   * Returns the visit_occurrence_id of the referenced FHIR Encounter resource for the processed
   * FHIR Observation resource.
   *
   * @param srcObservation FHIR Observation resource
   * @param personId person_id of the referenced FHIR Patient resource
   * @param observationLogicId logical id of the FHIR Observation resource
   * @return visit_occurrence_id of the referenced FHIR Encounter resource from visit_occurrence
   *     table in OMOP CDM
   */
  private Long getVisitOccId(Observation srcObservation, Long personId, String observationId) {

    var encounterReferenceIdentifier =
        referenceUtils.getEncounterReferenceIdentifier(srcObservation);
    var encounterReferenceLogicalId = referenceUtils.getEncounterReferenceLogicalId(srcObservation);
    var visitOccId =
        omopReferenceUtils.getVisitOccId(
            encounterReferenceIdentifier, encounterReferenceLogicalId, personId, observationId);
    if (visitOccId == null) {
      log.debug("No matching [Encounter] found for [Observation]: {}.", observationId);
      noMatchingEncounterCounter.increment();
    }

    return visitOccId;
  }

  /**
   * Extracts date time information from the FHIR Observation resource.
   *
   * @param srcObservation FHIR Observation resource
   * @return date time of the FHIR Observation resource
   */
  private ResourceOnset getObservationOnset(Observation srcObservation) {
    var resourceOnset = new ResourceOnset();
    if (srcObservation.hasEffective()) {
      var effective = checkDataAbsentReason.getValue(srcObservation.getEffective());
      if (effective == null) {
        return resourceOnset;
      }
    }

    if (srcObservation.hasEffectiveDateTimeType()) {
      var effectiveDateTime =
          checkDataAbsentReason.getValue(srcObservation.getEffectiveDateTimeType());
      if (effectiveDateTime != null) {
        resourceOnset.setStartDateTime(effectiveDateTime);
      }
      return resourceOnset;
    }

    if (srcObservation.hasIssued() && srcObservation.getIssued() != null) {
      resourceOnset.setStartDateTime(
          new Timestamp(srcObservation.getIssued().getTime()).toLocalDateTime());
      return resourceOnset;
    }

    if (srcObservation.hasEffectivePeriod()) {
      var effectivePeriod = srcObservation.getEffectivePeriod();
      if (effectivePeriod.getStart() != null) {
        resourceOnset.setStartDateTime(
            new Timestamp(srcObservation.getEffectivePeriod().getStart().getTime())
                .toLocalDateTime());
      }
      if (effectivePeriod.getEnd() != null) {
        resourceOnset.setEndDateTime(
            new Timestamp(srcObservation.getEffectivePeriod().getEnd().getTime())
                .toLocalDateTime());
      }
      return resourceOnset;
    }
    var fhirLogicalId = fhirReferenceUtils.extractId(ResourceType.Encounter.name(), srcObservation.getEncounter().getReferenceElement().getIdPart());
    var visitDetail = departmentCaseMapperService.getVisitStartDateTimeByFhirLogicId(fhirLogicalId);
    if(visitDetail != null){
      resourceOnset.setStartDateTime(visitDetail.getVisitDetailStartDatetime());
      resourceOnset.setEndDateTime(visitDetail.getVisitDetailEndDatetime());
    }
    return resourceOnset;
  }

  private void setHistoryOfTravelInObservation(
      OmopModelWrapper wrapper,
      Observation srcObservation,
      Long personId,
      Long visitOccId,
      String observationLogicId,
      String observationSourceIdentifier,
      String observationId) {
    // Only for GECCO History-of-travel profile
    var valueCodeableConcept = getValueCodeableConcept(srcObservation);
    if (valueCodeableConcept == null) {
      log.debug(
          "No [result for History-of-travel] found in [Observation]: {}. Skip resource.",
          observationId);
      noResultHistoryOfTravelCounter.increment();
      return;
    }
    var value =
        valueCodeableConcept.getCoding().stream()
            .filter(
                coding ->
                    FHIR_RESOURCE_GECCO_OBSERVATION_ACCEPTABLE_VALUE_CODE.contains(
                        coding.getCode()))
            .findFirst();
    if (!value.isPresent()) {
      log.debug(
          "No [acceptable result for History-of-travel] found in [Observation]: {}. Skip resource.",
          observationId);
      noAcceptableResultHistoryOfTravelCounter.increment();
      return;
    }
    if (!srcObservation.hasComponent()) {
      log.debug(
          "No [available information for History-of-travel] found in [Observation]: {}. Skip resource.",
          observationId);
      noAvailableInfoHistoryOfTravelCounter.increment();
      return;
    }

    var componentList = srcObservation.getComponent();
    if (componentList.isEmpty()) {
      log.debug(
          "No [available information for History-of-travel] found in [Observation]: {}. Skip resource.",
          observationId);
      noAvailableInfoHistoryOfTravelCounter.increment();
      return;
    }

    var travelComponentStartedDate = getComponentElement(componentList, "82752-7");
    setDateTimeTypeInObservation(
        wrapper,
        srcObservation,
        personId,
        visitOccId,
        observationLogicId,
        observationSourceIdentifier,
        travelComponentStartedDate,
        observationId);
    var travelComponentEndedDate = getComponentElement(componentList, "91560-3");
    setDateTimeTypeInObservation(
        wrapper,
        srcObservation,
        personId,
        visitOccId,
        observationLogicId,
        observationSourceIdentifier,
        travelComponentEndedDate,
        observationId);

    StringBuilder dataOne = new StringBuilder();
    StringBuilder dataTwo = new StringBuilder();
    dataOne.append(";");
    dataTwo.append(";");
    var travelComponentCity = getComponentElement(componentList, "94653-3");
    if (travelComponentCity != null) {
      dataOne.append(
          travelComponentCity
                  .getRight()
                  .castToString(travelComponentCity.getRight())
                  .asStringValue()
              + ";");
    }

    var travelComponentCountry = getComponentElement(componentList, "94651-7");
    if (travelComponentCountry != null) {
      dataOne.append(
          travelComponentCountry
              .getRight()
              .castToCodeableConcept(travelComponentCountry.getRight())
              .getCodingFirstRep()
              .getCode());
    }

    var travelComponentState = getComponentElement(componentList, "82754-3");
    if (travelComponentState != null) {
      dataTwo.append(
          travelComponentState
              .getRight()
              .castToCodeableConcept(travelComponentState.getRight())
              .getCodingFirstRep()
              .getCode());
    }
    if (dataOne.toString().equals(";") && dataTwo.toString().equals(";")) {
      return;
    }

    var ppm =
        PostProcessMap.builder()
            .type(Enumerations.ResourceType.OBSERVATION.name())
            .omopTable(OmopModelWrapper.Tablename.LOCATION.name())
            .fhirIdentifier(observationSourceIdentifier)
            .fhirLogicalId(observationLogicId)
            .dataOne(dataOne.toString())
            .dataTwo(dataTwo.toString())
            .build();

    wrapper.getPostProcessMap().add(ppm);
  }

  /**
   * @param wrapper
   * @param srcObservation
   * @param personId
   * @param visitOccId
   * @param observationLogicId
   * @param observationSourceIdentifier
   * @param dateTravelStartedComponent
   */
  private void setDateTimeTypeInObservation(
      OmopModelWrapper wrapper,
      Observation srcObservation,
      Long personId,
      Long visitOccId,
      String observationLogicId,
      String observationSourceIdentifier,
      Pair<Coding, Type> dateTravelDateComponent,
      String observationId) {
    if (dateTravelDateComponent == null) {
      return;
    }
    var travelDate =
        new Timestamp(
                dateTravelDateComponent
                    .getRight()
                    .castToDateTime(dateTravelDateComponent.getRight())
                    .getValue()
                    .getTime())
            .toLocalDateTime();
    var travelDateConcept =
        findOmopConcepts.getConcepts(
            dateTravelDateComponent.getLeft(),
            travelDate.toLocalDate(),
            bulkload,
            dbMappings,
            observationId);
    if (travelDateConcept == null) {
      return;
    }
    wrapper
        .getObservation()
        .add(
            createBasisObservation(
                srcObservation,
                personId,
                visitOccId,
                travelDateConcept.getConceptId(),
                travelDateConcept.getConceptId(),
                travelDateConcept.getConceptCode(),
                travelDate,
                observationLogicId,
                observationSourceIdentifier,
                observationId));
  }

  private Pair<Coding, Type> getComponentElement(
      List<ObservationComponentComponent> componentList, String componentCode) {
    if (componentList.isEmpty()) {
      return null;
    }
    for (var component : componentList) {
      var componentCoding = getComponentElementCoding(component, componentCode);
      if (componentCoding != null && component.getValue() != null) {
        return Pair.of(componentCoding, component.getValue());
      }
    }
    return null;
  }

  private Coding getComponentElementCoding(
      ObservationComponentComponent component, String componentCode) {
    var componentElementOptinal =
        component.getCode().getCoding().stream()
            .filter(coding -> coding.getCode().equals(componentCode))
            .findFirst();
    if (componentElementOptinal.isPresent()) {
      return componentElementOptinal.get();
    }
    return null;
  }

  /**
   * Extract code for laboratory analysis from Observation FHIR resource
   *
   * @param srcObservation FHIR Observation resource
   * @return code for laboratory analysis
   */
  private Coding getObservationCoding(Observation srcObservation) {
    if (!srcObservation.hasCode()) {
      return null;
    }

    var observationCodeableConcept = checkDataAbsentReason.getValue(srcObservation.getCode());
    if (observationCodeableConcept == null) {
      return null;
    }
    var observation =
        observationCodeableConcept.getCoding().stream()
            .filter(code -> fhirSystems.getObservationCode().contains(code.getSystem()))
            .findFirst();

    if (!observation.isPresent()) {
      return null;
    }
    return observation.get();
  }

  /**
   * Maps the processed FHIR Observation resource with lab data to OMOP CDM tables.
   *
   * @param wrapper cache of newly created OMOP CDM records from the FHIR Observation resource
   * @param observationCoding laboratory analysis code from the FHIR Observation resource
   * @param srcObservation FHIR Observation resource
   * @param personId person_id of the referenced FHIR Patient resource
   * @param visitOccId visit_occurrence_id of the referenced FHIR Encounter resource
   * @param effectiveDateTime date time of the FHIR Observation resource
   * @param observationLogicId logical id of the FHIR Observation resource
   * @param observationSourceIdentifier identifier of the FHIR Observation resource
   */
  private void setLabData(
      OmopModelWrapper wrapper,
      Coding observationCoding,
      Observation srcObservation,
      Long personId,
      Long visitOccId,
      LocalDateTime effectiveDateTime,
      String observationLogicId,
      String observationSourceIdentifier,
      String observationId) {
    Concept observationCodeConcept = null;
    List<Pair<String, List<StandardDomainLookup>>> loincStandardMapPairList = null;
    List<Pair<String, List<IcdSnomedDomainLookup>>> icdSnomedMapPairList = null;
    var observationVocabularyId =
        findOmopConcepts.getOmopVocabularyId(observationCoding.getSystem());

    if (FHIR_RESOURCE_GECCO_OBSERVATION_ECRF_PARAMETER_DOMAIN_OBSERVATION.contains(
        observationCoding.getCode())) {
      observationCodeConcept =
          createGeccoCustomConcept(
              observationCoding, SOURCE_VOCABULARY_ID_ECRF_PARAMETER, OMOP_DOMAIN_OBSERVATION);

      observationProcessor(
          null,
          observationCodeConcept,
          srcObservation,
          personId,
          visitOccId,
          effectiveDateTime,
          observationLogicId,
          observationSourceIdentifier,
          wrapper,
          observationId);

    } else if (FHIR_RESOURCE_GECCO_OBSERVATION_SOFA_CODES.contains(observationCoding.getCode())) {
      observationCodeConcept =
          createGeccoCustomConcept(
              observationCoding, SOURCE_VOCABULARY_ID_ECRF_PARAMETER, OMOP_DOMAIN_MEASUREMENT);

      observationProcessor(
          null,
          observationCodeConcept,
          srcObservation,
          personId,
          visitOccId,
          effectiveDateTime,
          observationLogicId,
          observationSourceIdentifier,
          wrapper,
          observationId);

    } else if (observationVocabularyId != null
        && observationVocabularyId.equals(VOCABULARY_LOINC)) {
      // for LOINC

      loincStandardMapPairList =
          getValidCodes(observationCoding, effectiveDateTime.toLocalDate(), observationId);

      if (loincStandardMapPairList.isEmpty()) {
        return;
      }
      for (var singlePair : loincStandardMapPairList) {
        observationProcessor(
            singlePair,
            null,
            srcObservation,
            personId,
            visitOccId,
            effectiveDateTime,
            observationLogicId,
            observationSourceIdentifier,
            wrapper,
            observationId);
      }
    } else if(observationVocabularyId != null
            && observationVocabularyId.equals(VOCABULARY_IPRD)){
      // for IPRD

      loincStandardMapPairList =
              getValidCodes(observationCoding, effectiveDateTime.toLocalDate(), observationId);

      if (loincStandardMapPairList.isEmpty()) {
        return;
      }
      for (var singlePair : loincStandardMapPairList) {
        observationProcessor(
                singlePair,
                null,
                srcObservation,
                personId,
                visitOccId,
                effectiveDateTime,
                observationLogicId,
                observationSourceIdentifier,
                wrapper,
                observationId);
      }
    }
    else if(observationVocabularyId != null
            && observationVocabularyId.equals(VOCABULARY_WHO)){
      // for WHO

      loincStandardMapPairList =
              getValidCodes(observationCoding, effectiveDateTime.toLocalDate(), observationId);

      if (loincStandardMapPairList.isEmpty()) {
        return;
      }
      for (var singlePair : loincStandardMapPairList) {
        observationProcessor(
                singlePair,
                null,
                srcObservation,
                personId,
                visitOccId,
                effectiveDateTime,
                observationLogicId,
                observationSourceIdentifier,
                wrapper,
                observationId);
      }
    }else if(observationVocabularyId != null
            && observationVocabularyId.equals(VOCABULARY_ICD10GM)){
      // for IPRD
      ArrayList<Coding> codings = new ArrayList<>();
      codings.add(observationCoding);
      var loincIcdMapPairList =
              getValidIcdCodes(codings, effectiveDateTime.toLocalDate(), observationId);
      if (loincIcdMapPairList.isEmpty()) {
        return;
      }
      for (var singlePair : loincIcdMapPairList) {
        observationProcessor(
                singlePair,
                null,
                srcObservation,
                personId,
                visitOccId,
                effectiveDateTime,
                observationLogicId,
                observationSourceIdentifier,
                wrapper,
                observationId);
      }
    }
  }

  private List<Pair<String, List<StandardDomainLookup>>> getValidIcdCodes(
          List<Coding> uncheckedIcds,
          LocalDate diagnoseDate,
          String conditionLogicId) {
    if (uncheckedIcds.isEmpty()) {
      return Collections.emptyList();
    }
    List<Pair<String, List<StandardDomainLookup>>> validIcdSnomedConceptMaps = new ArrayList<>();
    for (var uncheckedCode : uncheckedIcds) {
      String icdCode = uncheckedCode.getCode();
      if (icdCode == null) {
        return Collections.emptyList();
      }
      List<StandardDomainLookup> icdSnomedMap =
              findOmopConcepts.getStandardConcepts(
                      uncheckedCode, diagnoseDate, bulkload, dbMappings, conditionLogicId);
      if (icdSnomedMap.isEmpty()) {
        return Collections.emptyList();
      }
      validIcdSnomedConceptMaps.add(Pair.of(uncheckedCode.getCode(), icdSnomedMap));
    }
    return validIcdSnomedConceptMaps;
  }

  private void observationProcessor(
      @Nullable Pair<String, List<StandardDomainLookup>> loincStandardPair,
      @Nullable Concept observationCodeConcept,
      Observation srcObservation,
      Long personId,
      Long visitOccId,
      LocalDateTime effectiveDateTime,
      String observationLogicId,
      String observationSourceIdentifier,
      OmopModelWrapper wrapper,
      String observationId) {

    if (loincStandardPair == null && observationCodeConcept == null) {
      return;
    }

    if (observationCodeConcept != null) {
      setObservation(
          srcObservation,
          personId,
          visitOccId,
          effectiveDateTime,
          observationLogicId,
          observationSourceIdentifier,
          wrapper,
          observationCodeConcept.getConceptCode(),
          observationCodeConcept.getConceptId(),
          CONCEPT_NO_MATCHING_CONCEPT,
          observationCodeConcept.getDomainId(),
          observationId);
    } else {
      var loincCode = loincStandardPair.getLeft();
      var loincStandardMaps = loincStandardPair.getRight();

      for (var loincStandardMap : loincStandardMaps) {
        setObservation(
            srcObservation,
            personId,
            visitOccId,
            effectiveDateTime,
            observationLogicId,
            observationSourceIdentifier,
            wrapper,
            loincCode,
            loincStandardMap.getStandardConceptId(),
            loincStandardMap.getSourceConceptId(),
            loincStandardMap.getStandardDomainId(),
            observationId);
      }
    }
  }

  /** Write procedure information into correct OMOP tables based on their domains. */
  private void setObservation(
      Observation srcObservation,
      Long personId,
      Long visitOccId,
      LocalDateTime effectiveDateTime,
      String observationLogicId,
      String observationSourceIdentifier,
      OmopModelWrapper wrapper,
      String observationCode,
      Integer observationConceptId,
      Integer observationSourceConceptId,
      String domain,
      String observationId) {
    if(domain == null){
      log.warn("fhirId = {}={}",observationId,domain);
      return;
    }
    switch (domain) {
      case OMOP_DOMAIN_PROCEDURE:
        var procedure =
            setUpProcedure(
                srcObservation,
                personId,
                visitOccId,
                effectiveDateTime,
                observationConceptId,
                observationSourceConceptId,
                observationCode,
                observationLogicId,
                observationSourceIdentifier,
                observationId);

        wrapper.getProcedureOccurrence().add(procedure);

        break;
      case OMOP_DOMAIN_OBSERVATION:
        var observation =
            setUpObservation(
                srcObservation,
                personId,
                visitOccId,
                effectiveDateTime,
                observationConceptId,
                observationSourceConceptId,
                observationCode,
                observationLogicId,
                observationSourceIdentifier,
                wrapper,
                observationId);

        wrapper.getObservation().add(observation);

        break;
      case OMOP_DOMAIN_MEASUREMENT:
        var measurement =
            setUpMeasurement(
                srcObservation,
                personId,
                visitOccId,
                effectiveDateTime,
                observationConceptId,
                observationSourceConceptId,
                observationCode,
                observationLogicId,
                observationSourceIdentifier,
                wrapper,
                observationId);

        wrapper.getMeasurement().add(measurement);

        break;
      case OMOP_DOMAIN_CONDITION:
        var condition =
                setUpCondition(
                        effectiveDateTime,
                        observationConceptId,
                        observationSourceConceptId,
                        observationCode,
                        personId,
                        visitOccId,
                        observationLogicId,
                        observationSourceIdentifier,
                        srcObservation);
        wrapper.getConditionOccurrence().add(condition);
        break;
      default:
        log.error(
            "[Unsupported domain] {} of code in [Observation]: {}. Skip resource.",
            domain,
            observationId);
        break;
    }
  }

  private ConditionOccurrence setUpCondition(
          LocalDateTime effectiveDateTime,
          Integer diagnoseConceptId,
          Integer diagnoseSourceConceptId,
          String rawIcdCode,
          Long personId,
          Long visitOccId,
          String conditionLogicId,
          String conditionSourceIdentifier,
          Observation srcObservation) {
    String statusSourceValue = null;
    if(srcObservation.hasValueStringType()) {
      statusSourceValue = srcObservation.getValueStringType().getValue();
    } else if (srcObservation.hasValueCodeableConcept() && srcObservation.getValueCodeableConcept().hasCoding()) {
      statusSourceValue = srcObservation.getValueCodeableConcept().getCoding().get(0).getCode();
    }
    return ConditionOccurrence.builder()
            .personId(personId)
            .visitOccurrenceId(visitOccId)
            .conditionStartDate(effectiveDateTime.toLocalDate())
            .conditionStartDatetime(effectiveDateTime)
            .conditionSourceConceptId(diagnoseSourceConceptId)
            .conditionConceptId(diagnoseConceptId)
            .conditionTypeConceptId(CONCEPT_EHR)
            .conditionStatusSourceValue(statusSourceValue)
            .conditionSourceValue(rawIcdCode)
            .fhirLogicalId(conditionLogicId)
            .fhirIdentifier(conditionSourceIdentifier)
            .build();
  }

  /**
   * @param observationCoding
   * @return
   */
  private Concept createGeccoCustomConcept(
      Coding observationCoding, String vocabularyId, String domainId) {
    Concept observationCodeConcept;
    var geccoCustomConcept =
        findOmopConcepts.getCustomConcepts(observationCoding.getCode(), vocabularyId, dbMappings);
    observationCodeConcept =
        Concept.builder()
            .conceptCode(observationCoding.getCode())
            .conceptId(geccoCustomConcept.getTargetConceptId())
            .domainId(domainId)
            .build();
    return observationCodeConcept;
  }

  private ProcedureOccurrence setUpProcedure(
      Observation srcObservation,
      Long personId,
      Long visitOccId,
      LocalDateTime effectiveDateTime,
      Integer observationConceptId,
      Integer observationSourceConceptId,
      String observationCode,
      String observationLogicId,
      String observationSourceIdentifier,
      String observationId) {
    List<ProcedureOccurrence> procedures = new ArrayList<>();

    var basisProcedureOcc =
        createBasisProcedureOcc(
            srcObservation,
            personId,
            visitOccId,
            observationConceptId,
            observationSourceConceptId,
            observationCode,
            effectiveDateTime,
            observationLogicId,
            observationSourceIdentifier,
            observationId);
    var valueQuantity = getValueQuantity(srcObservation);
    var valueCodeableConcept = getValueCodeableConcept(srcObservation);

    if (valueQuantity == null && valueCodeableConcept == null) {
      log.debug(
          "No [ValueQuantity] or [ValueCodeableConcept] found for [Observation]: {}. Skip resource.",
          observationId);
      noValueCounter.increment();
      return null;
    }
    if (valueQuantity != null) {
      basisProcedureOcc.setModifierSourceValue(valueQuantity.getValue().toString());
    } else {
      setValueCodeableConceptInProcedure(
          effectiveDateTime, procedures, basisProcedureOcc, valueCodeableConcept, observationId);
    }

    return basisProcedureOcc;
  }

  /**
   * Creates new records of the observation table in OMOP CDM for the processed FHIR Observation
   * resource. The extracted LOINC code of the FHIR Observation resource belongs to the Observation
   * domain in OMOP CDM.
   *
   * @param srcObservation FHIR Observation resource
   * @param personId person_id of the referenced FHIR Patient resource
   * @param visitOccId visit_occurrence_id of the referenced FHIR Encounter resource
   * @param labObservationConcept concept of the laboratory analysis code from the FHIR Observation
   *     resource
   * @param effectiveDateTime date time of the FHIR Observation resource
   * @param observationLogicId logical id of the FHIR Observation resource
   * @param observationSourceIdentifier identifier of the FHIR Observation resource
   * @return list of new records of the observation table in OMOP CDM for the processed FHIR
   *     Observation resource
   */
  private OmopObservation setUpObservation(
      Observation srcObservation,
      Long personId,
      Long visitOccId,
      LocalDateTime effectiveDateTime,
      Integer observationConceptId,
      Integer observationSourceConceptId,
      String observationCode,
      String observationLogicId,
      String observationSourceIdentifier,
      OmopModelWrapper wrapper,
      String observationId) {
    List<OmopObservation> observations = new ArrayList<>();

    if (FHIR_RESOURCE_OBSERVATION_HISTORY_OF_TRAVEL_CODES.contains(observationCode)) {
      setHistoryOfTravelInObservation(
          wrapper,
          srcObservation,
          personId,
          visitOccId,
          observationLogicId,
          observationSourceIdentifier,
          observationId);
      return null;
    }

    var basisObservation =
        createBasisObservation(
            srcObservation,
            personId,
            visitOccId,
            observationConceptId,
            observationSourceConceptId,
            observationCode,
            effectiveDateTime,
            observationLogicId,
            observationSourceIdentifier,
            observationId);
    var valueQuantity = getValueQuantity(srcObservation);
    var valueCodeableConcept = getValueCodeableConcept(srcObservation);
    var valueIntegerType = getValueInteger(srcObservation);
    var valueStringType = getValueStringType(srcObservation);
    var valueDateTimeType = getValueDateTimeType(srcObservation);
    var valueBooleanType = getValueBooleanType(srcObservation);

    if (valueQuantity == null && valueCodeableConcept == null && valueIntegerType == null && valueStringType == null && valueDateTimeType == null && valueBooleanType == null) {
      log.debug(
          "No [Value] found for [Observation]: {}. Skip resource.",
          observationId);
      noValueCounter.increment();
      return null;
    }
    if (valueQuantity != null) {
      setValueQuantityInObservation(
          effectiveDateTime, observations, basisObservation, valueQuantity, observationId);
    } else if (valueCodeableConcept != null) {
      setValueCodeableConceptInObservation(
          effectiveDateTime, observations, basisObservation, valueCodeableConcept, observationId);
    } else if(valueIntegerType != null){
//      log.info("value = {} - observation id = {}",valueIntegerType,observationId);
      var integerToBigDecimal = new BigDecimal(String.valueOf(valueIntegerType.getValue()));
      basisObservation.setValueAsNumber(integerToBigDecimal);
    } else if(valueStringType != null){
      log.info("value = {} - observation id = {}",valueStringType,observationId);
      var processedValue = valueStringType.getValue().replaceAll("\n"," ").substring(0,Math.min(valueStringType.getValue().length(),60));
      basisObservation.setValueAsString(processedValue);
    } else if(valueBooleanType != null){
      basisObservation.setValueAsBoolean(valueBooleanType.getValue());
    } else {
      var localDateTime = new Timestamp(valueDateTimeType.getValue().getTime()).toLocalDateTime();
      basisObservation.setValueAsDateTime(localDateTime);
    }
    return basisObservation;
  }

  /**
   * Set value as quantity for OMOP OBSERVATION entry
   *
   * @param effectiveDateTime date time of the FHIR Observation resource
   * @param basisObservation the basic OBSERVATION entry
   * @param valueQuantity value from Observation FHIR resource as quantity
   */
  private void setValueQuantityInObservation(
      LocalDateTime effectiveDateTime,
      List<OmopObservation> observations,
      OmopObservation basisObservation,
      Quantity valueQuantity,
      String observationId) {
    basisObservation.setValueAsNumber(valueQuantity.getValue());
    if(valueQuantity.hasCode() && valueQuantity.hasSystem()){
      var quantityUnitCodingFormat =
              new Coding().setCode(valueQuantity.getCode()).setSystem(valueQuantity.getSystem());
      var valueQuantityUnitConcept =
              findOmopConcepts.getConcepts(
                      quantityUnitCodingFormat,
                      effectiveDateTime.toLocalDate(),
                      bulkload,
                      dbMappings,
                      observationId);
      if (valueQuantityUnitConcept != null) {

        basisObservation.setUnitConceptId(valueQuantityUnitConcept.getConceptId());
        basisObservation.setUnitSourceValue(
                valueQuantity.getUnit() == null
                        ? valueQuantityUnitConcept.getConceptCode()
                        : valueQuantity.getUnit());
        addToList(observations, basisObservation);
      }
    }else{
      if(valueQuantity.hasCode()){
        basisObservation.setUnitSourceValue(valueQuantity.getCode());
      }
      if(valueQuantity.hasUnit()){
        basisObservation.setUnitSourceValue(valueQuantity.getUnit());
      }
      addToList(observations, basisObservation);
    }

  }

  /**
   * Set value as code for OMOP OBSERVATION entry
   *
   * @param effectiveDateTime date time of the FHIR Observation resource
   * @param observations a list of OMOP OBSERVATION entries
   * @param basisObservation the basic OBSERVATION entry
   * @param valueCodeableConcept value from Observation FHIR resource as code
   */
  private void setValueCodeableConceptInObservation(
      LocalDateTime effectiveDateTime,
      List<OmopObservation> observations,
      OmopObservation basisObservation,
      CodeableConcept valueCodeableConcept,
      String observationId) {
    if (valueCodeableConcept.isEmpty()) {
      return;
    }
    if (valueCodeableConcept.getCoding().isEmpty()) {

      var valueConcept =
          findOmopConcepts.getCustomConcepts(
              valueCodeableConcept.getText(), SOURCE_VOCABULARY_ID_LAB_RESULT, dbMappings);

      basisObservation.setValueAsConceptId(valueConcept.getTargetConceptId());
      basisObservation.setValueAsString(valueConcept.getSourceCode());
    } else {
      for (var coding : valueCodeableConcept.getCoding()) {
        var system = coding.getSystem();
        Concept valueCodingConcept = null;
        if (fhirSystems.getGeccoBiologicalSex().contains(system)) {
          valueCodingConcept =
              createGeccoCustomConcept(coding, SOURCE_VOCABULARY_ID_GENDER, OMOP_DOMAIN_GENDER);
        } else {
          valueCodingConcept =
              findOmopConcepts.getConcepts(
                  coding, effectiveDateTime.toLocalDate(), bulkload, dbMappings, observationId);
        }

        if (valueCodingConcept != null) {
          basisObservation.setValueAsConceptId(valueCodingConcept.getConceptId());
          basisObservation.setValueAsString(valueCodingConcept.getConceptCode());
          addToList(observations, basisObservation);
        }
      }
    }
  }

  /**
   * Set value as code for OMOP procedure_occurrence entry
   *
   * @param effectiveDateTime date time of the FHIR Observation resource
   * @param procedures a list of OMOP procedure_occurrence entries
   * @param basisProcedureOcc the basic procedure_occurrence entry
   * @param valueCodeableConcept value from Observation FHIR resource as code
   */
  private void setValueCodeableConceptInProcedure(
      LocalDateTime effectiveDateTime,
      List<ProcedureOccurrence> procedures,
      ProcedureOccurrence basisProcedureOcc,
      CodeableConcept valueCodeableConcept,
      String observationId) {
    if (valueCodeableConcept.isEmpty()) {
      return;
    }
    if (valueCodeableConcept.getCoding().isEmpty()) {

      var valueConcept =
          findOmopConcepts.getCustomConcepts(
              valueCodeableConcept.getText(), SOURCE_VOCABULARY_ID_LAB_RESULT, dbMappings);

      basisProcedureOcc.setModifierConceptId(valueConcept.getTargetConceptId());
      basisProcedureOcc.setModifierSourceValue(valueConcept.getSourceCode());
    } else {
      for (var coding : valueCodeableConcept.getCoding()) {
        var system = coding.getSystem();
        Concept valueCodingConcept = null;
        if (fhirSystems.getGeccoBiologicalSex().contains(system)) {
          valueCodingConcept =
              createGeccoCustomConcept(coding, SOURCE_VOCABULARY_ID_GENDER, OMOP_DOMAIN_GENDER);
        } else {
          valueCodingConcept =
              findOmopConcepts.getConcepts(
                  coding, effectiveDateTime.toLocalDate(), bulkload, dbMappings, observationId);
        }

        if (valueCodingConcept != null) {
          basisProcedureOcc.setModifierConceptId(valueCodingConcept.getConceptId());
          basisProcedureOcc.setModifierSourceValue(valueCodingConcept.getConceptCode());
          addToList(procedures, basisProcedureOcc);
        }
      }
    }
  }

  /**
   * Add new OMOP OBSERVATION entry to a list of OBSERVATION entries without duplicates
   *
   * @param omopObservations a list of OBSERVATION entries
   * @param omopObservation new OMOP OBSERVATION entry
   */
  private void addToList(List<OmopObservation> omopObservations, OmopObservation omopObservation) {
    if (!omopObservations.contains(omopObservation)) {
      omopObservations.add(omopObservation);
    }
  }

  private ProcedureOccurrence createBasisProcedureOcc(
      Observation srcObservation,
      Long personId,
      Long visitOccId,
      Integer observationConceptId,
      Integer observationSourceConceptId,
      String observationCode,
      LocalDateTime effectiveDateTime,
      String observationLogicId,
      String observationSourceIdentifier,
      String observationId) {
    var categoryCoding = getCategoryCoding(srcObservation, observationId);
    var categoryCode = categoryCoding == null ? null : categoryCoding.getCode();

    SourceToConceptMap categoryConcept =
        findOmopConcepts.getCustomConcepts(
            categoryCode, SOURCE_VOCABULARY_ID_OBSERVATION_CATEGORY, dbMappings);

    return ProcedureOccurrence.builder()
        .personId(personId)
        .visitOccurrenceId(visitOccId)
        .procedureTypeConceptId(categoryConcept.getTargetConceptId())
        .procedureDate(effectiveDateTime.toLocalDate())
        .procedureDatetime(effectiveDateTime)
        .procedureSourceConceptId(observationSourceConceptId)
        .procedureConceptId(observationConceptId)
        .procedureSourceValue(observationCode)
        .fhirLogicalId(observationLogicId)
        .fhirIdentifier(observationSourceIdentifier)
        .build();
  }

  /**
   * Create a new OMOP Observation with basic information extracted from FHIR Observation resource
   *
   * @param srcObservation FHIR Observation resource
   * @param personId person_id of the referenced FHIR Patient resource
   * @param visitOccId visit_occurrence_id of the referenced FHIR Encounter resource
   * @param labObservationConcept concept of the laboratory analysis code from the FHIR Observation
   *     resource
   * @param effectiveDateTime date time of the FHIR Observation resource
   * @param observationLogicId logical id of the FHIR Observation resource
   * @param observationSourceIdentifier identifier of the FHIR Observation resource
   * @return a new OMOP Observation with basic information extracted from FHIR Observation resource
   */
  private OmopObservation createBasisObservation(
      Observation srcObservation,
      Long personId,
      Long visitOccId,
      Integer observationConceptId,
      Integer observationSourceConceptId,
      String observationCode,
      LocalDateTime effectiveDateTime,
      String observationLogicId,
      String observationSourceIdentifier,
      String observationId) {
    String interpretation = getInterpretation(srcObservation, observationId);
    var categoryCoding = getCategoryCoding(srcObservation, observationId);
    var categoryCode = categoryCoding == null ? null : categoryCoding.getCode();

    SourceToConceptMap categoryConcept =
        findOmopConcepts.getCustomConcepts(
            categoryCode, SOURCE_VOCABULARY_ID_OBSERVATION_CATEGORY, dbMappings);

    var newLabObservation =
        OmopObservation.builder()
            .personId(personId)
            .visitOccurrenceId(visitOccId)
            .observationTypeConceptId(categoryConcept.getTargetConceptId())
            .observationDate(effectiveDateTime.toLocalDate())
            .observationDatetime(effectiveDateTime)
            .observationSourceConceptId(observationSourceConceptId)
            .observationConceptId(observationConceptId)
            .observationSourceValue(observationCode)
            .fhirLogicalId(observationLogicId)
            .fhirIdentifier(observationSourceIdentifier)
            .build();
    if (StringUtils.isNotBlank(interpretation)) {
      var interpretationConcept =
          findOmopConcepts.getCustomConcepts(
              interpretation, SOURCE_VOCABULARY_ID_LAB_INTERPRETATION, dbMappings);

      newLabObservation.setQualifierConceptId(interpretationConcept.getTargetConceptId());
      newLabObservation.setQualifierSourceValue(
          StringUtils.left(interpretation, MAX_SOURCE_VALUE_LENGTH));
    }
    return newLabObservation;
  }

  /**
   * Extracts value-quantity information from the FHIR Observation resource.
   *
   * @param srcObservation FHIR Observation resource
   * @return value-quantity from FHIR Observation resource
   */
  private Quantity getValueQuantity(Observation srcObservation) {
    if (srcObservation.hasValueQuantity() && srcObservation.getValueQuantity() != null) {
      return srcObservation.getValueQuantity();
    }
    return null;
  }

  /**
   * Extracts value-codeable-concept information from the FHIR Observation resource.
   *
   * @param srcObservation FHIR Observation resource
   * @return value-codeable-concept from FHIR Observation resource
   */
  private CodeableConcept getValueCodeableConcept(Observation srcObservation) {
    if (srcObservation.hasValueCodeableConcept()) {
      return srcObservation.getValueCodeableConcept();
    }
    return null;
  }

  /**
   * Extracts interpretation information from the FHIR Observation resource.
   *
   * @param srcObservation FHIR Observation resource
   * @return interpretation from FHIR Observation resource
   */
  private String getInterpretation(Observation srcObservation, String observationId) {
    if (!srcObservation.hasInterpretation() || srcObservation.getInterpretation().isEmpty()) {
      log.debug("No [Interpretation] found in [Observation]: {}.", observationId);
      noInterpretationCounter.increment();
      return null;
    }
    var interpretation =
        srcObservation.getInterpretationFirstRep().getCoding().stream()
            .filter(x -> x.getSystem().equalsIgnoreCase(fhirSystems.getInterpretation()))
            .findFirst();

    if (interpretation.isEmpty()) {
      log.debug("No [Interpretation] found in [Observation]: {}.", observationId);
      noInterpretationCounter.increment();
      return null;
    }
    return interpretation.get().getCode();
  }

  /**
   * Creates new records of the measurement table in OMOP CDM for the processed FHIR Observation
   * resource. The extracted LOINC code of the FHIR Observation resource belongs to the Measurement
   * domain in OMOP CDM.
   *
   * @param srcObservation FHIR Observation resource
   * @param personId person_id of the referenced FHIR Patient resource
   * @param visitOccId visit_occurrence_id of the referenced FHIR Encounter resource
   * @param labObservationConcept concept of the laboratory analysis code from the FHIR Observation
   *     resource
   * @param effectiveDateTime date time of the FHIR Observation resource
   * @param observationLogicId logical id of the FHIR Observation resource
   * @param observationSourceIdentifier identifier of the FHIR Observation resource
   * @return list of new records of the measurement table in OMOP CDM for the processed FHIR
   *     Observation resource
   */
  private Measurement setUpMeasurement(
      Observation srcObservation,
      Long personId,
      Long visitOccId,
      LocalDateTime effectiveDateTime,
      Integer observationConceptId,
      Integer observationSourceConceptId,
      String observationCode,
      String observationLogicId,
      String observationSourceIdentifier,
      OmopModelWrapper wrapper,
      String observationId) {
    List<Measurement> measurements = new ArrayList<>();

    if (FHIR_RESOURCE_GECCO_OBSERVATION_IN_MEASUREMENT_DOMAIN_CODES.contains(observationCode)) {
      setMeasurementUsingComponent(
          srcObservation,
          personId,
          visitOccId,
          observationConceptId,
          observationSourceConceptId,
          observationCode,
          effectiveDateTime,
          observationLogicId,
          observationSourceIdentifier,
          wrapper,
          observationId);
      return null;
    }

    var basisMeasurement =
        createBasisMeasurement(
            srcObservation,
            personId,
            visitOccId,
            observationConceptId,
            observationSourceConceptId,
            observationCode,
            effectiveDateTime,
            observationLogicId,
            observationSourceIdentifier,
            observationId);
    var valueQuantity = getValueQuantity(srcObservation);
    var valueCodeableConcept = getValueCodeableConcept(srcObservation);

    if (valueQuantity == null && valueCodeableConcept == null) {
      log.debug(
          "No [ValueQuantity] or [ValueCodeableConcept] found for [Observation]: {}. Skip resource.",
          observationId);
      noValueCounter.increment();
      return null;
    }
    if (valueQuantity != null) {
      setValueQuantityInMeasurement(
          effectiveDateTime, measurements, basisMeasurement, valueQuantity, observationId);
    } else {
      setValueCodeableConceptInMeasurement(
          effectiveDateTime, measurements, basisMeasurement, valueCodeableConcept, observationId);
    }

    return basisMeasurement;
  }

  private void setMeasurementUsingComponent(
      Observation srcObservation,
      Long personId,
      Long visitOccId,
      Integer observationConceptId,
      Integer observationSourceConceptId,
      String observationCode,
      LocalDateTime effectiveDateTime,
      String observationLogicId,
      String observationSourceIdentifier,
      OmopModelWrapper wrapper,
      String observationId) {
    var componentList = srcObservation.getComponent();
    var valueInteger = srcObservation.getValueIntegerType().getValue();
    if ((componentList == null || componentList.isEmpty()) && valueInteger == null) {
      return;
    }
    var categoryCoding = getCategoryCoding(srcObservation, observationId);
    var categoryCode = categoryCoding == null ? null : categoryCoding.getCode();

    SourceToConceptMap categoryConcept =
        findOmopConcepts.getCustomConcepts(
            categoryCode, SOURCE_VOCABULARY_ID_OBSERVATION_CATEGORY, dbMappings);
    if (categoryConcept == null) {
      return;
    }

    if (FHIR_RESOURCE_GECCO_OBSERVATION_SOFA_CODES.contains(observationCode)) {
      var geccoSofaTotalMeasurement =
          createGeccoSofaTotalMeasurement(
              personId,
              visitOccId,
              observationConceptId,
              observationSourceConceptId,
              observationCode,
              valueInteger,
              effectiveDateTime,
              observationLogicId,
              observationSourceIdentifier);
      geccoSofaTotalMeasurement.setMeasurementTypeConceptId(categoryConcept.getTargetConceptId());
      wrapper.getMeasurement().add(geccoSofaTotalMeasurement);
    }

    for (var component : componentList) {
      var componentAfterCheckedDataAbsentReason = checkDataAbsentReason.getValue(component);
      if (componentAfterCheckedDataAbsentReason == null) {
        return;
      }
      var componentCoding = getComponentCoding(component);
      if (componentCoding == null) {
        continue;
      }

      if (FHIR_RESOURCE_GECCO_OBSERVATION_BLOOD_PRESSURE_CODES.contains(observationCode)) {

        var geccoBloodPressureMeasurement =
            createGeccoBloodPressureMeasurement(
                component,
                personId,
                visitOccId,
                observationConceptId,
                componentCoding,
                effectiveDateTime,
                observationLogicId,
                observationSourceIdentifier,
                observationId);
        if (geccoBloodPressureMeasurement != null) {
          geccoBloodPressureMeasurement.setMeasurementTypeConceptId(
              categoryConcept.getTargetConceptId());
        }

        wrapper.getMeasurement().add(geccoBloodPressureMeasurement);
      }
      if (FHIR_RESOURCE_GECCO_OBSERVATION_SOFA_CODES.contains(observationCode)) {
        var geccoSofaMeasurement =
            createGeccoSofaMeasurement(
                component,
                personId,
                visitOccId,
                componentCoding,
                effectiveDateTime,
                observationLogicId,
                observationSourceIdentifier);
        if (geccoSofaMeasurement != null) {
          geccoSofaMeasurement.setMeasurementTypeConceptId(categoryConcept.getTargetConceptId());
        }
        wrapper.getMeasurement().add(geccoSofaMeasurement);
      }
    }
  }

  private Measurement createGeccoSofaTotalMeasurement(
      Long personId,
      Long visitOccId,
      Integer observationConceptId,
      Integer observationSourceConceptId,
      String observationCode,
      Integer valueInteger,
      LocalDateTime effectiveDateTime,
      String observationLogicId,
      String observationSourceIdentifier) {

    return Measurement.builder()
        .personId(personId)
        .visitOccurrenceId(visitOccId)
        .valueAsNumber(BigDecimal.valueOf(valueInteger))
        .measurementConceptId(observationConceptId)
        .measurementSourceConceptId(observationSourceConceptId)
        .measurementSourceValue(observationCode)
        .measurementDate(effectiveDateTime.toLocalDate())
        .measurementDatetime(effectiveDateTime)
        .fhirIdentifier(observationSourceIdentifier)
        .fhirLogicalId(observationLogicId)
        .build();
  }

  private Measurement createGeccoSofaMeasurement(
      ObservationComponentComponent component,
      Long personId,
      Long visitOccId,
      Coding componentCoding,
      LocalDateTime effectiveDateTime,
      String observationLogicId,
      String observationSourceIdentifier) {
    var componentCodingConcept =
        findOmopConcepts.getCustomConcepts(
            componentCoding.getCode(), SOURCE_VOCABULARY_SOFA_CATEGORY, dbMappings);
    if (componentCodingConcept == null) {
      return null;
    }
    var componentValueCodeableConcept = component.getValueCodeableConcept();
    if (componentValueCodeableConcept == null) {
      return null;
    }

    var componentValueCodeOptional =
        componentValueCodeableConcept.getCoding().stream()
            .filter(coding -> coding.getSystem().equals(fhirSystems.getGeccoSofaScore()))
            .findFirst();
    if (!componentValueCodeOptional.isPresent()) {
      return null;
    }
    var componentValueCode = componentValueCodeOptional.get().getCode();
    if (componentValueCode == null) {
      return null;
    }
    var componentValueNumber =
        BigDecimal.valueOf(Long.parseLong(componentValueCode.replaceAll("[^0-9]", "")));
    return Measurement.builder()
        .personId(personId)
        .visitOccurrenceId(visitOccId)
        .measurementDate(effectiveDateTime.toLocalDate())
        .measurementDatetime(effectiveDateTime)
        .measurementConceptId(componentCodingConcept.getTargetConceptId())
        .measurementSourceConceptId(componentCodingConcept.getTargetConceptId())
        .measurementSourceValue(componentCoding.getCode())
        .valueSourceValue(componentValueCode)
        .valueAsNumber(componentValueNumber)
        .fhirIdentifier(observationSourceIdentifier)
        .fhirLogicalId(observationLogicId)
        .build();
  }

  private Measurement createGeccoBloodPressureMeasurement(
      ObservationComponentComponent component,
      Long personId,
      Long visitOccId,
      Integer observationConceptId,
      Coding componentCoding,
      LocalDateTime effectiveDateTime,
      String observationLogicId,
      String observationSourceIdentifier,
      String observationId) {

    var componentCodingConcept =
        findOmopConcepts.getConcepts(
            componentCoding, effectiveDateTime.toLocalDate(), bulkload, dbMappings, observationId);
    if (componentCodingConcept == null) {
      return null;
    }
    var componentValueQuantity = component.getValueQuantity();
    if (componentValueQuantity == null) {
      return null;
    }
    if (componentValueQuantity.getValue() == null) {
      return null;
    }
    var componentValueQuantityUnitCoding =
        new Coding()
            .setCode(componentValueQuantity.getCode())
            .setSystem(componentValueQuantity.getSystem());
    var componentValueQuantityUnitConcept =
        findOmopConcepts.getConcepts(
            componentValueQuantityUnitCoding,
            effectiveDateTime.toLocalDate(),
            bulkload,
            dbMappings,
            observationLogicId);
    return Measurement.builder()
        .personId(personId)
        .visitOccurrenceId(visitOccId)
        .measurementDate(effectiveDateTime.toLocalDate())
        .measurementDatetime(effectiveDateTime)
        .measurementConceptId(observationConceptId)
        .measurementSourceConceptId(componentCodingConcept.getConceptId())
        .measurementSourceValue(componentCoding.getCode())
        .valueSourceValue(componentValueQuantity.getValue().toString())
        .valueAsNumber(componentValueQuantity.getValue())
        .unitSourceValue(componentValueQuantity.getUnit())
        .unitConceptId(
            componentValueQuantityUnitConcept == null
                ? null
                : componentValueQuantityUnitConcept.getConceptId())
        .fhirIdentifier(observationSourceIdentifier)
        .fhirLogicalId(observationLogicId)
        .build();
  }

  private Coding getComponentCoding(ObservationComponentComponent component) {
    var codeableConcept = component.getCode();
    if (codeableConcept == null) {
      return null;
    }
    var codingList = codeableConcept.getCoding();
    if (codingList.isEmpty()) {
      return null;
    }
    var codingCodeOptional =
        codingList.stream()
            .filter(coding -> fhirSystems.getGeccoComponents().contains(coding.getSystem()))
            .findFirst();
    if (!codingCodeOptional.isPresent()) {
      return null;
    }
    return codingCodeOptional.get();
  }

  /**
   * Set value as code for OMOP MEASUREMENT entry
   *
   * @param effectiveDateTime date time of the FHIR Observation resource
   * @param measurements a list of OMOP MEASUREMENT entries
   * @param basisMeasurement the basic MEASUREMENT entry
   * @param valueCodeableConcept value from Observation FHIR resource as code
   */
  private void setValueCodeableConceptInMeasurement(
      LocalDateTime effectiveDateTime,
      List<Measurement> measurements,
      Measurement basisMeasurement,
      CodeableConcept valueCodeableConcept,
      String observationId) {
    if (valueCodeableConcept.isEmpty()) {
      return;
    }
    if (valueCodeableConcept.getCoding().isEmpty()) {

      var valueConcept =
          findOmopConcepts.getCustomConcepts(
              valueCodeableConcept.getText(), SOURCE_VOCABULARY_ID_LAB_RESULT, dbMappings);

      basisMeasurement.setValueAsConceptId(valueConcept.getTargetConceptId());
      basisMeasurement.setValueSourceValue(valueConcept.getSourceCode());

    } else {
      for (var coding : valueCodeableConcept.getCoding()) {
        var codingCode = coding.getCode();
        if (codingCode == null || Strings.isNullOrEmpty(codingCode)) {
          continue;
        }
        var codingSystemUrl = coding.getSystem();
        if (codingSystemUrl.equals(fhirSystems.getGeccoFrailtyScore())) {
          var valueCodingConcept =
              findOmopConcepts.getCustomConcepts(
                  codingCode, SOURCE_VOCABULARY_FRAILTY_SCORE, dbMappings);
          if (valueCodingConcept != null) {
            basisMeasurement.setValueAsConceptId(valueCodingConcept.getTargetConceptId());
            basisMeasurement.setValueSourceValue(codingCode);
          }
        } else {
          var valueCodingConcept =
              findOmopConcepts.getConcepts(
                  coding, effectiveDateTime.toLocalDate(), bulkload, dbMappings, observationId);
          if (valueCodingConcept == null) {
            continue;
          }
          basisMeasurement.setValueAsConceptId(valueCodingConcept.getConceptId());
          basisMeasurement.setValueSourceValue(valueCodingConcept.getConceptCode());
        }
        addToList(measurements, basisMeasurement);
      }
    }
  }

  /**
   * Set value as quantity for OMOP MEASUREMENT entry
   *
   * @param effectiveDateTimedate time of the FHIR Observation resource
   * @param basisMeasurement the basic MEASUREMENT entry
   * @param valueQuantity value from Observation FHIR resource as quantity
   */
  private void setValueQuantityInMeasurement(
      LocalDateTime effectiveDateTime,
      List<Measurement> measurements,
      Measurement basisMeasurement,
      Quantity valueQuantity,
      String observationId) {
    basisMeasurement.setValueAsNumber(valueQuantity.getValue());
    basisMeasurement.setValueSourceValue(valueQuantity.getValue().toString());

    if(valueQuantity.hasCode() && valueQuantity.hasSystem()){
      var quantityUnitCodingFormat =
              new Coding().setCode(valueQuantity.getCode()).setSystem(valueQuantity.getSystem());
      var valueQuantityUnitConcept =
              findOmopConcepts.getConcepts(
                      quantityUnitCodingFormat,
                      effectiveDateTime.toLocalDate(),
                      bulkload,
                      dbMappings,
                      observationId);
      if (valueQuantityUnitConcept != null) {

        basisMeasurement.setUnitConceptId(valueQuantityUnitConcept.getConceptId());
        basisMeasurement.setUnitSourceValue(
                valueQuantity.getUnit() == null
                        ? valueQuantityUnitConcept.getConceptCode()
                        : valueQuantity.getUnit());
        addToList(measurements, basisMeasurement);
      }
    }else{
      if(valueQuantity.hasCode()){
        basisMeasurement.setUnitSourceValue(valueQuantity.getCode());
      }
      if(valueQuantity.hasUnit()){
        basisMeasurement.setUnitSourceValue(valueQuantity.getUnit());
      }
      addToList(measurements, basisMeasurement);
    }
  }

  /**
   * Add new OMOP MEASUREMENT entry to a list of MEASUREMENT entries without duplicates
   *
   * @param omopMeasurements a list of MEASUREMENT entries
   * @param omopMeasurement new OMOP MEASUREMENT entry
   */
  private void addToList(List<Measurement> omopMeasurements, Measurement omopMeasurement) {
    if (!omopMeasurements.contains(omopMeasurement)) {
      omopMeasurements.add(omopMeasurement);
    }
  }

  /**
   * Add new OMOP procedure_occurrence entry to a list of procedure_occurrence entries without
   * duplicates
   *
   * @param omopProcedureOccs a list of procedure_occurrence entries
   * @param omopProcedureOcc new OMOP procedure_occurrence entry
   */
  private void addToList(
      List<ProcedureOccurrence> omopProcedureOccs, ProcedureOccurrence omopProcedureOcc) {
    if (!omopProcedureOccs.contains(omopProcedureOcc)) {
      omopProcedureOccs.add(omopProcedureOcc);
    }
  }

  /**
   * Create a new Measurement with basic information extracted from FHIR Observation resource
   *
   * @param srcObservation FHIR Observation resource
   * @param personId person_id of the referenced FHIR Patient resource
   * @param visitOccId visit_occurrence_id of the referenced FHIR Encounter resource
   * @param labObservationConcept concept of the laboratory analysis code from the FHIR Observation
   *     resource
   * @param effectiveDateTime date time of the FHIR Observation resource
   * @param observationLogicId logical id of the FHIR Observation resource
   * @param observationSourceIdentifier identifier of the FHIR Observation resource
   * @return a new Measurement with basic information extracted from FHIR Observation resource
   */
  private Measurement createBasisMeasurement(
      Observation srcObservation,
      Long personId,
      Long visitOccId,
      Integer observationConceptId,
      Integer observationSourceConceptId,
      String observationCode,
      LocalDateTime effectiveDateTime,
      String observationLogicId,
      String observationSourceIdentifier,
      String observationId) {
    var categoryCoding = getCategoryCoding(srcObservation, observationId);
    var categoryCode = categoryCoding == null ? null : categoryCoding.getCode();

    SourceToConceptMap categoryConcept =
        findOmopConcepts.getCustomConcepts(
            categoryCode, SOURCE_VOCABULARY_ID_OBSERVATION_CATEGORY, dbMappings);

    String interpretation = getInterpretation(srcObservation, observationId);

    var newLabMeasurement =
        Measurement.builder()
            .personId(personId)
            .measurementDate(effectiveDateTime.toLocalDate())
            .measurementDatetime(effectiveDateTime)
            .measurementTypeConceptId(categoryConcept.getTargetConceptId())
            .visitOccurrenceId(visitOccId)
            .measurementConceptId(observationConceptId)
            .measurementSourceConceptId(observationSourceConceptId)
            .measurementSourceValue(observationCode)
            .fhirLogicalId(observationLogicId)
            .fhirIdentifier(observationSourceIdentifier)
            .build();

    if (!Strings.isNullOrEmpty(interpretation)) {
      var interpretationConcept =
          findOmopConcepts.getCustomConcepts(
              interpretation, SOURCE_VOCABULARY_ID_LAB_INTERPRETATION, dbMappings);
      newLabMeasurement.setOperatorConceptId(interpretationConcept.getTargetConceptId());
    }
    setReferenceRange(srcObservation, newLabMeasurement, observationId);
    return newLabMeasurement;
  }

  /**
   * Sets the extracted reference range information from FHIR Observation resource to the new
   * measurement record.
   *
   * @param srcObservation FHIR Observation resource
   * @param newLabMeasurement record of the measurement table in OMOP CDM for the processed FHIR
   *     Observation resource
   */
  private void setReferenceRange(
      Observation srcObservation, Measurement newLabMeasurement, String observationId) {
    var referenceRange = getReferenceRange(srcObservation);

    if (referenceRange == null) {
      log.debug("No [Reference Range] found in [Observation]: {}.", observationId);
      noReferenceRangeCounter.increment();
      return;
    }

    if (referenceRange.hasHigh() && referenceRange.getHigh() != null) {
      newLabMeasurement.setRangeHigh(referenceRange.getHigh().getValue());
    } else {
      log.debug("Missing [high range] for [Observation]: {}.", observationId);
      missingHighRangeCounter.increment();
    }

    if (referenceRange.hasLow() && referenceRange.getLow() != null) {
      newLabMeasurement.setRangeLow(referenceRange.getLow().getValue());
    } else {
      log.debug("Missing [low range] for [Observation]: {}.", observationId);
      missingLowRangeCounter.increment();
    }
  }

  /**
   * Extracts reference range information from the FHIR Observation resource.
   *
   * @param srcObservation FHIR Observation resource
   * @return reference range from FHIR Observation resource
   */
  private ObservationReferenceRangeComponent getReferenceRange(Observation srcObservation) {
    if (srcObservation.hasReferenceRange()
        && !srcObservation.getReferenceRangeFirstRep().isEmpty()) {
      return srcObservation.getReferenceRangeFirstRep();
    }
    return null;
  }

  /**
   * Extracts category information from the FHIR Observation resource.
   *
   * @param srcObservation FHIR Observation resource
   * @return category from FHIR Observation resource
   */
  private Coding getCategoryCoding(Observation srcObservation, String observationId) {
    if (!srcObservation.hasCategory() || srcObservation.getCategory().isEmpty()) {
      log.warn(
          "No [Category] found for [Observation]: {}. Invalid resource. Please Check.",
          observationId);
      noCategoryCount.increment();
      return null;
    }
    var categories = srcObservation.getCategory();
    for (var category : categories) {
      var categoryCode =
          category.getCoding().stream()
              //              .filter(cat ->
              // cat.getSystem().equals(fhirSystems.getLabObservationCategory()))
              .filter(cat -> fhirSystems.getLabObservationCategory().contains(cat.getSystem()))
              .findFirst();
      if (!categoryCode.isEmpty()) {
        return categoryCode.get();
      }
    }
    return null;
  }

  /**
   * Delete FHIR Observation resources from OMOP CDM tables using fhir_logical_id and
   * fhir_identifier
   *
   * @param observationLogicId logical id of the FHIR Observation resource
   * @param observationSourceIdentifier identifier of the FHIR Observation resource
   */
  private void deleteExistingLabObservations(
      String observationLogicId, String observationSourceIdentifier) {
    if (!Strings.isNullOrEmpty(observationLogicId)) {
      observationService.deleteExistingLabObservationByFhirLogicalId(observationLogicId);
    } else {
      observationService.deleteExistingLabObservationByFhirIdentifier(observationSourceIdentifier);
    }
  }

  /**
   * Extract valid pairs of LOINC code and its OMOP concept_id and domain information as a list
   *
   * @param coding
   * @param observationDate the date of observation
   * @return a list of valid pairs of LOINC code and its OMOP concept_id and domain information
   */
  private List<Pair<String, List<StandardDomainLookup>>> getValidCodes(
      Coding coding, LocalDate observationDate, String observationId) {
    if (coding == null) {
      return Collections.emptyList();
    }

    List<Pair<String, List<StandardDomainLookup>>> validStandardConceptMaps =
        new ArrayList<>();
    List<StandardDomainLookup> standardMap =
        findOmopConcepts.getStandardConcepts(
            coding, observationDate, bulkload, dbMappings, observationId);
    if (standardMap.isEmpty()) {
      return Collections.emptyList();
    }

    validStandardConceptMaps.add(Pair.of(coding.getCode(), standardMap));

    return validStandardConceptMaps;
  }

  private IntegerType getValueInteger(Observation srcObservation) {
    if (srcObservation.hasValueIntegerType() && srcObservation.getValueIntegerType() != null) {
      return srcObservation.getValueIntegerType();
    }
    return null;
  }

  private StringType getValueStringType(Observation srcObservation) {
    if (srcObservation.hasValueStringType() && srcObservation.getValueStringType() != null) {
      return srcObservation.getValueStringType();
    }
    return null;
  }

  private DateTimeType getValueDateTimeType(Observation srcObservation) {
    if (srcObservation.hasValueDateTimeType() && srcObservation.getValueDateTimeType() != null) {
      return srcObservation.getValueDateTimeType();
    }
    return null;
  }

  private BooleanType getValueBooleanType(Observation srcObservation) {
    if (srcObservation.hasValueBooleanType() && srcObservation.getValueBooleanType() != null) {
      return srcObservation.getValueBooleanType();
    }
    return null;
  }
}
