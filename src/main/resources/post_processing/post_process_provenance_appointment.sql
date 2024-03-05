update cds_cdm.appointment as app
set visit_occurrence_id = app_prov_details.visit_occurrence_id,
person_id = app_prov_details.person_id,
care_site_id = app_prov_details.care_site_id
	from (
		with qr_details as (
			select * from cds_etl_helper.post_process_map ppm where "type" = 'QUESTIONNAIRERESPONSE' and data_one <> 'ENCOUNTER'
		),
		provenance_details as (
			select * ,
			split_part(data_two, ';', 2) as questionnaire_response_logical_id,
			split_part(data_two, ';', 1) as appointment_logical_id
			from cds_etl_helper.post_process_map ppm where "type" = 'PROVENANCE' and data_one = 'Appointment'
		)
		select vo.visit_occurrence_id , vo.person_id, vo.care_site_id, appointment_logical_id  from qr_details qd, provenance_details pd, cds_cdm.visit_occurrence vo, cds_cdm.appointment app
		where pd.questionnaire_response_logical_id=qd.fhir_logical_id and qd.data_two = vo.fhir_logical_id
	) as app_prov_details
where app.fhir_logical_id = app_prov_details.appointment_logical_id;