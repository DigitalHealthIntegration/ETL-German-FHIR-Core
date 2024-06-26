import argparse
import os
import etl_utils

def reset_etl(synthea=False,hapi=False,omop=False,vocab=None,all=False):
    if vocab:
        print("Reset VOCAB initiated...")
        etl_utils.remove_docker_containers("../../deploy/docker-compose-postgress.yml")
        etl_utils.remove_docker_containers("../../deploy/docker-compose-etl.yml")
        etl_utils.remove_docker_volume(OMOP_POSTGRESS_VOLUME_NAME)
        verify_vocab_and_download(vocab)
        print("Reset VOCAB completed...")
        return
    if omop:
        print("Reset OMOP initiated...")
        etl_utils.truncate_omop_tables()
        print("Reset OMOP completed...")
        return
    if hapi:
        print("Reset HAPI initiated...")
        etl_utils.truncate_omop_tables()
        etl_utils.remove_docker_containers(".././hapi/docker-compose-hapi.yml")
        etl_utils.remove_docker_volume(HAPI_DB_VOLUME_NAME)
        print("Reset HAPI completed...")
        return
    if all:
        print("Reset Everything initiated...")
        print(OMOP_POSTGRESS_VOLUME_NAME)
        etl_utils.remove_docker_containers("../../deploy/docker-compose-postgress.yml")
        etl_utils.remove_docker_containers("../../deploy/docker-compose-etl.yml")
        etl_utils.remove_docker_containers(".././hapi/docker-compose-hapi.yml")
        etl_utils.remove_docker_containers(".././synthea/docker-compose-synthea.yml")
        etl_utils.remove_docker_volume(OMOP_POSTGRESS_VOLUME_NAME)
        etl_utils.remove_docker_volume(HAPI_DB_VOLUME_NAME)
        etl_utils.remove_folder("../../omop-vocab")
        etl_utils.delete_files_from_given_path(".././synthea/output/fhir")
        etl_utils.delete_files_from_given_path(".././synthea/output/metadata")
        print("Reset Everything completed...")
        return
    if synthea:
        print("Reset Synthea initiated...")
        etl_utils.remove_docker_containers(".././synthea/docker-compose-synthea.yml")
        etl_utils.delete_files_from_given_path(".././synthea/output/fhir")
        etl_utils.delete_files_from_given_path(".././synthea/output/output/metadata")
        print("Reset Synthea completed...")    
    print(synthetic_data_dir)
    print(synthea)

def run_etl(hapi=False,synthetic_data_dir=None,synthea=False,vocab=None,incremental_load=False):
    print("Running ETL process...")
    if hapi:
        etl_utils.run_docker_compose(".././hapi/docker-compose-hapi.yml")
        if synthea:
            etl_utils.run_docker_compose_with_logs(".././synthea/docker-compose-synthea.yml")
            etl_utils.upload_synthea_data_to_hapi()
            print(f"Synthea: {synthea}")

        if synthetic_data_dir:
            print(f"Synthetic Data Directory: {synthetic_data_dir}")
            etl_utils.upload_synthea_data_to_hapi(synthetic_data_dir)
        # return
    elif synthetic_data_dir:
        print(f"Synthetic Data Directory: {synthetic_data_dir}")
        etl_utils.upload_synthea_data_to_hapi(synthetic_data_dir)
        return
    elif synthea:
        print(f"Synthea: {synthea}")
        return
    if vocab:
        print(f"{vocab}")
        verify_vocab_and_download(vocab)   
        etl_utils.run_etl_pipeline()
        print(f"v = {current_version}")
        print(f"hash = {current_hash}")
        return
    if incremental_load:
        print("incremental load")
        etl_utils.update_dotenv_file('false')
        etl_utils.run_etl_pipeline()
        return
    print(f"Normal run")
    print(vocab)
    etl_utils.update_dotenv_file('true')
    etl_utils.run_etl_pipeline()
    

