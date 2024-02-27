package org.miracum.etl.fhirtoomop.listeners;

import lombok.extern.slf4j.Slf4j;
import org.miracum.etl.fhirtoomop.DbMappings;
import org.miracum.etl.fhirtoomop.MemoryLogger;
import org.miracum.etl.fhirtoomop.repository.OmopRepository;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Slf4j
@Component
@CacheConfig(cacheManager = "")
public class QuestionnaireResponseStepListener implements StepExecutionListener {

    private final OmopRepository repositories;
    private final DbMappings dbMappings;
    private final Boolean bulkload;

    private static final MemoryLogger memoryLogger = new MemoryLogger();
    private Boolean dictionaryLoadInRam;

    @Value("${app.startSingleStep}")
    private String startSingleStep;

    @Autowired
    @Qualifier("writerDataSource")
    private final DataSource dataSource;

    @Autowired
    public QuestionnaireResponseStepListener(
            OmopRepository repositories,
            DbMappings dbMappings,
            Boolean dictionaryLoadInRam,
            Boolean bulkload,
            DataSource dataSource
    ){
        this.repositories = repositories;
        this.dbMappings = dbMappings;
        this.dictionaryLoadInRam = dictionaryLoadInRam;
        this.bulkload = bulkload;
        this.startSingleStep = startSingleStep;
        this.dataSource = dataSource;
    }
    @Override
    public void beforeStep(StepExecution stepExecution) {
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
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        memoryLogger.logMemoryDebugOnly();
        if (bulkload.equals(Boolean.TRUE)) {
            dbMappings.getFindIcdSnomedMapping().clear();
            dbMappings.getFindOrphaSnomedMapping().clear();
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
