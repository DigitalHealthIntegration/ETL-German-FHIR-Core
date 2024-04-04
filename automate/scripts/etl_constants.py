bucket_name = "demo-ocl-image-store"  # Replace with your S3 bucket name
object_name = "ocl/omop-vocab.zip"  # Replace with the object (file) path in the bucket
local_file_path = "C:\\IPRD\\MCP\\ETL-German-FHIR-Core\\omop-vocab.zip"  # Replace with the local file path where you want to save the downloaded file
region_name = "us-east-1"
current_directory = "C:/IPRD/MCP/ETL-German-FHIR-Core"
omop_postgress_volume_name = "fhir-to-omop_omop-postgress"
tables_to_truncate = ["cds_cdm.person","cds_cdm.observation","cds_cdm.condition_occurrence","cds_cdm.measurement","cds_cdm.procedure_occurrence","cds_cdm.drug_exposure"]
hapi_db_volume_name= "hapi_hapi-fhir-postgres"