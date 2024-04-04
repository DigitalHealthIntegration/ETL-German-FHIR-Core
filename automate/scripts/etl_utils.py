import argparse
import docker
import subprocess
import glob
import os
import boto3
import zipfile
import os
import shutil
import etl_constants
import psycopg2
import json
import time

    
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
    client = docker.from_env()
    try:
        volume = client.volumes.get(volume_name)
        if volume:
            volume.remove()
            print(f"Docker volume '{volume_name}' removed successfully.")
        else:
            print(f"Error: Docker volume '{volume_name}' not found.")
    except docker.errors.APIError as e:
        print(f"Error removing Docker volume '{volume_name}': {e}")
    # try:
    #     # Run Docker volume removal command in CMD
    #     subprocess.run(f'docker volume rm "{volume_name}"', check=True)
    #     print("Docker volume removed successfully.")
    # except subprocess.CalledProcessError as e:
    #     print("Error removing Docker volume:", e)


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

# def download_vocab_from_s3():
#     bucket_name = "demo-ocl-image-store"  # Replace with your S3 bucket name
#     object_name = "ocl/omop-vocab.zip"  # Replace with the object (file) path in the bucket
#     local_file_path = "C:\\IPRD\\MCP\\ETL-German-FHIR-Core\\omop-vocab.zip"  # Replace with the local file path where you want to save the downloaded file
#     region_name = "us-east-1"
#     try:
#         # Initialize the S3 client
#         print("downloading...")
#         s3 = boto3.client('s3',aws_access_key_id='',aws_secret_access_key='', region_name = etl_constants.region_name)
#         s3._request_signer.sign = (lambda *args, **kwargs: None)
#         # Download the file from S3
#         s3.download_file(bucket_name, object_name, local_file_path)
#         print(f"File '{object_name}' downloaded from bucket '{bucket_name}' to '{local_file_path}'")
#         with zipfile.ZipFile(local_file_path, 'r') as zip_ref:
#             print("extracting....")
#             zip_ref.extractall(os.path.dirname(local_file_path))
        
#         print(f"File '{local_file_path}' unzipped successfully.")
#     except Exception as e:
#         print(f"Error downloading or unzipping file from S3: {e}")


def remove_folder(folder_path):
    # absolute_folder_path = os.path.join(etl_constants.current_directory,folder_path)
    if os.path.exists(folder_path):
        try:
            shutil.rmtree(folder_path)
            print(f"Folder '{folder_path}' deleted successfully.")
        except OSError as e:
            print(f"Error: {folder_path} : {e.strerror}")
    else:
        print(f"Error: Folder '{folder_path}' does not exist.")

def connect_to_database():
    try:
        connection = psycopg2.connect(user="ohdsi_admin_user",
                                          password="admin1",
                                          host="localhost",
                                          port="5412",
                                          database="ohdsi")
        print("connected to databse")
        return connection
    except (Exception, psycopg2.Error) as error:
        print("Error retrieving load status:", error)
        return None

def retrieve_load_status(connection):
    while True:
        try:
            # Create a cursor object to execute SQL queries
            cursor = connection.cursor()

            # Execute the SELECT query
            cursor.execute("SELECT * FROM cds_cdm.load_status;")

            # Fetch one row from the result set
            row = cursor.fetchone()

            # Check if the load status is "started"
            if row and row[0] == "started":
                print("Load status is 'started'.")
                cursor.close
                return True

        except (Exception, psycopg2.Error) as error:
            print("Error retrieving load status:", error)
            return False

def truncate_omop_tables():
    connection = connect_to_database()
    if connection:
        tables_to_truncate = ["cds_cdm.person"]  # List of tables to truncate
        truncate_tables(connection, tables_to_truncate)
        connection.close()
        print("Database connection closed.")

def truncate_tables(connection, tables):
    try:
        cursor = connection.cursor()
        for table in tables:
            cursor.execute(f"TRUNCATE TABLE {table} RESTART IDENTITY CASCADE;")
            print(f"Truncated table {table}")
        connection.commit()
        cursor.close()
    except (Exception, psycopg2.Error) as error:
        print("Error trauncating tables:", error)

def read_version_and_md5_hash_from_json(file_path):
    # local_file_path = os.path.join(etl_constants.current_directory, file_path)
    try:
        with open(file_path, 'r') as json_file:
            data = json.load(json_file)
            version = data.get("version")
            md5_hash = data.get("md5_hash")
            return version, md5_hash
    except Exception as e:
        print(f"Error reading version and MD5 hash from JSON file: {e}")
        return None, None

def download_hash_from_s3(folder_name):
    bucket_name = "demo-ocl-image-store"  # Replace with your S3 bucket name
    region_name = "us-east-1"
    try:
        folder_path = f"C:\\IPRD\\MCP\\ETL-German-FHIR-Core\\{folder_name}"
        if not os.path.exists(folder_path):
            os.makedirs(folder_path)
            print(f"Created folder: {folder_path}")
        
        #download md5_hash.txt
        object_name = f"ocl/{folder_name}/md5_hash.txt"
        local_file_path_md5 = os.path.join(folder_path, "md5_hash.txt")
        download_from_s3(bucket_name, object_name, local_file_path_md5, region_name)
    except Exception as e:
        print(f"Error downloading files from S3: {e}")

