/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.spreadsheetimport;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.openmrs.Attributable;
import org.openmrs.Encounter;
import org.openmrs.GlobalProperty;
import org.openmrs.Location;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType;
import org.openmrs.PatientProgram;
import org.openmrs.PersonAddress;
import org.openmrs.PersonAttribute;
import org.openmrs.PersonAttributeType;
import org.openmrs.PersonName;
import org.openmrs.api.APIException;
import org.openmrs.api.ConceptService;
import org.openmrs.api.context.Context;
import org.openmrs.module.idgen.service.IdentifierSourceService;
import org.openmrs.util.PrivilegeConstants;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;

/**
 *
 */
public class SpreadsheetImportUtil {
	
	/** Logger for this class and subclasses */
	protected static final Log log = LogFactory.getLog(SpreadsheetImportUtil.class);
	public static final String COVID_QUARANTINE_ENROLLMENT_ENCOUNTER = "33a3a55c-73ae-11ea-bc55-0242ac130003";
	public static final String COVID_QUARANTINE_ENROLLMENT_FORM = "9a5d57b6-739a-11ea-bc55-0242ac130003";
	public static final String COVID_QUARANTINE_PROGRAM = "9a5d555e-739a-11ea-bc55-0242ac130003";
    public static final String COVID_19_TRAVEL_HISTORY_ENCOUNTER = "50a59411-921b-435a-9109-42aa68ee7aa7";
    public static final String COVID_19_TRAVEL_HISTORY_FORM = "87513b50-6ced-11ea-bc55-0242ac130003";





