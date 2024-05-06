DO
$$
DECLARE v_rowCount int;
BEGIN
update cds_cdm.appointment as app
set appointment_reason = app_reason_details.data_two
from (
	with qr_details as (
		select * from cds_etl_helper.post_process_map ppm where data_one = 'Appointment Reason'
	)
	select * from qr_details qr
) as app_reason_details
where app.questionnaire_response_id = app_reason_details.fhir_logical_id;
GET DIAGNOSTICS v_rowCount = ROW_COUNT;
RAISE NOTICE 'Updated % rows in appointment.',v_rowCount;
END;
$$;