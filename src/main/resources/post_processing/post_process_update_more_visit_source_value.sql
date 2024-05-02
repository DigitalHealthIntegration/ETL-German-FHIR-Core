DO
$$
DECLARE v_rowCount int;
BEGIN
update cds_cdm.visit_occurrence as vo
set more_visit_source_value = ppm.data_one
from cds_etl_helper.post_process_map ppm
where vo.fhir_logical_id = ppm.data_two ;
GET DIAGNOSTICS v_rowCount = ROW_COUNT;
RAISE NOTICE 'Updated % rows in provider.',v_rowCount;
END;
$$;