    /**
	 * Resolve template dependencies: 1. Generate pre-specified values which are necessary for
	 * template to be imported. 2. Create import indices which describe the order in which columns
	 * must be imported. 3. Generated dependencies between columns being imported and other columns
	 * which be must imported first.
	 * 
	 * @param template
	 * @throws Exception
	 */
	public static void resolveTemplateDependencies(SpreadsheetImportTemplate template) throws Exception {
		
		Set<SpreadsheetImportTemplatePrespecifiedValue> prespecifiedValues = new TreeSet<SpreadsheetImportTemplatePrespecifiedValue>();
		
		Map<String, Set<UniqueImport>> mapTnToUi = template.getMapOfColumnTablesToUniqueImportSet();
		Map<UniqueImport, Set<SpreadsheetImportTemplateColumn>> mapUiToCs = template.getMapOfUniqueImportToColumnSet();
		
		List<String> tableNamesSortedByImportIdx = new ArrayList<String>();
		
//		// special treatment: when there's a reference to person_id, but 
//		//  1) the current table is not encounter and 
//		//  2) there's no column of table person to be added
//		// then we should still add a person implicitly. This person record will use all default values
//		boolean hasToAddPerson = false;
//		for (UniqueImport key : mapUiToCs.keySet()) {
//			String tableName = key.getTableName();			
//			if (!("encounter".equals(tableName) || mapTnToUi.keySet().contains("person"))) {
//				hasToAddPerson = true;
//				break;
//			}
//		}
//		if (hasToAddPerson) {
//			UniqueImport ui = new UniqueImport("person", new Integer(-1));
//			mapTnToUi.put("person", new TreeSet<UniqueImport>());
//			mapUiToCs.put(ui, new TreeSet<SpreadsheetImportTemplateColumn>());
//		}
				
		// Find requirements
		for (UniqueImport key : mapUiToCs.keySet()) {
			String tableName = key.getTableName();
			
			Map<String, String> mapIkTnToCn = DatabaseBackend.getMapOfImportedKeyTableNameToColumnNamesForTable(tableName);
			
			if ("patient_identifier".equals(tableName))
				mapIkTnToCn.put("patient", "patient_id");
			
			// encounter_id is optional, so it won't be part of mapIkTnToCn
			// if we need to create new encounter for this row, then force it to be here
			if (template.isEncounter() && "obs".equals(tableName))
				mapIkTnToCn.put("encounter", "encounter_id");
			
			// we need special treatment for provider_id of Encounter
			// provider_id is of type person, but the meaning is different. During import, reference to person is considered patient,
			// but for provider_id of Encounter, it refers to a health practitioner
			if ("encounter".equals(tableName)) {
//				mapIkTnToCn.put("person", "provider_id"); 			// UPDATE: provider_id is no longer a foreign key for encounter
				mapIkTnToCn.put("location", "location_id");
				mapIkTnToCn.put("form", "form_id");
				
//				// if this is an encounter-based import, then pre-specify the form_id for the encounter
//				// 1. search for encounter column
//				SpreadsheetImportTemplateColumn encounterColumn = mapUiToCs.get(key).iterator().next();
//				// 2. prespecify form 				
//				SpreadsheetImportTemplatePrespecifiedValue v = new SpreadsheetImportTemplatePrespecifiedValue();
//				v.setTemplate(template);
//				v.setTableDotColumn("form.form_id");
//				v.setValue(template.getTargetForm());
//				SpreadsheetImportTemplateColumnPrespecifiedValue cpv = new SpreadsheetImportTemplateColumnPrespecifiedValue();
//				cpv.setColumn(encounterColumn);
//				cpv.setPrespecifiedValue(v);
//				prespecifiedValues.add(v);
			}
			
			// Ignore users tableName 
			mapIkTnToCn.remove("users");
			
			for (String necessaryTableName : mapIkTnToCn.keySet()) {

				String necessaryColumnName = mapIkTnToCn.get(necessaryTableName);

				// TODO: I believe patient and person are only tables with this relationship, if not, then this
				// needs to be generalized
				if (necessaryTableName.equals("patient") &&
					!mapTnToUi.containsKey("patient") &&
					mapTnToUi.containsKey("person")) {
					necessaryTableName = "person";
				}
				
				if (mapTnToUi.containsKey(necessaryTableName) && !("encounter".equals(tableName) && ("provider_id".equals(necessaryColumnName)))) {
					
					// Not already imported? Add
					if (!tableNamesSortedByImportIdx.contains(necessaryTableName)) {
						tableNamesSortedByImportIdx.add(necessaryTableName);
					}
					
					// Add column dependencies
					// TODO: really _table_ dependencies - for simplicity only use _first_ column
					// of each unique import
					Set<SpreadsheetImportTemplateColumn> columnsImportFirst = new TreeSet<SpreadsheetImportTemplateColumn>();
					for (UniqueImport uniqueImport : mapTnToUi.get(necessaryTableName)) {
						// TODO: hacky cast
						columnsImportFirst.add(((TreeSet<SpreadsheetImportTemplateColumn>)mapUiToCs.get(uniqueImport)).first());
					}
					for (SpreadsheetImportTemplateColumn columnImportNext : mapUiToCs.get(key)) {
						for (SpreadsheetImportTemplateColumn columnImportFirst : columnsImportFirst) {
							SpreadsheetImportTemplateColumnColumn cc = new SpreadsheetImportTemplateColumnColumn();
							cc.setColumnImportFirst(columnImportFirst);
							cc.setColumnImportNext(columnImportNext);
							cc.setColumnName(necessaryColumnName);
							columnImportNext.getColumnColumnsImportBefore().add(cc);
						}
					}
					
				} else {
					
					// Add pre-specified value
					SpreadsheetImportTemplatePrespecifiedValue v = new SpreadsheetImportTemplatePrespecifiedValue();
					v.setTemplate(template);
					v.setTableDotColumn(necessaryTableName + "." + necessaryTableName + "_id");
					for (SpreadsheetImportTemplateColumn column : mapUiToCs.get(key)) {
						SpreadsheetImportTemplateColumnPrespecifiedValue cpv = new SpreadsheetImportTemplateColumnPrespecifiedValue();
						cpv.setColumn(column);
						cpv.setPrespecifiedValue(v);
						
						
//						System.out.println("SpreadsheetImportUtils: " + v.getTableDotColumn() + " ==> " + v.getValue());
						
						cpv.setColumnName(necessaryColumnName);						
						v.getColumnPrespecifiedValues().add(cpv);
					}
					prespecifiedValues.add(v);
				}
			}
			
			// Add this tableName if not already added
			if (!tableNamesSortedByImportIdx.contains(tableName)) {
				tableNamesSortedByImportIdx.add(tableName);
			}
		}
		
		// Add all pre-specified values		
		template.getPrespecifiedValues().addAll(prespecifiedValues);
		
		// Set column import indices based on tableNameSortedByImportIdx
		int importIdx = 0;
		for (String tableName : tableNamesSortedByImportIdx) {
			for (UniqueImport uniqueImport : mapTnToUi.get(tableName)) {
				for (SpreadsheetImportTemplateColumn column : mapUiToCs.get(uniqueImport)) {
					column.setImportIdx(importIdx);
					importIdx++;
				}
			}
		}
	}
	
