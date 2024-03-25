import argparse
import docker
import subprocess
import glob
import os
import requests
import boto3
import zipfile
import os
import shutil

def reset_database():
    # Implementation to reset the database
    print("Resetting the database...")

def run_etl(reset=False, with_hapi=False, synthetic_data_dir=None, synthea=False):
    # Implementation to run ETL process
    print("Running ETL process...")
    if(reset):
        print(f"Reset: {reset}")
        remove_docker_containers("C:/IPRD/MCP/ETL-German-FHIR-Core/deploy/docker-compose.yml")
        remove_docker_containers("docker-compose-hapi.yml")
        remove_docker_containers("docker-compose-synthea.yml")
        remove_docker_volume("fhir-to-omop_omop-postgress")
        remove_docker_volume("scripts_hapi-fhir-postgres")
        remove_folder("C:/IPRD/MCP/ETL-German-FHIR-Core/omop-vocab")
        delete_files_from_given_path("output\\fhir")
        delete_files_from_given_path("output\\metadata")
        download_vocab_from_s3()
        run_docker_compose("docker-compose-hapi.yml")
        run_docker_compose_with_logs("C:/IPRD/MCP/ETL-German-FHIR-Core/deploy/docker-compose.yml")
        return
    elif(with_hapi):
        print(f"With HAPI: {with_hapi}")
        run_docker_compose("docker-compose-hapi.yml")
        if(synthea):
            run_docker_compose_with_logs("docker-compose-synthea.yml")
            upload_synthea_data_to_hapi()
            print(f"Synthea: {synthea}")

        if(synthetic_data_dir):
            print(f"Synthetic Data Directory: {synthetic_data_dir}")
            upload_synthea_data_to_hapi(synthetic_data_dir)
        return
    elif(synthetic_data_dir):
        print(f"Synthetic Data Directory: {synthetic_data_dir}")
        return
    elif(synthea):
        print(f"Synthea: {synthea}")
        return
    print(f"Normal run")
    run_docker_compose_with_logs("C:/IPRD/MCP/ETL-German-FHIR-Core/deploy/docker-compose.yml")

def main():
    # Create an ArgumentParser object
    parser = argparse.ArgumentParser(description='ETL Script')

    # Add arguments
    parser.add_argument('action', choices=['run'], default='run', help='Action to perform')
    parser.add_argument('--reset', action='store_true', help='Reset the database')
    parser.add_argument('--with-hapi', action='store_true', help='Setup HAPI image')
    parser.add_argument('--synthetic-data-dir', help='Directory containing synthetic data')
    parser.add_argument('--synthea', action='store_true', help='Pull Synthea and upload data to HAPI server')

    # Parse the command-line arguments
    args = parser.parse_args()

    # Perform actions based on arguments
    if args.action == 'reset':
        reset_database()
    elif args.action == 'run':
        run_etl(reset=args.reset, with_hapi=args.with_hapi, synthetic_data_dir=args.synthetic_data_dir, synthea=args.synthea)

def run_docker_compose(yaml_path):
    try:
        # Run Docker Compose command in CMD
        with subprocess.Popen(f'docker-compose -f "{yaml_path}" up -d', shell=True) as process:
            process.wait()  # Wait for the process to complete
        print("Docker Compose executed successfully.")
    except subprocess.CalledProcessError as e:
        print("Error running Docker Compose command:", e)
    except KeyboardInterrupt:
        print("Process interrupted by the user.")
        # Perform any necessary cleanup here
        raise 
        
def run_docker_compose_with_logs(yaml_path):
    try:
        # Run Docker Compose command in CMD
        with subprocess.Popen(f'docker-compose -f "{yaml_path}" up', shell=True) as process:
            process.wait()  # Wait for the process to complete
        print("Docker Compose executed successfully.")
    except subprocess.CalledProcessError as e:
        print("Error running Docker Compose command:", e)
    except KeyboardInterrupt:
        print("Process interrupted by the user.")
        # Perform any necessary cleanup here
        raise 

