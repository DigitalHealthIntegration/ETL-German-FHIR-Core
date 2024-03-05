package org.miracum.etl.fhirtoomop.repository;

import org.miracum.etl.fhirtoomop.model.omop.Appointment;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public interface AppointmentRepository extends PagingAndSortingRepository<Appointment, Long> {

    @Override
    List<Appointment> findAll();

    default Map<Long, Appointment> appointmentMap(){
        return findAll().stream().collect(Collectors.toMap(Appointment::getAppointmentId, v -> v));
    }

    @Transactional
    @Modifying
    @Query(value = "TRUNCATE TABLE cds_cdm.appointment CASCADE", nativeQuery = true)
    void truncateAppointmentTable();
}