	private static String toString(List<String> list) {
		String result = "";
		for (int i = 0; i < list.size(); i++) {
			if (list.size() == 2 && i == 1) {
				result += " and ";
			} else if (list.size() > 2 && i == list.size() - 1) {
				result += ", and ";
			} else if (i != 0) {
				result += ", ";
			}
			result += list.get(i);
		}
		return result;
	}
	
	public static File importTemplate(SpreadsheetImportTemplate template, MultipartFile file, String sheetName,
	                                     List<String> messages, boolean rollbackTransaction) throws Exception {

		if (file.isEmpty()) {
			messages.add("file must not be empty");
			return null;
		}
		
		// Open file
		Workbook wb = WorkbookFactory.create(file.getInputStream());
		Sheet sheet;
		if (!StringUtils.hasText(sheetName)) {
			sheet = wb.getSheetAt(0);
		} else {
			sheet = wb.getSheet(sheetName);
		}
		
		// Header row
		Row firstRow = sheet.getRow(0);
		if (firstRow == null) {
			messages.add("Spreadsheet header row must not be null");
			return null;
		}
		
		List<String> columnNames = new Vector<String>();
		for (Cell cell : firstRow) {
			columnNames.add(cell.getStringCellValue());
		}
		if (log.isDebugEnabled()) {
			log.debug("Column names: " + columnNames.toString());
		}
		
		// Required column names
		List<String> columnNamesOnlyInTemplate = new Vector<String>();
		columnNamesOnlyInTemplate.addAll(template.getColumnNamesAsList());
		columnNamesOnlyInTemplate.removeAll(columnNames);
		/*if (columnNamesOnlyInTemplate.isEmpty() == false) {
			messages.add("required column names not present: " + toString(columnNamesOnlyInTemplate));
			return null;
		}*/
		
		// Extra column names?
		List<String> columnNamesOnlyInSheet = new Vector<String>();
		columnNamesOnlyInSheet.addAll(columnNames);
		columnNamesOnlyInSheet.removeAll(template.getColumnNamesAsList());
		if (columnNamesOnlyInSheet.isEmpty() == false) {
			messages.add("Extra column names present, these will not be processed: " + toString(columnNamesOnlyInSheet));
		}
		
		// Process rows
		importQuarantineList(sheet);

		
		// write back Excel file to a temp location
		File returnFile = File.createTempFile("sim", ".xls");
		FileOutputStream fos = new FileOutputStream(returnFile);
		//wb.write(fos);
		fos.close();
		
		return returnFile;
	}

