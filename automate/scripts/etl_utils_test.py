import unittest
import docker
from unittest.mock import patch, MagicMock, mock_open
from io import StringIO
from etl_utils import unzip_vocab,calculate_retry_delay,retrieve_load_status,update_json_file,read_file,download_hash_from_s3,read_version_and_md5_hash_from_json,remove_folder,remove_docker_volume,truncate_omop_tables,connect_to_database,download_from_s3,download_latest_vocab_from_s3,run_docker_compose, run_docker_compose_with_logs, remove_docker_containers,delete_files_from_given_path
import subprocess
import os
import coverage
import psycopg2
import json



class TestRunDockerCompose(unittest.TestCase):
    @patch('builtins.print')  # Mock the print function
    def test_run_docker_compose(self, mock_print):
        docker_client = docker.from_env()
        yaml_path = "docker-compose-hello-world.yaml"

        # Run the function
        run_docker_compose(yaml_path)

        # Check if the containers are running
        containers = docker_client.containers.list()
        self.assertTrue(containers)  # Assert that there are containers running
        for container in containers:
            print(f"container status {container.status}")
            self.assertEqual(container.status, "exited")  # Assert that each container is running
    
    @patch('sys.stdout', new_callable=StringIO)
    def test_run_docker_compose_with_logs(self, mock_stdout):
        docker_client = docker.from_env()
        yaml_path = "docker-compose-hello-world.yaml"

        # Run the function
        run_docker_compose_with_logs(yaml_path)

        # Check if the logs are printed
        printed_logs = mock_stdout.getvalue()

        # Define expected log message
        expected_log = "Docker Compose executed successfully.\n"

        # Assert expected log message
        self.assertIn(expected_log, printed_logs)

        # Check if the containers are running
        containers = docker_client.containers.list()
        # self.assertTrue(containers)  # Assert that there are containers running
        for container in containers:
            print(f"container status {container.status}")
            self.assertEqual(container.status, "exited")

    @patch('subprocess.Popen')
    @patch('sys.stdout', new_callable=StringIO)
    def test_run_docker_compose_error(self, mock_stdout, mock_popen):
        # Mock subprocess.Popen to raise CalledProcessError
        mock_popen.side_effect = subprocess.CalledProcessError(returncode=1, cmd='docker-compose')

        # Call the function
        run_docker_compose('docker-compose-hello-world.yaml')

        # Assert that the error message is printed
        expected_output = "Error running Docker Compose command: Command 'docker-compose' returned non-zero exit status 1."
        self.assertIn(expected_output, mock_stdout.getvalue())


    @patch('subprocess.Popen')
    @patch('sys.stdout', new_callable=StringIO)
    def test_run_docker_compose_with_logs_error(self, mock_stdout, mock_popen):
        # Mock subprocess.Popen to raise CalledProcessError
        mock_popen.side_effect = subprocess.CalledProcessError(returncode=1, cmd='docker-compose')
        # Call the function
        run_docker_compose_with_logs('docker-compose-hello-world.yaml')

        # Assert that the error message is printed
        expected_output = "Error running Docker Compose command: Command 'docker-compose' returned non-zero exit status 1."
        self.assertIn(expected_output, mock_stdout.getvalue())

    @patch('sys.stdout', new_callable=StringIO)
    def test_remove_docker_containers(self, mock_stdout):
        # Define the path to the Docker Compose YAML file
        yaml_path = "docker-compose-hello-world.yaml"

        # Run the function to remove Docker containers
        remove_docker_containers(yaml_path)

        # Create a Docker client
        docker_client = docker.from_env()

        # Get a list of containers after calling remove_docker_containers
        containers = docker_client.containers.list()
    
        # Assert that there are no containers running
        self.assertEqual(len(containers), 0)

        printed_logs = mock_stdout.getvalue()
        # Define expected log message
        expected_log = "Docker Compose down executed successfully.\n"

        # Assert expected log message
        self.assertIn(expected_log, printed_logs)

    @patch('subprocess.Popen')
    @patch('sys.stdout', new_callable=StringIO)
    def test_remove_docker_container_error(self, mock_stdout, mock_popen):
        # Mock subprocess.Popen to raise CalledProcessError
        mock_popen.side_effect = subprocess.CalledProcessError(returncode=1, cmd='docker-compose')
        # Call the function
        remove_docker_containers('docker-compose-hello-world.yaml')

        # Assert that the error message is printed
        expected_output = "Error running Docker Compose command: Command 'docker-compose' returned non-zero exit status 1."
        self.assertIn(expected_output, mock_stdout.getvalue())

    @patch('os.listdir')
    @patch('os.remove')
    @patch('builtins.print')
    @patch('os.path.isfile', return_value=True)
    def test_delete_files_success(self, mock_isfile, mock_print, mock_remove, mock_listdir):
        # Define folder path
        folder_path = "./"

        # Mock os.listdir to return a list of files
        mock_listdir.return_value = ["file1.txt", "file2.txt"]

        # Call the function
        delete_files_from_given_path(folder_path)

        # Assert that os.listdir is called with the correct folder path
        mock_listdir.assert_called_once_with(folder_path)

        # Assert that os.remove is called for each file
        mock_remove.assert_any_call(os.path.join(folder_path, "file1.txt"))
        mock_remove.assert_any_call(os.path.join(folder_path, "file2.txt"))

        # Assert that the correct print statements are called
        mock_print.assert_any_call("Deleted file: ./file1.txt")
        mock_print.assert_any_call("Deleted file: ./file2.txt")
        mock_print.assert_called_with("All files in the folder have been deleted.")

    @patch('os.listdir')
    @patch('builtins.print')
    @patch('os.path.isfile', return_value=True)
    def test_delete_files_exception_handling(self, mock_isfile, mock_print, mock_listdir):
        # Define folder path
        folder_path = "./path"

        # Mock os.listdir to raise an exception
        mock_listdir.side_effect = FileNotFoundError("Folder not found")

        # Call the function
        delete_files_from_given_path(folder_path)

        # Assert that the correct error message is printed
        mock_print.assert_called_once_with("Error deleting files: Folder not found")

    @patch('os.makedirs')
    @patch('etl_utils.download_from_s3')
    @patch('builtins.print')
    def test_download_latest_vocab_from_s3_success(self, mock_print, mock_download, mock_makedirs):
        # Define folder name
        folder_name = "vocabs"

        # Call the function
        download_latest_vocab_from_s3(folder_name)

        # Assert that os.makedirs is called with the correct folder path
        mock_makedirs.assert_called_once_with(f"../../{folder_name}")

        # Assert that download_from_s3 is called with the correct parameters
        mock_download.assert_called_once_with(
            "demo-ocl-image-store", 
            f"ocl/{folder_name}/omop-vocab.zip",
            f"../../{folder_name}/omop-vocab.zip",
            "us-east-1"
        )

        # Assert that the correct print statements are called
        mock_print.assert_any_call(f"Created folder: ../../{folder_name}")

    @patch('os.makedirs')
    @patch('etl_utils.download_from_s3')
    @patch('builtins.print')
    def test_download_latest_vocab_from_s3_exception_handling(self, mock_print, mock_download, mock_makedirs):
        # Define folder name
        folder_name = "vocabs"

        # Mock os.makedirs to raise an exception
        mock_makedirs.side_effect = FileNotFoundError("Folder not found")

        # Call the function
        download_latest_vocab_from_s3(folder_name)

        # Assert that the correct error message is printed
        mock_print.assert_called_once_with("Error downloading files from S3: Folder not found")


    @patch('boto3.client')
    @patch('builtins.print')
    def test_download_from_s3_success(self, mock_print, mock_boto3_client):
        # Define test data
        bucket_name = "test-bucket"
        object_name = "test-object"
        local_file_path = "/path/to/test/file.txt"
        region_name = "us-west-1"

        # Mock the S3 client and download_file method
        mock_s3_client = MagicMock()
        mock_boto3_client.return_value = mock_s3_client
        mock_s3_client.download_file.return_value = None

        # Call the function
        download_from_s3(bucket_name, object_name, local_file_path, region_name)

        # Assert that the S3 client is initialized with the correct arguments
        mock_boto3_client.assert_called_once_with('s3', region_name=region_name)

        # Assert that s3.download_file is called with the correct arguments
        mock_s3_client.download_file.assert_called_once_with(bucket_name, object_name, local_file_path)

        # Assert that the correct print statement is called
        expected_print_output = f"File '{object_name}' downloaded from bucket '{bucket_name}' to '{local_file_path}'"
        mock_print.assert_called_once_with(expected_print_output)

    @patch('boto3.client')
    @patch('builtins.print')
    def test_download_from_s3_exception_handling(self, mock_print, mock_boto3_client):
        # Define test data
        bucket_name = "test-bucket"
        object_name = "test-object"
        local_file_path = "/path/to/test/file.txt"
        region_name = "us-west-1"

        # Mock the S3 client and download_file method to raise an exception
        mock_s3_client = MagicMock()
        mock_boto3_client.return_value = mock_s3_client
        mock_s3_client.download_file.side_effect = Exception("Download error")

        # Call the function
        download_from_s3(bucket_name, object_name, local_file_path, region_name)

        # Assert that the correct error message is printed
        mock_print.assert_called_once_with("Error downloading file from S3: Download error")

    @patch('etl_utils.psycopg2.connect')
    @patch('builtins.print')
    def test_connect_success(self, mock_print, mock_connect):
        # Set up mock connection object
        mock_connection = MagicMock(name='connection_mock')
        mock_connect.return_value = mock_connection
        # Call the function
        result = connect_to_database()
        # Check if psycopg2.connect is called with correct parameters
        mock_connect.assert_called_once_with(user="ohdsi_admin_user",
                                             password="admin1",
                                             host="localhost",
                                             port="5412",
                                             database="ohdsi")
        # Check if the function returns the connection object
        self.assertEqual(result, mock_connection)
        # Check if "connected to database" message is printed
        mock_print.assert_called_once_with("connected to database")

    @patch('builtins.print')  # Mock the print function
    def test_remove_existing_volume(self, mock_print):
        # Mock the docker client and volume
        mock_volume = MagicMock()
        mock_volume_name = "test_volume"
        mock_volume.name = mock_volume_name
        mock_client = MagicMock()
        mock_client.volumes.get.return_value = mock_volume

        with patch('docker.from_env', return_value=mock_client):
            remove_docker_volume(mock_volume_name)

            # Assert that the volume's remove method was called
            mock_volume.remove.assert_called_once()

            # Assert that the success message was printed
            mock_print.assert_called_once_with(f"Docker volume '{mock_volume_name}' removed successfully.")
    
    @patch('builtins.print')  # Mock the print function
    def test_remove_nonexistent_volume(self, mock_print):
        # Mock the docker client to return None for the volume
        mock_client = MagicMock()
        mock_client.volumes.get.return_value = None

        with patch('docker.from_env', return_value=mock_client):
            remove_docker_volume("nonexistent_volume")

            # Assert that the error message was printed
            mock_print.assert_called_once_with("Error: Docker volume 'nonexistent_volume' not found.")

    @patch('builtins.print')  # Mock the print function
    def test_remove_volume_error(self, mock_print):
        # Mock the docker client to raise an APIError
        mock_client = MagicMock()
        mock_client.volumes.get.side_effect = docker.errors.APIError("Volume error")

        with patch('docker.from_env', return_value=mock_client):
            remove_docker_volume("test_volume")

            # Assert that the error message was printed
            mock_print.assert_called_once_with("Error removing Docker volume 'test_volume': Volume error")

    @patch('builtins.print')  # Mock the print function
    @patch('shutil.rmtree')  # Mock shutil.rmtree
    def test_remove_existing_folder(self, mock_rmtree, mock_print):
        # Mock shutil.rmtree to avoid actual removal
        folder_path = "test_folder"
        with patch('os.path.exists', return_value=True):
            remove_folder(folder_path)

            # Assert that shutil.rmtree was called with the correct folder path
            mock_rmtree.assert_called_once_with(folder_path)

            # Assert that the success message was printed
            mock_print.assert_called_once_with(f"Folder '{folder_path}' deleted successfully.")

    @patch('builtins.print')  # Mock the print function
    def test_remove_nonexistent_folder(self, mock_print):
        # Mock os.path.exists to return False
        folder_path = "nonexistent_folder"
        with patch('os.path.exists', return_value=False):
            remove_folder(folder_path)

            # Assert that the error message was printed
            mock_print.assert_called_once_with(f"Error: Folder '{folder_path}' does not exist.")

    @patch('builtins.print')  # Mock the print function
    @patch('shutil.rmtree', side_effect=OSError("None"))
    def test_remove_folder_error(self, mock_rmtree, mock_print):
        # Mock os.path.exists to return True
        folder_path = "test_folder"
        with patch('os.path.exists', return_value=True):
            remove_folder(folder_path)

            # Assert that the error message was printed
            mock_print.assert_called_once_with(f"Error: {folder_path} : None")

    def test_read_valid_json(self):
        # Define test data
        test_json_data = {
            "version": "1.0",
            "md5_hash": "abcdef1234567890"
        }
        # Mock the open function to return a file-like object with the test data
        with patch('builtins.open', mock_open(read_data=json.dumps(test_json_data))):
            # Call the function
            version, md5_hash = read_version_and_md5_hash_from_json('test.json')
            # Assert that the version and md5_hash are read correctly
            self.assertEqual(version, "1.0")
            self.assertEqual(md5_hash, "abcdef1234567890")

    def test_read_valid_json_missing_keys(self):
        # Define test data with missing keys
        test_json_data = {
            "version": "1.0"
            # "md5_hash": "abcdef1234567890" - md5_hash key is missing
        }
        # Mock the open function to return a file-like object with the test data
        with patch('builtins.open', mock_open(read_data=json.dumps(test_json_data))):
            # Call the function
            version, md5_hash = read_version_and_md5_hash_from_json('test.json')
            # Assert that the version and md5_hash are None
            self.assertEqual(version, "1.0")
            self.assertIsNone(md5_hash)

    def test_read_invalid_json(self):
        # Mock the open function to raise a JSONDecodeError
        with patch('builtins.open', side_effect=json.JSONDecodeError("Expecting value", "", 0)):
            # Call the function
            version, md5_hash = read_version_and_md5_hash_from_json('test.json')
            # Assert that the version and md5_hash are None
            self.assertIsNone(version)
            self.assertIsNone(md5_hash)

    @patch('builtins.print')  # Mock the print function
    def test_exception_handling(self, mock_print):
        # Mock the open function to raise an exception
        with patch('builtins.open', side_effect=Exception("File not found")):
            # Call the function
            version, md5_hash = read_version_and_md5_hash_from_json('test.json')
            # Assert that the error message was printed
            mock_print.assert_called_once_with("Error reading version and MD5 hash from JSON file: File not found")

    @patch('builtins.print')  # Mock the print function
    @patch('os.makedirs')  # Mock os.makedirs
    @patch('etl_utils.download_from_s3')  # Mock download_from_s3
    def test_download_hash_success(self, mock_download_from_s3, mock_makedirs, mock_print):
        # Mock folder name and path
        folder_name = "test_folder"
        folder_path = f"C:\\IPRD\\MCP\\ETL-German-FHIR-Core\\{folder_name}"
        
        # Mock os.path.exists to return False initially
        with patch('os.path.exists', return_value=False):
            download_hash_from_s3(folder_name)

            # Assert that os.makedirs was called with the correct folder path
            mock_makedirs.assert_called_once_with(folder_path)

            # Assert that the success message was printed
            mock_print.assert_called_once_with(f"Created folder: {folder_path}")

            # Assert that download_from_s3 was called with the correct arguments
            mock_download_from_s3.assert_called_once_with(
                "demo-ocl-image-store", 
                f"ocl/{folder_name}/md5_hash.txt",
                os.path.join(folder_path, "md5_hash.txt"),
                "us-east-1"
            )

    @patch('builtins.print')  # Mock the print function
    @patch('os.makedirs')  # Mock os.makedirs
    @patch('etl_utils.download_from_s3', side_effect=Exception("Download error"))  # Mock download_from_s3 to raise an exception
    def test_download_hash_error(self, mock_download_from_s3, mock_makedirs, mock_print):
        # Mock folder name and path
        folder_name = "test_folder"
        folder_path = f"C:\\IPRD\\MCP\\ETL-German-FHIR-Core\\{folder_name}"
        
        # Mock os.path.exists to return False initially
        with patch('os.path.exists', return_value=False):
            download_hash_from_s3(folder_name)

            # Assert that os.makedirs was called with the correct folder path
            mock_makedirs.assert_called_once_with(folder_path)

            # Assert that download_from_s3 was called with the correct arguments
            mock_download_from_s3.assert_called_once_with(
                "demo-ocl-image-store", 
                f"ocl/{folder_name}/md5_hash.txt",
                os.path.join(folder_path, "md5_hash.txt"),
                "us-east-1"
            )

    def test_read_existing_file(self):
        # Define test data
        file_path = "test.txt"
        file_content = "This is a test file."
        # Mock the open function to return a file-like object with the test data
        with patch('builtins.open', mock_open(read_data=file_content)):
            # Call the function
            result = read_file(file_path)
            # Assert that the file content is read correctly
            self.assertEqual(result, file_content)

    def test_read_nonexistent_file(self):
        # Mock the open function to raise a FileNotFoundError
        with patch('builtins.open', side_effect=FileNotFoundError("File not found")):
            # Call the function
            result = read_file("nonexistent_file.txt")
            # Assert that None is returned
            self.assertIsNone(result)

    @patch('builtins.print')  # Mock the print function
    def test_read_file_error(self, mock_print):
        # Mock the open function to raise an exception
        with patch('builtins.open', side_effect=Exception("File error")):
            # Call the function
            result = read_file("test.txt")
            # Assert that None is returned
            self.assertIsNone(result)
            # Assert that the error message was printed
            mock_print.assert_called_once_with("Error reading file: File error")

    def test_update_json(self):
        # Define test data
        file_path = "test.json"
        initial_data = {
            "version": "1.0",
            "md5_hash": "initial_md5_hash"
        }

        # Set up: Write initial data to a temporary JSON file
        with open(file_path, 'w') as json_file:
            json.dump(initial_data, json_file, indent=4)

        try:
            # Read and assert initial values from the JSON file
            with open(file_path, 'r') as json_file:
                initial_data = json.load(json_file)
                self.assertEqual(initial_data["version"], "1.0")
                self.assertEqual(initial_data["md5_hash"], "initial_md5_hash")

            # Update JSON file with new values
            updated_version = "2.0"
            updated_md5_hash = "updated_md5_hash"
            update_json_file(updated_version, updated_md5_hash, file_path)

            # Read and assert updated values from the JSON file
            with open(file_path, 'r') as json_file:
                updated_data = json.load(json_file)
                self.assertEqual(updated_data["version"], updated_version)
                self.assertEqual(updated_data["md5_hash"], updated_md5_hash)

        finally:
            # Teardown: Delete the temporary JSON file
            os.remove(file_path)

    def test_first_attempt(self):
        self.assertEqual(calculate_retry_delay(1), 1)

    def test_exponential_backoff(self):
        # Exponential backoff up to 60 seconds
        self.assertEqual(calculate_retry_delay(2), 2)
        self.assertEqual(calculate_retry_delay(3), 4)
        self.assertEqual(calculate_retry_delay(4), 8)
        self.assertEqual(calculate_retry_delay(5), 16)

    def test_max_delay(self):
        # After 5 attempts, should wait for 60 seconds
        self.assertEqual(calculate_retry_delay(6), 60)
        self.assertEqual(calculate_retry_delay(7), 60)
        self.assertEqual(calculate_retry_delay(8), 60)
        # Ensure it remains 60 after 5 attempts
        self.assertEqual(calculate_retry_delay(100), 60)
    
    @patch('etl_utils.os.makedirs')
    @patch('etl_utils.zipfile.ZipFile')
    @patch('builtins.print')
    def test_unzip_vocab_success(self, mock_print, mock_zipfile, mock_makedirs):
        folder_name = "test_folder"
        folder_path = f"C:\\IPRD\\MCP\\ETL-German-FHIR-Core\\{folder_name}"
        local_file_path = f"{folder_path}/omop-voacb.zip"
        unzip_at_path = "omop_vocab"

        # Mock os.path.exists to return False initially
        with patch('etl_utils.os.path.exists', return_value=False):
            unzip_vocab(folder_name)

            # Assert that os.makedirs was called with the correct folder path
            mock_makedirs.assert_called_once_with(unzip_at_path)

        # Mock the ZipFile object
        mock_zipfile.return_value.__enter__.return_value = mock_zipfile
        mock_zipfile.extractall.return_value = None

        # Call the function
        unzip_vocab(folder_name)

        # Assert that ZipFile was called with the correct file path
        mock_zipfile.assert_called_once_with(local_file_path, 'r')

        # Assert that extractall was called with the correct path
        mock_zipfile.extractall.assert_called_once_with(os.path.dirname(unzip_at_path))

        # Assert that the success message was printed
        mock_print.assert_called_with(f"File '{folder_name}.zip' unzipped successfully.")


if __name__ == '__main__':
    unittest.main()
