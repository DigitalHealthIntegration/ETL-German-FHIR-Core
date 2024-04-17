# ETL Automation Script

## Description

This python script automates ETL processess

## Installation

  1. Install and start docker

  2. Clone the repository:
     
     ```bash
     git clone https://github.com/DigitalHealthIntegration/ETL-German-FHIR-Core.git
     ```
  3. Navigate to the scripts directory:
     
     ```bash
     cd <path_to_project_dir>/automate/scripts
     ```
  4. Install the required dependencies:
        
     ```bash
     pip install -r requirements.txt
     ```
     
## Usage
Run the script with the following command:
    
  ```bash
  python etl.py [positional arguments] [options]
  ```
### Positional Arguments

  1. run :
       - This Argument runs the ETL pipeline

          ```bash
          python etl.py run [options]
          ```
  2. reset :
       - This argument resets the server database and vocabulary

          ```bash
          python etl.py run [options]
          ```
      
  3. set :
       - This argument used to set initial server and vocab

          ```bash
          python etl.py run [options]
          ```
      
### Options

  1. --omop :
       - This option deletes the ETL database tables created by FHIR Resource when used with reset argument

          ```bash
          python etl.py reset --omop
          ```
      
  2. --vocab <vocab_version> :
       - This option deletes old Vocabulary and resets Vocabulary with version passed when used with reset argument

          ```bash
          python etl.py reset --vocab <vocab_version>
          ```
          
       - This sets Vocabulary with version passed when used with set argument

          ```bash
          python etl.py set --vocab <vocab_version>
          ```
      
  3. --hapi :
       - This option is used to reset Hapi server when used with reset argument

          ```bash
          python etl.py run [options]
          ```

       - This option is used to set Hapi server when used with set argument

          ```bash
          python etl.py set --vocab <vocab_version>
          ```

       - This option is used to run ETL with Hapi server when used with run argument

          ```bash
          python etl.py set --vocab <vocab_version>
          ```          

  4. --all :
       - This option deletes all server, database and synthetic data when used with reset argument

          ```bash
          python etl.py reset --all
          ```
      
  5. --synthea :
       - This option is used to set Synthea server and download synthetic data when used with set argument

          ```bash
          python etl.py set --synthea
          ```

       -  This option is used with `--hapi` to upload all downloaded synthetic data to Hapi server. used with set argument

          ```bash
          python etl.py set --hapi --synthea
          ```

       -  This option is used with `--hapi` to upload all downloaded synthetic data to Hapi server and run ETL pipeline. used with run argument

          ```bash
          python etl.py run --hapi --synthea
          ```

  6. --synthetic-data-dir <directory_path> :
       - This option is used to upload all synthetic data files present in the given directory_path to Hapi serever when used with set argument 

          ```bash
          python etl.py set --hapi --synthetic-data-dir <directory-path>
          ```

       - This option is used to upload all synthetic data files present in the give directory_path to Hapi serever and starts ETL pipeline when used with run argument 

          ```bash
          python etl.py run --hapi --hapi --synthetic-data-dir <directory-path>
          ```

  7. --incremental-load :
       - This option runs ETL pipeline in incremental load 

          ```bash
          python etl.py run --incremental-load
          ```


          