	private static void importQuarantineList(Sheet sheet) {

		boolean start = true;
		int counter = 0;
		for (Row row : sheet) {
			if (start) {
				start = false;
				continue;
			}
			int colFacilityName = 1;
			int colClientName = 2;
			int colAge = 4;
			int colSex = 6;
			int colNationalId = 7;
			int colPhone = 8;
			int colCountryofOrigin = 9;
			int colTravelingFrom = 10;
			int colNationality = 11;
			int colNoKName = 15;
			//int colNoKName = 13;
			int colNoKContact = 16;
			//int colNoKContact = 14;
			int colArrivalDate = 17;
			//int colArrivalDate = 15;
			counter++;

			DataFormatter formatter = new DataFormatter();
			String facilityName = formatter.formatCellValue(row.getCell(colFacilityName));
			String clientName = formatter.formatCellValue(row.getCell(colClientName));
			String ageStr = formatter.formatCellValue(row.getCell(colAge));
			Integer age = ageStr != null && !ageStr.equals("") ? Integer.valueOf(ageStr) : 99;
			String sex = formatter.formatCellValue(row.getCell(colSex));
			String nationalId = formatter.formatCellValue(row.getCell(colNationalId));
			String phone = formatter.formatCellValue(row.getCell(colPhone));
			String countryofOrigin = formatter.formatCellValue(row.getCell(colCountryofOrigin));
			String travelingFrom = formatter.formatCellValue(row.getCell(colTravelingFrom));
			String nationality = formatter.formatCellValue(row.getCell(colNationality));
			String noKName = formatter.formatCellValue(row.getCell(colNoKName));
			String noKContact = formatter.formatCellValue(row.getCell(colNoKContact));
			String arrivalDate = formatter.formatCellValue(row.getCell(colArrivalDate));
			arrivalDate = arrivalDate.replace(".", "/");

			Patient p = checkIfPatientExists(nationalId);
			if (p != null) {
				System.out.println("A patient with identifier " + nationalId + " already exists. Skipping this row");
				continue;
			}
			Date admissionDate = null;
			List<String> dateFormats = new ArrayList<String>();

            dateFormats.add("dd-MM-yyyy");
			dateFormats.add("dd/MM/yyyy");
			dateFormats.add("dd-MMM-yyyy");

			for (String format : dateFormats) {
				try {
					admissionDate = new SimpleDateFormat(format).parse(arrivalDate);
					break;
				} catch (ParseException e) {

				}
			}
			if (admissionDate == null) {
				admissionDate = new Date();

			}

			System.out.print("Facility Name, Client, age, nationalId, arrivalDate: ");
			System.out.println(facilityName + " ," + clientName + ", " + age + ", " + nationalId + ", " + arrivalDate + " ");
			phone = phone.replace("-", "");
			nationalId.replace("-","");
			facilityName.replace("'","\'");

			Patient patient = createPatient(clientName, age != null ? age : 99, sex, nationalId);
			patient = addPersonAttributes(patient, phone, noKName, noKContact);
			patient = addPersonAddresses(patient, nationality, null, null, null, null);
			patient = saveAndenrollPatientInCovidQuarantine(patient, admissionDate, facilityName);

			if (travelingFrom.equals("") && !countryofOrigin.equals("")) {
			    travelingFrom = countryofOrigin;
            }
            if (travelingFrom != null && !travelingFrom.equals("") && patient != null) {
                updateTravelInfo(patient, admissionDate, travelingFrom);
            }

			if (counter % 200 == 0) {
				Context.flushSession();
				Context.clearSession();

			}
		}
	}

	private static Object getColumnValue(Cell cell) {
		Object value = null;
		//DataFormatter formatter = new DataFormatter();
		switch (cell.getCellType()) {
			case Cell.CELL_TYPE_BOOLEAN:
				value = new Boolean(cell.getBooleanCellValue());
				break;
			case Cell.CELL_TYPE_ERROR:
				value = new Byte(cell.getErrorCellValue());
				break;
			case Cell.CELL_TYPE_FORMULA:
			case Cell.CELL_TYPE_NUMERIC:
				if (DateUtil.isCellDateFormatted(cell)) {
					java.util.Date date = cell.getDateCellValue();
					value =  date;//"'" + new java.sql.Timestamp(date.getTime()).toString() + "'";
				} else {
					value = cell.getNumericCellValue();
					value = ((Double)value).intValue();
				}
				break;
			case Cell.CELL_TYPE_STRING:
				// Escape for SQL
				value = cell.getRichStringCellValue();
				break;
		}
		return value;
	}

