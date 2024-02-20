package org.miracum.etl.fhirtoomop.mapper;

import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.StringType;
import org.miracum.etl.fhirtoomop.DbMappings;
import org.miracum.etl.fhirtoomop.mapper.helpers.ResourceFhirReferenceUtils;
import org.miracum.etl.fhirtoomop.model.OmopModelWrapper;
import org.miracum.etl.fhirtoomop.model.omop.DrugExposure;
import org.miracum.etl.fhirtoomop.model.omop.Provider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import static org.miracum.etl.fhirtoomop.Constants.CONCEPT_EHR;

@Slf4j
@Component
public class PractitionerMapper implements FhirMapper<Practitioner> {

    private final Boolean bulkload;
    private final DbMappings dbMappings;

    @Autowired
    ResourceFhirReferenceUtils fhirReferenceUtils;

    public PractitionerMapper(Boolean bulkload, DbMappings dbMappings) {
        this.bulkload = bulkload;
        this.dbMappings = dbMappings;
    }


    @Override
    public OmopModelWrapper map(Practitioner resource, boolean isDeleted) {
        var wrapper = new OmopModelWrapper();
        Random rand = new Random();
        String gender = String.valueOf(resource.getGenderElement().getValue());
        Integer id = Math.abs(rand.nextInt());
        log.info(
                "Gender value for Practitioner resource is " + gender + " " + id);

        List<StringType> givenNames = resource.getName().get(0).getGiven();
        List<String> givenNamesStrings = givenNames.stream()
                .map(StringType::getValue)
                .collect(Collectors.toList());
        String concatenatedGivenNames = String.join(" ", givenNamesStrings);
        String familyName = resource.getName().get(0).getFamily();
        String fullName = concatenatedGivenNames + " " + familyName;

        Date date = resource.getBirthDate();
        LocalDate localDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        int year = localDate.getYear();
        var practitionerId = fhirReferenceUtils.extractId(resource);


        Provider provider = setUpProvier(gender,id,fullName,year,practitionerId);
        wrapper.getProvider().add(provider);
        return wrapper;
    }

    private Provider setUpProvier(String gender,Integer id,String fullName, Integer year, String practitionerId ){
        var provider =
                Provider.builder()
                        .providerId(id)
                        .providerName(fullName)
                        .npi("adfadf")
                        .dea("adfasdzc")
                        .specialtyConceptId(1)
                        .careSiteId(null)
                        .yearOfBirth(year)
                        .genderConceptId(null)
                        .providerSourceValue("visitOccId")
                        .specialtySourceValue("CONCEPT_EHR")
                        .specialtySourceConceptId(null)
                        .genderSourceValue(gender)
                        .genderSourceConceptId(null)
                        .fhirLogicalId(practitionerId)
                        .build();
        return provider;
    }
}
