package org.miracum.etl.fhirtoomop.model;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.miracum.etl.fhirtoomop.model.omop.*;

/**
 * The OmopModelWrapper class serves as a cache of newly created records, which are to be written to
 * OMOP CDM.
 *
 * @author Elisa Henke
 * @author Yuan Peng
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OmopModelWrapper {
  private Person person;
  private VisitOccurrence visitOccurrence;
  private List<ConditionOccurrence> conditionOccurrence = new ArrayList<>();
  private List<ProcedureOccurrence> procedureOccurrence = new ArrayList<>();
  private List<DrugExposure> drugExposure = new ArrayList<>();
  private List<Measurement> measurement = new ArrayList<>();
  private List<OmopObservation> observation = new ArrayList<>();
  private List<Provider> provider = new ArrayList<>();
  private List<VisitDetail> visitDetail = new ArrayList<>();
  private List<DeviceExposure> deviceExposure = new ArrayList<>();

  private List<MedicationIdMap> medicationIdMap = new ArrayList<>();
  private List<PostProcessMap> postProcessMap = new ArrayList<>();

  /**
   * Enumeration of all OMOP CDM table names which can be filled by the ETL process.
   *
   * @author Elisa Henke
   * @author Yuan Peng
   */
  public enum Tablename {
    PERSON("person"),
    LOCATION("location"),
    DEATH("death"),
    VISITOCCURRENCE("visit_occurrence"),
    OBSERVATIONPERIOD("observation_period"),
    VISITDETAIL("visit_detail"),
    CONDITIONOCCURRENCE("condition_occurrence"),
    FACTRELATIONSHIP("fact_relationship"),
    OBSERVATIONOMOP("observation"),
    MEASUREMENT("measurement"),
    PROCEDUREOCCURRENCE("procedure_occurrence"),
    DEVICEEXPOSURE("device_exposure"),
    DRUGEXPOSURE("drug_exposure"),
    PROVIDER("provider");
    private final String label;

    /**
     * Constructor for objects of the enumeration Tablename.
     *
     * @param label a OMOP CDM table name
     */
    Tablename(String label) {
      this.label = label;
    }

    /**
     * Returns a OMOP CDM table name.
     *
     * @return a OMOP CDM table name
     */
    public String getTableName() {
      return label;
    }
  }
}