	private static Patient createPatient(String fullName, Integer age, String sex, String idNo) {

		Patient patient = null;
		String PASSPORT_NUMBER = "e1e80daa-6d7e-11ea-bc55-0242ac130003";

		fullName = fullName.replace(".","");
		fullName = fullName.replace(",", "");
		fullName = fullName.replace("'", "");
		fullName = fullName.replace("  ", " ");

		String fName = "", mName = "", lName = "";
		if (fullName != null && !fullName.equals("")) {

			String [] nameParts = fullName.trim().split(" ");
			fName = nameParts[0].trim();
			if (nameParts.length > 1) {
				lName = nameParts[1].trim();
			} else {
				lName = nameParts[0].trim();
			}
			if (nameParts.length > 2) {
				mName = nameParts[2].trim();
			}

			fName = fName != null && !fName.equals("") ? fName : "";
			mName = mName != null && !mName.equals("") ? mName : "";
			lName = lName != null && !lName.equals("") ? lName : "";
			patient = new Patient();
			if (sex == null || sex.equals("") || StringUtils.isEmpty(sex)) {
				sex = "U";
			}
			patient.setGender(sex);
			PersonName pn = new PersonName();//Context.getPersonService().parsePersonName(fullName);
			pn.setGivenName(fName);
			pn.setFamilyName(lName);
			if (mName != null && !mName.equals("")) {
				pn.setMiddleName(mName);
			}
			System.out.print("Person name: " + pn);

			patient.addName(pn);

			if (age == null) {
				age = 100;
			}
			Calendar effectiveDate = Calendar.getInstance();
			effectiveDate.set(2020, 3, 1, 0, 0);

			Calendar computedDob = Calendar.getInstance();
			computedDob.setTimeInMillis(effectiveDate.getTimeInMillis());
			computedDob.add(Calendar.YEAR, -age);

			if (computedDob != null) {
				patient.setBirthdate(computedDob.getTime());
			}

			patient.setBirthdateEstimated(true);

			System.out.println(", ID No: " + idNo);

			PatientIdentifier openMRSID = generateOpenMRSID();

			if (idNo != null && !idNo.equals("")) {
				PatientIdentifierType upnType = Context.getPatientService().getPatientIdentifierTypeByUuid(PASSPORT_NUMBER);

				PatientIdentifier upn = new PatientIdentifier();
				upn.setIdentifierType(upnType);
				upn.setIdentifier(idNo);
				upn.setPreferred(true);
				patient.addIdentifier(upn);
			} else {
				openMRSID.setPreferred(true);
			}
			patient.addIdentifier(openMRSID);

		}
		return patient;
	}

	public static Patient checkIfPatientExists(String identifier) {

		if (identifier != null) {
			List<Patient> patientsAlreadyAssigned = Context.getPatientService().getPatients(null, identifier.trim(), null, false);
			if (patientsAlreadyAssigned.size() > 0) {
				return patientsAlreadyAssigned.get(0);
			}
		}
		/*PatientIdentifierType HEI_NUMBER_TYPE = patientService.getPatientIdentifierTypeByUuid(HEI_UNIQUE_NUMBER);
		PatientIdentifierType CCC_NUMBER_TYPE = patientService.getPatientIdentifierTypeByUuid(UNIQUE_PATIENT_NUMBER);
		PatientIdentifierType NATIONAL_ID_TYPE = patientService.getPatientIdentifierTypeByUuid(NATIONAL_ID);
		PatientIdentifierType SMART_CARD_SERIAL_NUMBER_TYPE = patientService.getPatientIdentifierTypeByUuid(SmartCardMetadata._PatientIdentifierType.SMART_CARD_SERIAL_NUMBER);
		PatientIdentifierType HTS_NUMBER_TYPE = patientService.getPatientIdentifierTypeByUuid(SmartCardMetadata._PatientIdentifierType.HTS_NUMBER);
		PatientIdentifierType GODS_NUMBER_TYPE = patientService.getPatientIdentifierTypeByUuid(SmartCardMetadata._PatientIdentifierType.GODS_NUMBER);

		String shrGodsNumber = SHRUtils.getSHR(incomingSHR).pATIENT_IDENTIFICATION.eXTERNAL_PATIENT_ID.iD;
		if (shrGodsNumber != null && !shrGodsNumber.isEmpty()) {
			List<Patient> patientsAssignedGodsNumber = patientService.getPatients(null, shrGodsNumber.trim(), Arrays.asList(GODS_NUMBER_TYPE), false);
			if (patientsAssignedGodsNumber.size() > 0) {
				return patientsAssignedGodsNumber.get(0);
			}
		}
		for (int x = 0; x < SHRUtils.getSHR(this.incomingSHR).pATIENT_IDENTIFICATION.iNTERNAL_PATIENT_ID.length; x++) {

			String idType = SHRUtils.getSHR(this.incomingSHR).pATIENT_IDENTIFICATION.iNTERNAL_PATIENT_ID[x].iDENTIFIER_TYPE;
			PatientIdentifierType identifierType = null;

			String identifier = SHRUtils.getSHR(this.incomingSHR).pATIENT_IDENTIFICATION.iNTERNAL_PATIENT_ID[x].iD;

			if (idType.equals("ANC_NUMBER")) {
				// get patient with the identifier

				List<Obs> obs = obsService.getObservations(
						null,
						null,
						Arrays.asList(conceptService.getConceptByUuid(ANC_NUMBER)),
						null,
						null,
						null,
						null,
						null,
						null,
						null,
						null,
						false
				);
				for (Obs ancNo : obs) {
					if (ancNo.getValueText().equals(identifier.trim()))
						return (Patient) ancNo.getPerson();
				}

			} else {
				if (idType.equals("HEI_NUMBER")) {
					identifierType = HEI_NUMBER_TYPE;
				} else if (idType.equals("CCC_NUMBER")) {
					identifierType = CCC_NUMBER_TYPE;
				} else if (idType.equals("NATIONAL_ID")) {
					identifierType = NATIONAL_ID_TYPE;
				} else if (idType.equals("CARD_SERIAL_NUMBER")) {
					identifierType = SMART_CARD_SERIAL_NUMBER_TYPE;
				} else if (idType.equals("HTS_NUMBER")) {
					identifierType = HTS_NUMBER_TYPE;
				}

				if (identifierType != null && identifier != null) {
					List<Patient> patientsAlreadyAssigned = Context.getPatientService().getPatients(null, identifier.trim(), null, false);
					if (patientsAlreadyAssigned.size() > 0) {
						return patientsAlreadyAssigned.get(0);
					}
				}
			}

		}*/


		return null;
	}


