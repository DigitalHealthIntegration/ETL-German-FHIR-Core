# ETL Automation Script

## Description

This python script automates ETL processess.

## Installation

  1. Install and start docker.

  2. Clone the repository:
     
     ```bash
     git clone https://github.com/DigitalHealthIntegration/ETL-German-FHIR-Core.git
     ```

  4. Change directory to deploy. eg: cd deploy.
  5. Rename sample.env to .env.
  6. Modify the following properties in .env file as per your setup.
      ````
      DATA_FHIRSERVER_BASEURL=http://localhost:8080/fhir 
      DATA_FHIRSERVER_USERNAME=username
      DATA_FHIRSERVER_PASSWORD=password
      DATA_FHIRSERVER_TOKEN=token
    
      // Use begin date and end date to restrict the amount of data fetched. It's currently set to a default value of 
      DATA_BEGINDATE=1800-01-01
      DATA_ENDDATE=2099-12-31
      ````
  7. Run `docker network create cloudbuild` . The important part here is to make sure the omop conversion application is on the same network as the fhir server to detect it. Ignore if network already exists.
  8. Navigate to the scripts directory:
     
     ```bash
     cd <path_to_project_dir>/automate/scripts
     ```
  9. Install the required dependencies:
        
     ```bash
     pip install -r requirements.txt  
     ```

## Basic steps To Use

  1. Download and set Vocabulary:
       - Use:
           ```bash
             python etl.py set --vocab 5.1
           ```

  2. Set up Hapi server:
       - Use:
           ```bash
             python etl.py set --hapi
           ```

  4. Download synthetic data and upload data to Hapi server
      - Only to download synthetic data use:
           ```bash
             python etl.py set --synthea
           ```
      
      - To download synthetic data and upload it to the hapi use: 
           ```bash
             python etl.py set --hapi --synthea
           ```
           
      - To upload synthetic data present in some directory to the hapi use: 
           ```bash
             python etl.py set --hapi --synthetic-data-dir <dir_path>
           ```
           
            
  5. Run ETL pipeline in Bulk Load Mode.
      - Only to run ETL pipeline use:
           ```bash
             python etl.py run
           ```
      
      - To run ETL pipeline with Hapi server and also download synthetic data in one command use:
           ```bash
             python etl.py run --hapi --synthea
           ```                 

      - To run ETL pipeline with Hapi server and also use synthetic data preset in given directory: 
           ```bash
             python etl.py run --hapi --synthetic-data-dir <dir_path>
           ```
           
  6. Run ETL pipeline in Incremetal Load Mode.
      - Only to run ETL pipeline use:
           ```bash
             python etl.py run
           ```

  7. To check logs of ETL pipeline.
     
      - Navigate to `deploy/logs` directory.
        
      - Open `fullLogs` to check full logs.
        
      - Open `summaryLogs` to check summary.
     
## Different Operations and Usage.
Run the script with the following command:
    
  ```bash
  python etl.py [positional arguments] [options]
  ```
### Positional Arguments

  1. run :
       - This Argument runs the ETL pipeline.

          ```bash
          python etl.py run [options]
          ```
  2. reset :
       - This argument resets the server database and vocabulary.

          ```bash
          python etl.py reset [options]
          ```
      
  3. set :
       - This argument used to set initial server and vocab.

          ```bash
          python etl.py set [options]
          ```
      
### Options

  1. --omop :
       - This option deletes the ETL database tables created by FHIR Resource when used with reset argument.

          ```bash
          python etl.py reset --omop
          ```
      
  2. --vocab <vocab_version> :
       - This option deletes old Vocabulary and resets Vocabulary with version passed when used with reset argument.

          ```bash
          python etl.py reset --vocab <vocab_version>
          ```
          
       - This sets Vocabulary with version passed when used with set argument.

          ```bash
          python etl.py set --vocab <vocab_version>
          ```
      
  3. --hapi :
       - This option is used to reset Hapi server when used with reset argument.

          ```bash
          python etl.py reset --hapi
          ```

       - This option is used to set Hapi server when used with set argument.

          ```bash
          python etl.py set --hapi
          ```

       - This option is used to run ETL with Hapi server when used with run argument.

          ```bash
          python etl.py run --hapi
          ```          

  4. --all :
       - This option deletes all server, database and synthetic data when used with reset argument.

          ```bash
          python etl.py reset --all
          ```
      
  5. --synthea :
       - This option is used to set Synthea server and download synthetic data when used with set argument.

          ```bash
          python etl.py set --synthea
          ```

       -  This option is used with `--hapi` to upload all downloaded synthetic data to Hapi server. used with set argument.

          ```bash
          python etl.py set --hapi --synthea
          ```

       -  This option is used with `--hapi` to upload all downloaded synthetic data to Hapi server and run ETL pipeline. used with run argument.

          ```bash
          python etl.py run --hapi --synthea
          ```

  6. --synthetic-data-dir <directory_path> :
       - This option is used to upload all synthetic data files present in the given directory_path to Hapi serever when used with set argument.

          ```bash
          python etl.py set --hapi --synthetic-data-dir <directory-path>
          ```

       - This option is used to upload all synthetic data files present in the give directory_path to Hapi serever and starts ETL pipeline when used with run argument. 

          ```bash
          python etl.py run --hapi --hapi --synthetic-data-dir <directory-path>
          ```

  7. --incremental-load :
       - This option runs ETL pipeline in incremental load.

          ```bash
          python etl.py run --incremental-load
          ```

