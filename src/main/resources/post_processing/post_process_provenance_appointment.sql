with qr_details as (
select * from cds_etl_helper.post_process_map ppm where "type" = 'QUESTIONNAIRERESPONSE' and data_one <> 'ENCOUNTER'
),
provenance_details as (
select * ,
split_part(data_two, ';', 2) as questionnaire_response_logical_id,
split_part(data_two, ';', 1) as appointment_logical_id
from cds_etl_helper.post_process_map ppm where "type" = 'PROVENANCE' and data_one = 'Appointment'
)

select * from qr_details qd, provenance_details pd
where pd.questionnaire_response_logical_id=qd.fhir_logical_id;