	private static Patient addPersonAttributes(Patient patient, String phone, String nokName, String nokPhone) {

		String NEXT_OF_KIN_CONTACT = "342a1d39-c541-4b29-8818-930916f4c2dc";
		String NEXT_OF_KIN_NAME = "830bef6d-b01f-449d-9f8d-ac0fede8dbd3";
		String TELEPHONE_CONTACT = "b2c38640-2603-4629-aebd-3b54f33f1e3a";


		PersonAttributeType phoneType = Context.getPersonService().getPersonAttributeTypeByUuid(TELEPHONE_CONTACT);
		PersonAttributeType nokNametype = Context.getPersonService().getPersonAttributeTypeByUuid(NEXT_OF_KIN_NAME);
		PersonAttributeType nokContacttype = Context.getPersonService().getPersonAttributeTypeByUuid(NEXT_OF_KIN_CONTACT);

		if (phone != null && !phone.equals("")) {
			PersonAttribute attribute = new PersonAttribute(phoneType, phone);

			try {
				Object hydratedObject = attribute.getHydratedObject();
				if (hydratedObject == null || "".equals(hydratedObject.toString())) {
					// if null is returned, the value should be blanked out
					attribute.setValue("");
				} else if (hydratedObject instanceof Attributable) {
					attribute.setValue(((Attributable) hydratedObject).serialize());
				} else if (!hydratedObject.getClass().getName().equals(phoneType.getFormat())) {
					// if the classes doesn't match the format, the hydration failed somehow
					// TODO change the PersonAttribute.getHydratedObject() to not swallow all errors?
					throw new APIException();
				}
			} catch (APIException e) {
				//.warn("Got an invalid value: " + value + " while setting personAttributeType id #" + paramName, e);
				// setting the value to empty so that the user can reset the value to something else
				attribute.setValue("");
			}
			patient.addAttribute(attribute);
		}

		if (nokName != null && !nokName.equals("")) {
			PersonAttribute attribute = new PersonAttribute(nokNametype, nokName);

			try {
				Object hydratedObject = attribute.getHydratedObject();
				if (hydratedObject == null || "".equals(hydratedObject.toString())) {
					// if null is returned, the value should be blanked out
					attribute.setValue("");
				} else if (hydratedObject instanceof Attributable) {
					attribute.setValue(((Attributable) hydratedObject).serialize());
				} else if (!hydratedObject.getClass().getName().equals(nokNametype.getFormat())) {
					// if the classes doesn't match the format, the hydration failed somehow
					// TODO change the PersonAttribute.getHydratedObject() to not swallow all errors?
					throw new APIException();
				}
			} catch (APIException e) {
				//.warn("Got an invalid value: " + value + " while setting personAttributeType id #" + paramName, e);
				// setting the value to empty so that the user can reset the value to something else
				attribute.setValue("");
			}
			patient.addAttribute(attribute);
		}

		if (nokPhone != null && !nokPhone.equals("")) {
			PersonAttribute attribute = new PersonAttribute(nokContacttype, nokPhone);

			try {
				Object hydratedObject = attribute.getHydratedObject();
				if (hydratedObject == null || "".equals(hydratedObject.toString())) {
					// if null is returned, the value should be blanked out
					attribute.setValue("");
				} else if (hydratedObject instanceof Attributable) {
					attribute.setValue(((Attributable) hydratedObject).serialize());
				} else if (!hydratedObject.getClass().getName().equals(nokContacttype.getFormat())) {
					// if the classes doesn't match the format, the hydration failed somehow
					// TODO change the PersonAttribute.getHydratedObject() to not swallow all errors?
					throw new APIException();
				}
			} catch (APIException e) {
				//.warn("Got an invalid value: " + value + " while setting personAttributeType id #" + paramName, e);
				// setting the value to empty so that the user can reset the value to something else
				attribute.setValue("");
			}
			patient.addAttribute(attribute);
		}
		return patient;
	}

