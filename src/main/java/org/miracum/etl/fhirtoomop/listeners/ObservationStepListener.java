package org.miracum.etl.fhirtoomop.listeners;

import static org.miracum.etl.fhirtoomop.Constants.VOCABULARY_ICD10GM;
import static org.miracum.etl.fhirtoomop.Constants.VOCABULARY_IPRD;
import static org.miracum.etl.fhirtoomop.Constants.VOCABULARY_LOINC;
import static org.miracum.etl.fhirtoomop.Constants.VOCABULARY_SNOMED;
import static org.miracum.etl.fhirtoomop.Constants.VOCABULARY_UCUM;
import static org.miracum.etl.fhirtoomop.Constants.VOCABULARY_WHO;

import com.google.common.base.Strings;
import java.io.IOException;
import java.sql.SQLException;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.miracum.etl.fhirtoomop.DbMappings;
import org.miracum.etl.fhirtoomop.MemoryLogger;
import org.miracum.etl.fhirtoomop.repository.OmopRepository;
import org.miracum.etl.fhirtoomop.utils.ExecuteSqlScripts;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

/**
 * The ObservationStepListener class describes activities to be performed before and after the
 * execution of the step for FHIR Observation resources.
 *
 * @author Elisa Henke
 * @author Yuan Peng
 */
@Slf4j
@Component
public class ObservationStepListener implements StepExecutionListener {
  private final OmopRepository repositories;
  private final DbMappings dbMappings;
  private static final MemoryLogger memoryLogger = new MemoryLogger();
  private Boolean dictionaryLoadInRam;
  private final Boolean bulkload;

  @Autowired
  @Qualifier("writerDataSource")
  private final DataSource dataSource;

  @Value("${app.startSingleStep}")
  private String startSingleStep;

  /**
   * Constructor for objects of the class ObservationStepListener.
   *
   * @param repositories OMOP CDM repositories
   * @param dbMappings collections for the intermediate storage of data from OMOP CDM in RAM
   * @param dictionaryLoadInRam parameter which indicates whether referenced data is searched in RAM
   *     or in OMOP CDM database
   * @param startSingleStep parameter which indicates which steps should be executed
   * @param bulkload flag to differentiate between bulk load or incremental load
   * @param dataSource connection data of target database
   */
  @Autowired
  public ObservationStepListener(
      OmopRepository repositories,
      DbMappings dbMappings,
      Boolean dictionaryLoadInRam,
      Boolean bulkload,
      DataSource dataSource) {
    this.repositories = repositories;
    this.dbMappings = dbMappings;
    this.dictionaryLoadInRam = dictionaryLoadInRam;
    this.bulkload = bulkload;
    this.dataSource = dataSource;
  }

  /**
   * Executes all activities which should take place before the step for FHIR Observation resources
   * is executed.
   *
   * @param stepExecution the execution of the step
   */
  @Override
  public void beforeStep(StepExecution stepExecution) {
    if (bulkload.equals(Boolean.TRUE)) {
      if (!Strings.isNullOrEmpty(startSingleStep)) {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        try {
          deleteAllLabData(stepExecution.createStepContribution());
        } catch (SQLException | IOException e) {
          e.printStackTrace();
          stepExecution.setTerminateOnly();
        }
        stopWatch.stop();

        log.info(
            "Cleaned up all laboratory data in [{}s]",
            String.format("%.3f", stopWatch.getTotalTimeSeconds()));
      }

      if (dictionaryLoadInRam.equals(Boolean.TRUE)) {
        dbMappings.setFindPersonIdByLogicalId(
            repositories.getPersonRepository().getFhirLogicalIdAndPersonId());
        dbMappings.setFindPersonIdByIdentifier(
            repositories.getPersonRepository().getFhirIdentifierAndPersonId());

        dbMappings.setFindVisitOccIdByLogicalId(
            repositories.getVisitOccRepository().getFhirLogicalIdAndVisitOccId());
        dbMappings.setFindVisitOccIdByIdentifier(
            repositories.getVisitOccRepository().getFhirIdentifierAndVisitOccId());
      }
      dbMappings.setFindLoincStandardMapping(
          repositories.getStandardRepository().getStandardMap());
      dbMappings
          .getOmopConceptMapWrapper()
          .setFindValidLoincConcept(
              repositories.getConceptRepository().findValidConceptId(VOCABULARY_LOINC));
      dbMappings
              .getOmopConceptMapWrapper()
              .setFindValidIPRDConcept(
                      repositories.getConceptRepository().findValidConceptId(VOCABULARY_IPRD));
      dbMappings
          .getOmopConceptMapWrapper()
          .setFindValidUcumConcept(
              repositories.getConceptRepository().findValidConceptId(VOCABULARY_UCUM));
      dbMappings
          .getOmopConceptMapWrapper()
          .setFindValidSnomedConcept(
              repositories.getConceptRepository().findValidConceptId(VOCABULARY_SNOMED));
      dbMappings.getOmopConceptMapWrapper().setFindValidWHOConcept(
              repositories.getConceptRepository().findValidConceptId(VOCABULARY_WHO)
      );
      dbMappings.getOmopConceptMapWrapper().setFindValidIcd10GmConcept(
              repositories.getConceptRepository().findValidConceptId(VOCABULARY_ICD10GM)
      );
    }
    dbMappings.setFindHardCodeConcept(
        repositories.getSourceToConceptRepository().sourceToConceptMap());
  }

  /**
   * Deletes all data from OMOP CDM that originates from FHIR Observation resources.
   *
   * @param contribution a contribution to a StepExecution
   * @throws IOException
   * @throws SQLException
   */
  private void deleteAllLabData(StepContribution contribution) throws SQLException, IOException {

    ExecuteSqlScripts executeSqlScripts = new ExecuteSqlScripts(dataSource, contribution);
    Resource deleteLaboratoryData =
        new ClassPathResource("single_step/single_step_clean_up_laboratory_data.sql");

    executeSqlScripts.executeSQLScript(deleteLaboratoryData);
  }

  /**
   * Executes all activities which should take place after the step for FHIR Observation resources
   * has been executed.
   *
   * @param stepExecution the execution of the step
   * @return status of the step execution
   */
  @Override
  public ExitStatus afterStep(StepExecution stepExecution) {
    memoryLogger.logMemoryDebugOnly();
    if (bulkload.equals(Boolean.TRUE)) {
      dbMappings.getOmopConceptMapWrapper().getFindValidLoincConcept().clear();
      dbMappings.getOmopConceptMapWrapper().getFindValidUcumConcept().clear();
      dbMappings.getOmopConceptMapWrapper().getFindValidSnomedConcept().clear();
      dbMappings.getFindLoincStandardMapping().clear();
      if (dictionaryLoadInRam.equals(Boolean.TRUE)) {
        dbMappings.getFindPersonIdByIdentifier().clear();
        dbMappings.getFindPersonIdByLogicalId().clear();
        dbMappings.getFindVisitOccIdByIdentifier().clear();
        dbMappings.getFindVisitOccIdByLogicalId().clear();
      }
    }
    dbMappings.getFindHardCodeConcept().clear();
    return ExitStatus.COMPLETED;
  }
}