def main():
    # Create an ArgumentParser object
    parser = argparse.ArgumentParser(description='ETL Automation Script')

    # Add arguments
    parser.add_argument('action', choices=['run','reset','set'], default='run', help='Action to perform')
    parser.add_argument('--synthetic-data-dir', help='Directory containing synthetic data')
    parser.add_argument('--synthea', action='store_true', help='Pull Synthea and upload data to HAPI server')
    parser.add_argument('--hapi', action='store_true', help='Resets Hapi server')
    parser.add_argument('--vocab', help='Resets Vocab')
    parser.add_argument('--omop', action='store_true', help='Resets omop')
    parser.add_argument('--all', action='store_true', help='Resets all')
    parser.add_argument('--with-incremental-load', action='store_true', help='Pull Synthea and upload data to HAPI server')
    

    # Parse the command-line arguments
    args = parser.parse_args()
    etl_utils.load_env_file()
    # Perform actions based on arguments
    if args.action == 'reset':
        reset_etl(  hapi=args.hapi,
                    synthea=args.synthea,
                    omop=args.omop,
                    vocab=args.vocab,
                    all=args.all    )
    elif args.action == 'run':
        run_etl(hapi=args.hapi, 
                synthetic_data_dir=args.synthetic_data_dir, 
                synthea=args.synthea,
                vocab=args.vocab,
                incremental_load=args.with_incremental_load)
    elif args.action == 'set':
        set_etl(vocab = args.vocab,
        hapi=args.hapi,
        synthea=args.synthea,
        synthetic_data_dir=args.synthetic_data_dir)

def set_etl(vocab=None,hapi=False,synthea=False,synthetic_data_dir=None):

    if vocab:
        verify_vocab_and_download(vocab)
        return
    if hapi:
        etl_utils.run_docker_compose(".././hapi/docker-compose-hapi.yml")
        if synthea:
            etl_utils.run_docker_compose_with_logs(".././synthea/docker-compose-synthea.yml")
            etl_utils.upload_synthea_data_to_hapi()
            print(f"Synthea: {synthea}")
        if synthetic_data_dir:
            print(f"Synthetic Data Directory: {synthetic_data_dir}")
            etl_utils.upload_synthea_data_to_hapi(synthetic_data_dir)
        return
    if synthea:
        etl_utils.run_docker_compose_with_logs(".././synthea/docker-compose-synthea.yml")


def verify_vocab_and_download(vocab_version):
    print("Verifying the VOCAB")
    current_version, current_hash = etl_utils.read_version_and_md5_hash_from_json("../../latest_version_hash.json")
    if current_version == vocab_version:
        download_hash_file = etl_utils.download_hash_from_s3(vocab_version)
        hash_from_s3 = etl_utils.read_file(f"../../{vocab_version}/md5_hash.txt")
        if current_hash == hash_from_s3:
            print("all ok")
        else:
            print("Download only VOCAB")
            etl_utils.download_latest_vocab_from_s3(vocab_version)
            hash_from_s3 = etl_utils.read_file(f"../../{vocab_version}/md5_hash.txt")
            etl_utils.update_json_file(vocab_version,hash_from_s3,"../../latest_version_hash.json")
            etl_utils.unzip_vocab(vocab_version)
            print("Download VOCAB completed")

    else:
        print("Download both VOCAB and hash")
        etl_utils.download_hash_from_s3(vocab_version)
        etl_utils.download_latest_vocab_from_s3(vocab_version)
        hash_from_s3 = etl_utils.read_file(f"../../{vocab_version}/md5_hash.txt")
        etl_utils.update_json_file(vocab_version,hash_from_s3,"../../latest_version_hash.json")
        latest_version, latest_hash = etl_utils.read_version_and_md5_hash_from_json("../../latest_version_hash.json")
        if latest_hash == hash_from_s3:
            etl_utils.unzip_vocab(vocab_version) 
        print("Download VOCAB and hash completed")

if __name__ == "__main__":
    OMOP_POSTGRESS_VOLUME_NAME = "fhir-to-omop_omop-postgress"
    HAPI_DB_VOLUME_NAME = "hapi_hapi-fhir-postgres"
    main()