def read_file(file_path):
    try:
        with open(file_path, 'r') as file:
            file_content = file.read()
        return file_content
    except Exception as e:
        print(f"Error reading file: {e}")
        return None

def download_from_s3(bucket_name, object_name, local_file_path, region_name):
    try:
        # Initialize the S3 client
        s3 = boto3.client('s3', region_name=region_name)
        s3._request_signer.sign = (lambda *args, **kwargs: None)
        # Download the file from S3
        s3.download_file(bucket_name, object_name, local_file_path)
        print(f"File '{object_name}' downloaded from bucket '{bucket_name}' to '{local_file_path}'")
    except Exception as e:
        print(f"Error downloading file from S3: {e}")

def download_latest_vocab_from_s3(folder_name):
    bucket_name = "demo-ocl-image-store"  # Replace with your S3 bucket name
    region_name = "us-east-1"
    try:   
        folder_path = f"../../{folder_name}"
        if not os.path.exists(folder_path):
            os.makedirs(folder_path)
            print(f"Created folder: {folder_path}")

        # Download omop-vocab.zip
        object_name = f"ocl/{folder_name}/omop-vocab.zip"
        local_file_path_vocab = f"../../{folder_name}/omop-vocab.zip"
        download_from_s3(bucket_name, object_name, local_file_path_vocab, region_name)
    except Exception as e:
        print(f"Error downloading files from S3: {e}")

def update_json_file(version, md5_hash, file_path):
    # local_file_path = os.path.join(etl_constants.current_directory,file_path)
    try:
        # Read existing JSON data
        with open(file_path, 'r') as json_file:
            data = json.load(json_file)
        
        # Update version and MD5 hash
        data["version"] = version
        data["md5_hash"] = md5_hash
        
        # Write updated data back to the file
        with open(file_path, 'w') as json_file:
            json.dump(data, json_file, indent=4)
        
        print(f"JSON file '{file_path}' updated successfully.")
    except Exception as e:
        print(f"Error updating JSON file: {e}")

def unzip_vocab(folder_name):
    zip_local_file_path = os.path.join(etl_constants.current_directory,f"{folder_name}/omop-voacb.zip")
    unzip_at_path = os.path.join(etl_constants.current_directory,"omop_vocab")
    if not os.path.exists(unzip_at_path):
            os.makedirs(unzip_at_path)
            print(f"Created folder: {unzip_at_path}")
    with zipfile.ZipFile(local_file_path, 'r') as zip_ref:
        zip_ref.extractall(os.path.dirname(unzip_at_path))
        print(f"File '{file_name}.zip' unzipped successfully.")

def calculate_retry_delay(attempt):
    if attempt <= 5:
        return min(2 ** attempt if attempt > 1 else 30, 90)
    else:
        return 60 # 2 minutes in milliseconds

def extract_logs(input_file, output_file):
    try:
        with open(input_file, 'r') as f:
            lines = f.readlines()

        start_marker = "==== Summary ===="
        end_marker = "==== Job End ===="

        start_index = None
        end_index = None

        for i, line in enumerate(lines):
            if start_marker in line:
                start_index = i
            elif end_marker in line:
                end_index = i
                break

        if start_index is not None and end_index is not None:
            extracted_lines = []

            for line in lines[start_index:end_index + 1]:
                parts = line.split("FhirToOmopJobListener", 1)
                if len(parts) > 1:
                    cleaned_line = parts[1].strip()
                    extracted_lines.append(cleaned_line + '\n')

            with open(output_file, 'w') as f:
                f.writelines(extracted_lines)
            
            print(f"Extracted logs and saved to '{output_file}'")
        else:
            print("Start or end marker not found.")

    except Exception as e:
        print(f"Error extracting logs: {e}")

def download_container_logs(yaml_path):
    try:
        # Run Docker Compose command in CMD
        with subprocess.Popen(f'docker-compose -f "{yaml_path}" logs ohdsi-germany > .././etl_report/full_report.txt', shell=True) as process:
            process.wait()  # Wait for the process to complete
        print("Docker logs downloaded ")
    except subprocess.CalledProcessError as e:
        print("Error running Docker Compose command:", e)
    except KeyboardInterrupt:
        print("Process interrupted by the user.")
        # Perform any necessary cleanup here
        raise

def run_etl_pipeline():
    connection = None
    retry_attempts = 0
    run_docker_compose("../../deploy/docker-compose-postgress.yml")
    while True:
        if not connection:
            connection = connect_to_database()
            if connection:
                if retrieve_load_status(connection):
                    run_docker_compose("../../deploy/docker-compose-etl.yml")
                    download_container_logs("../../deploy/docker-compose-etl.yml")
                    extract_logs(".././etl_report/full_report.txt",".././etl_report/summary_report.txt")
                    break
            retry_attempts += 1
            retry_delay = calculate_retry_delay(retry_attempts)
            print(f"Retry attempt {retry_attempts}. Waiting for {retry_delay} seconds before retrying...")
            time.sleep(retry_delay)
    if connection:
        connection.close()
        print("database connection closed")