def remove_docker_containers(yaml_path):
    try:
        # Run Docker Compose command in CMD
        with subprocess.Popen(f'docker-compose -f "{yaml_path}" down', shell=True) as process:
            process.wait()  # Wait for the process to complete
        print("Docker Compose down executed successfully.")
    except subprocess.CalledProcessError as e:
        print("Error running Docker Compose command:", e)
    except KeyboardInterrupt:
        print("Process interrupted by the user.")
        # Perform any necessary cleanup here
        raise 

def remove_docker_volume(volume_name):
    try:
        # Run Docker volume removal command in CMD
        subprocess.run(f'docker volume rm "{volume_name}"', check=True)
        print("Docker volume removed successfully.")
    except subprocess.CalledProcessError as e:
        print("Error removing Docker volume:", e)

def upload_synthea_data_to_hapi(file_path = "output\\fhir"):
    #fhir server endpoint
    URL = "http://localhost:8080/fhir/"

    #fhir server json header content
    headers = {"Content-Type": "application/fhir+json;charset=utf-8"}
    print("started running script")
    def process_and_upload_file(file_path):
        with open(file_path, "r", encoding="utf8") as bundle_file:         
                data = bundle_file.read()
                r = requests.post(url = URL, data = data.encode("utf-8"), headers = headers)
                #output file name that was processed
                print(file_path)

    script_directory = os.path.dirname(os.path.abspath(__file__))

    # Assuming the output/fhir directory is inside the parent directory
    
    if(os.path.isabs(file_path)):
        full_path = file_path
    else:
        full_path = os.path.join(script_directory, file_path)
    print(full_path)
    full_path = os.path.join(script_directory, relative_path)

    for dirpath, dirnames, files in os.walk(full_path):

        pattern = os.path.join(full_path, "hospitalInformation*.json")
        hospital_files = glob.glob(pattern)

        for file_path in hospital_files:
            process_and_upload_file(file_path)

        pattern = os.path.join(full_path, "practitionerInformation*.json")
        practitioner_files = glob.glob(pattern)

        for file_path in practitioner_files:
            process_and_upload_file(file_path)

        for file_name in files:
            file_path = os.path.join(full_path, file_name)
            process_and_upload_file(file_path)
        print("completed")  
            
def delete_files_from_given_path(folder_path):
    try:
        # List all files in the folder
        files = os.listdir(folder_path)
        
        # Iterate over each file and delete it
        for file_name in files:
            file_path = os.path.join(folder_path, file_name)
            if os.path.isfile(file_path):
                os.remove(file_path)
                print(f"Deleted file: {file_path}")
        
        print("All files in the folder have been deleted.")
    except Exception as e:
        print(f"Error deleting files: {e}")

def download_vocab_from_s3():
    try:
        # Initialize the S3 client
        print("downloading...")
        s3 = boto3.client('s3',aws_access_key_id='',aws_secret_access_key='', region_name = region_name)
        s3._request_signer.sign = (lambda *args, **kwargs: None)
        # Download the file from S3
        s3.download_file(bucket_name, object_name, local_file_path)
        print(f"File '{object_name}' downloaded from bucket '{bucket_name}' to '{local_file_path}'")
        with zipfile.ZipFile(local_file_path, 'r') as zip_ref:
            print("extracting....")
            zip_ref.extractall(os.path.dirname(local_file_path))
        
        print(f"File '{local_file_path}' unzipped successfully.")
    except Exception as e:
        print(f"Error downloading or unzipping file from S3: {e}")


def remove_folder(folder_path):
    if os.path.exists(folder_path):
        try:
            shutil.rmtree(folder_path)
            print(f"Folder '{folder_path}' deleted successfully.")
        except OSError as e:
            print(f"Error: {folder_path} : {e.strerror}")
    else:
        print(f"Error: Folder '{folder_path}' does not exist.")

bucket_name = "demo-ocl-image-store"  # Replace with your S3 bucket name
object_name = "ocl/omop-vocab.zip"  # Replace with the object (file) path in the bucket
local_file_path = "C:\\IPRD\\MCP\\ETL-German-FHIR-Core\\omop-vocab.zip"  # Replace with the local file path where you want to save the downloaded file
region_name = "us-east-1"


if __name__ == "__main__":
    main()