	private static Patient addPersonAddresses(Patient patient, String nationality, String county, String subCounty, String ward, String postaladdress) {

		Set<PersonAddress> patientAddress = patient.getAddresses();
		if (patientAddress.size() > 0) {
			for (PersonAddress address : patientAddress) {
				if (nationality != null) {
					address.setCountry(nationality);
				}
				if (county != null) {
					address.setCountyDistrict(county);
				}
				if (subCounty != null) {
					address.setStateProvince(subCounty);
				}
				if (ward != null) {
					address.setAddress4(ward);
				}

				if (postaladdress != null) {
					address.setAddress1(postaladdress);
				}
				patient.addAddress(address);
			}
		} else {
			PersonAddress pa = new PersonAddress();
			if (nationality != null) {
				pa.setCountry(nationality);
			}
			if (county != null) {
				pa.setCountyDistrict(county);
			}
			if (subCounty != null) {
				pa.setStateProvince(subCounty);
			}
			if (ward != null) {
				pa.setAddress4(ward);
			}

			if (postaladdress != null) {
				pa.setAddress1(postaladdress);
			}
			patient.addAddress(pa);
		}
		return patient;
	}

	private static void updateTravelInfo(Patient patient, Date admissionDate, String from) {

		Encounter enc = new Encounter();
		enc.setEncounterType(Context.getEncounterService().getEncounterTypeByUuid(COVID_19_TRAVEL_HISTORY_ENCOUNTER));
		enc.setEncounterDatetime(admissionDate);
		enc.setPatient(patient);
		enc.addProvider(Context.getEncounterService().getEncounterRole(1), Context.getProviderService().getProvider(1));
		enc.setForm(Context.getFormService().getFormByUuid(COVID_19_TRAVEL_HISTORY_FORM));


		// set traveled from
		ConceptService conceptService = Context.getConceptService();
		Obs o = new Obs();
		o.setConcept(conceptService.getConcept("165198"));
		o.setDateCreated(new Date());
		o.setCreator(Context.getUserService().getUser(1));
		o.setLocation(enc.getLocation());
		o.setObsDatetime(admissionDate);
		o.setPerson(patient);
		o.setValueText(from);

		// date of arrival
        Obs ad = new Obs();
        ad.setConcept(conceptService.getConcept("160753"));
        ad.setDateCreated(new Date());
        ad.setCreator(Context.getUserService().getUser(1));
        ad.setLocation(enc.getLocation());
        ad.setObsDatetime(admissionDate);
        ad.setPerson(patient);
        ad.setValueDatetime(admissionDate);

		// default all to flight
		Obs o1 = new Obs();
		o1.setConcept(conceptService.getConcept("1375"));
		o1.setDateCreated(new Date());
		o1.setCreator(Context.getUserService().getUser(1));
		o1.setLocation(enc.getLocation());
		o1.setObsDatetime(admissionDate);
		o1.setPerson(patient);
		o1.setValueCoded(conceptService.getConcept("1378"));
		enc.addObs(o);
		enc.addObs(ad);
		enc.addObs(o1);

		Context.getEncounterService().saveEncounter(enc);

	}


