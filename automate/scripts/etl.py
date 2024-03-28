import argparse
import os
import etl_utils
import etl_constants

def reset_etl(with_hapi=False,synthetic_data_dir=None,synthea=False,hapi=False,omop=False,vocab=False,all=False):
    if vocab:
        print(vocab)
        etl_utils.remove_docker_containers("deploy/docker-compose.yml")
        etl_utils.remove_docker_volume(etl_constants.omop_postgress_volume_name)
        return
    print(synthetic_data_dir)
    print(synthea)

def run_etl(with_hapi=False,synthetic_data_dir=None,synthea=False):
    print("Running ETL process...")
    if(reset):
        print(f"Reset: {reset}")
        etl_utils.remove_docker_containers("C:/IPRD/MCP/ETL-German-FHIR-Core/deploy/docker-compose.yml")
        etl_utils.remove_docker_containers("docker-compose-hapi.yml")
        etl_utils.remove_docker_containers("docker-compose-synthea.yml")
        etl_utils.remove_docker_volume("fhir-to-omop_omop-postgress")
        etl_utils.remove_docker_volume("scripts_hapi-fhir-postgres")
        etl_utils.remove_folder("C:/IPRD/MCP/ETL-German-FHIR-Core/omop-vocab")
        etl_utils.delete_files_from_given_path("output\\fhir")
        etl_utils.delete_files_from_given_path("output\\metadata")
        etl_utils.download_vocab_from_s3()
        etl_utils.run_docker_compose("docker-compose-hapi.yml")
        etl_utils.run_docker_compose_with_logs("C:/IPRD/MCP/ETL-German-FHIR-Core/deploy/docker-compose.yml")
        return
    elif(with_hapi):
        print(f"With HAPI: {with_hapi}")
        etl_utils.run_docker_compose("docker-compose-hapi.yml")
        if(synthea):
            etl_utils.run_docker_compose_with_logs("docker-compose-synthea.yml")
            etl_utils.upload_synthea_data_to_hapi()
            print(f"Synthea: {synthea}")

        if(synthetic_data_dir):
            print(f"Synthetic Data Directory: {synthetic_data_dir}")
            etl_utils.upload_synthea_data_to_hapi(synthetic_data_dir)
        return
    elif(synthetic_data_dir):
        print(f"Synthetic Data Directory: {synthetic_data_dir}")
        return
    elif(synthea):
        print(f"Synthea: {synthea}")
        return
    print(f"Normal run")
    etl_utils.run_docker_compose_with_logs("C:/IPRD/MCP/ETL-German-FHIR-Core/deploy/docker-compose.yml")
    

def main():
    # Create an ArgumentParser object
    parser = argparse.ArgumentParser(description='ETL Script')

    # Add arguments
    parser.add_argument('action', choices=['run','reset'], default='run', help='Action to perform')
    parser.add_argument('--with-hapi', action='store_true', help='Setup HAPI image')
    parser.add_argument('--synthetic-data-dir', help='Directory containing synthetic data')
    parser.add_argument('--synthea', action='store_true', help='Pull Synthea and upload data to HAPI server')
    parser.add_argument('--hapi', action='store_true', help='Resets Hapi server')
    parser.add_argument('--vocab', action='store_true', help='Resets Vocab')
    parser.add_argument('--omop', action='store_true', help='Resets omop')
    parser.add_argument('--all', action='store_true', help='Resets all')

    # Parse the command-line arguments
    args = parser.parse_args()

    # Perform actions based on arguments
    if args.action == 'reset':
        reset_etl(  with_hapi=args.with_hapi,
                    synthetic_data_dir=args.synthetic_data_dir,
                    synthea=args.synthea,
                    hapi=args.hapi,
                    omop=args.omop,
                    vocab=args.vocab,
                    all=args.all    )
    elif args.action == 'run':
        run_etl(with_hapi=args.with_hapi, synthetic_data_dir=args.synthetic_data_dir, synthea=args.synthea)


if __name__ == "__main__":
    main()
