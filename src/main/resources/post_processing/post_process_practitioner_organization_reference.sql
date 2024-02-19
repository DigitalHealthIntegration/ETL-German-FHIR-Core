DO
$$
DECLARE v_rowCount int;
BEGIN
WITH practitioner_organization_reference AS
(
    SELECT
        ppm.data_one AS practitioner_logical_id,
        ppm.data_two AS organization_logical_id,
        cs.care_site_id AS care_site_id
    FROM
        cds_etl_helper.post_process_map ppm,
        cds_cdm.care_site cs
    WHERE
        cs.fhir_logical_id = ppm.data_two AND
        ppm = 'PRACTITIONERROLE'
)
UPDATE cds_cdm.provider AS p
SET p.care_site_id = por.care_site_id
FROM practitioner_organization_reference por
WHERE p.fhir_logical_id = por.practitioner_logical_id
;
GET DIAGNOSTICS v_rowCount = ROW_COUNT;
RAISE NOTICE 'Updated % rows in provider.',v_rowCount;
END;
$$;