	private static Patient saveAndenrollPatientInCovidQuarantine(Patient patient, Date admissionDate, String quarantineCenter) {

		Encounter enc = new Encounter();
		enc.setEncounterType(Context.getEncounterService().getEncounterTypeByUuid(COVID_QUARANTINE_ENROLLMENT_ENCOUNTER));
		enc.setEncounterDatetime(admissionDate);
		enc.setPatient(patient);
		enc.addProvider(Context.getEncounterService().getEncounterRole(1), Context.getProviderService().getProvider(1));
		enc.setForm(Context.getFormService().getFormByUuid(COVID_QUARANTINE_ENROLLMENT_FORM));


		// set quarantine center
		ConceptService conceptService = Context.getConceptService();
		Obs o = new Obs();
		o.setConcept(conceptService.getConcept("162724"));
		o.setDateCreated(new Date());
		o.setCreator(Context.getUserService().getUser(1));
		o.setLocation(enc.getLocation());
		o.setObsDatetime(admissionDate);
		o.setPerson(patient);
		o.setValueText(quarantineCenter);

		// default all admissions type to new
		Obs o1 = new Obs();
		o1.setConcept(conceptService.getConcept("161641"));
		o1.setDateCreated(new Date());
		o1.setCreator(Context.getUserService().getUser(1));
		o1.setLocation(enc.getLocation());
		o1.setObsDatetime(admissionDate);
		o1.setPerson(patient);
		o1.setValueCoded(conceptService.getConcept("164144"));
		enc.addObs(o);
		enc.addObs(o1);

		Context.getPatientService().savePatient(patient);
		Context.getEncounterService().saveEncounter(enc);
		// enroll in quarantine program
		PatientProgram pp = new PatientProgram();
		pp.setPatient(patient);
		pp.setProgram(Context.getProgramWorkflowService().getProgramByUuid(COVID_QUARANTINE_PROGRAM));
		pp.setDateEnrolled(admissionDate);
		pp.setDateCreated(new Date());
		Context.getProgramWorkflowService().savePatientProgram(pp);

		return patient;
	}

	private static PatientIdentifier generateOpenMRSID() {
		PatientIdentifierType openmrsIDType = Context.getPatientService().getPatientIdentifierTypeByUuid("dfacd928-0370-4315-99d7-6ec1c9f7ae76");
		String generated = Context.getService(IdentifierSourceService.class).generateIdentifier(openmrsIDType, "Registration");
		PatientIdentifier identifier = new PatientIdentifier(generated, openmrsIDType, getDefaultLocation());
		return identifier;
	}

	public static Location getDefaultLocation() {
		try {
			Context.addProxyPrivilege(PrivilegeConstants.VIEW_LOCATIONS);
			Context.addProxyPrivilege(PrivilegeConstants.VIEW_GLOBAL_PROPERTIES);
			String GP_DEFAULT_LOCATION = "kenyaemr.defaultLocation";
			GlobalProperty gp = Context.getAdministrationService().getGlobalPropertyObject(GP_DEFAULT_LOCATION);
			return gp != null ? ((Location) gp.getValue()) : null;
		}
		finally {
			Context.removeProxyPrivilege(PrivilegeConstants.VIEW_LOCATIONS);
			Context.removeProxyPrivilege(PrivilegeConstants.VIEW_GLOBAL_PROPERTIES);
		}

	}

}
