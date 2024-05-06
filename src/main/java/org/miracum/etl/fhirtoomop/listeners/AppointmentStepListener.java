package org.miracum.etl.fhirtoomop.listeners;

import com.google.common.base.Strings;
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
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;
import javax.sql.DataSource;

@Slf4j
@Component
public class AppointmentStepListener implements StepExecutionListener {
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

    public AppointmentStepListener(
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

    @Override
    public void beforeStep(StepExecution stepExecution) {

        if (bulkload.equals(Boolean.TRUE)){
            repositories.getAppointmentRepository().truncateAppointmentTable();
            if (!Strings.isNullOrEmpty(startSingleStep)){
                StopWatch stopWatch = new StopWatch();
                stopWatch.start();
                stopWatch.stop();
            }
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
