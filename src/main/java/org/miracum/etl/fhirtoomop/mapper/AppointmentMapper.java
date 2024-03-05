package org.miracum.etl.fhirtoomop.mapper;

import ca.uhn.fhir.fhirpath.IFhirPath;
import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Appointment;
import org.miracum.etl.fhirtoomop.DbMappings;
import org.miracum.etl.fhirtoomop.config.FhirSystems;
import org.miracum.etl.fhirtoomop.mapper.helpers.FindOmopConcepts;
import org.miracum.etl.fhirtoomop.mapper.helpers.ResourceFhirReferenceUtils;
import org.miracum.etl.fhirtoomop.model.OmopModelWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.ZoneId;

@Slf4j
@Component
public class AppointmentMapper implements FhirMapper<Appointment> {

    private static final FhirSystems fhirSystems = new FhirSystems();
    private final IFhirPath fhirPath;
    private final Boolean bulkload;
    private final DbMappings dbMappings;
    @Autowired
    ResourceFhirReferenceUtils fhirReferenceUtils;
    @Autowired
    FindOmopConcepts findOmopConcepts;

    @Autowired
    public AppointmentMapper(IFhirPath fhirPath, Boolean bulkload, DbMappings dbMappings) {

        this.fhirPath = fhirPath;
        this.bulkload = bulkload;
        this.dbMappings = dbMappings;
    }

    @Override
    public OmopModelWrapper map(Appointment srcAppointment, boolean isDeleted) {
        var wrapper = new OmopModelWrapper();
        var appointmentLogicalId = fhirReferenceUtils.extractId(srcAppointment);
        if (Strings.isNullOrEmpty(appointmentLogicalId)){
            return null;
        }
        var appointmentStatus = srcAppointment.getStatusElement().getValueAsString();
        if (Strings.isNullOrEmpty(appointmentStatus)){
            return null;
        }
        var createdDateTime = srcAppointment.getCreated().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        var appointmentDateTime = srcAppointment.getStart().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        if (createdDateTime == null || appointmentDateTime == null){
            return null;
        }
        var appointment = org.miracum.etl.fhirtoomop.model.omop.Appointment.builder()
                .scheduledDateTime(appointmentDateTime)
                .createdDateTime(createdDateTime)
                .appointmentStatus(appointmentStatus)
                .fhirLogicalId(appointmentLogicalId).build();
        wrapper.setAppointment(appointment);
        return wrapper;
    }
}