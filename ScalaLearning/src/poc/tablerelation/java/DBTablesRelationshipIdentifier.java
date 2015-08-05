package poc.tablerelation.java;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import poc.tablerelation.java.constants.CommonConstants;
import poc.tablerelation.java.constants.DBConstants;
import poc.tablerelation.java.pojo.QueryTuple;

public class DBTablesRelationshipIdentifier {
	
	private static final Properties priorityProperties = new Properties();
	
	private static final Properties programProperties = new Properties();
	
	private static final Properties exceptionListProperties = new Properties();
	
	private static Path outputFilePath = null;
	
	private static String dbUrl = null;
	
	/**
	 * All initialization logic will be moved to this method
	 * @throws Exception
	 */
	private static void initialize() throws Exception {
		priorityProperties.load(ClassLoader.
				getSystemResourceAsStream(CommonConstants.PRIORITY_DETAILS_FILES));
		programProperties.load(ClassLoader.
				getSystemResourceAsStream(CommonConstants.PROGRAM_DETAILS_FILES));
		exceptionListProperties.load(ClassLoader.
				getSystemResourceAsStream(CommonConstants.EXCEPTION_LIST_FILES));
		
		outputFilePath = Paths.get(
				programProperties.getProperty(CommonConstants.OUTPUT_FILE_PATH)
				+LocalDateTime.now().toString().replaceAll(":", "_")+".properties");
		
		String serviceName = programProperties.getProperty(DBConstants.DATABASE_SERVICE_NAME);
		String port = programProperties.getProperty(DBConstants.DATABASE_PORT);
		String host = programProperties.getProperty(DBConstants.DATABASE_HOST);
		if(serviceName != null && !serviceName.isEmpty()) {
			dbUrl = "jdbc:oracle:thin:@"+host+":"+port+"/"+serviceName;
		} else {
			String sid = programProperties.getProperty(DBConstants.DATABASE_SID);
			dbUrl = "jdbc:oracle:thin:@"+host+":"+port+":"+sid;
		}
	}
	
	public static void main(String[] args) throws Exception {

		initialize();
		
		DBTablesRelationshipIdentifier launcher = new DBTablesRelationshipIdentifier();
		Class.forName("oracle.jdbc.driver.OracleDriver");
		
		try(Connection connection 
				= DriverManager.getConnection(dbUrl,
						programProperties.getProperty(DBConstants.DATABASE_USERNAME),
						programProperties.getProperty(DBConstants.DATABASE_PSWD))) {
			
			//Fetching all the tables from given DB for with 'PAL' owner.
			List<String> allTablesWithPalOwner = getAllTablesWithPalOwner(connection);

			
			//Removing the tables for which manually optimized query are already available and added in ExceptionList
			resolveExceptionList(allTablesWithPalOwner);
			
			
			//Initially we try to obtain the table relationship by specifying the priority columns to look for
			Map<String, QueryTuple> autogeneratedQueries = 
					launcher.identifyTableRelationUsingMappingToPriorityDefinedTables(
							connection, allTablesWithPalOwner);
			
			
			//For the remaining tables we get the relationship from FK mapping in Oracle meta-data tables
			autogeneratedQueries =
					launcher.identifyTableRelationUsingFKDetailsFromOracleMetaDataTables(
					connection, allTablesWithPalOwner, autogeneratedQueries);
			
			
			/*
			 * Removing the all content tables at last from the list to be safe not to remove the needed tables
			 * accidentally from the list. E.g., CACHE_INFO table is also present in All content but not 
			 * actually all CACHE_INFO data are pushed as part of all content.
			 */
			eliminateAllContentTables(allTablesWithPalOwner);
			
			
			/*
			 * Finally displaying all the tables which are unresolved even after all the above steps.
			 * These tables can be manually resolved and added to the ExceptionList.properties file
			 * so that they won't be in the unresolved list during next execution.
			 */
			displayUnResolvedTables(allTablesWithPalOwner);
		}
	}
	
	/**
	 * Displaying all the unresolved tables.
	 * 
	 * @param allTablesWithPalOwner
	 */
	private static void displayUnResolvedTables(List<String> allTablesWithPalOwner) {
		allTablesWithPalOwner.forEach((table) -> {
			
			try {
				Files.write(outputFilePath, 
						("Unresoved Table : "+table+"\n").getBytes(), 
						StandardOpenOption.APPEND, StandardOpenOption.CREATE);
			} catch(IOException e) {
				throw new RuntimeException(e);
			}
		});
	}

	/**
	 * Removing all the tables which are part of ALL CONTENT push.
	 * 
	 * @param allTablesWithPalOwner
	 */
	private static void eliminateAllContentTables(List<String> allTablesWithPalOwner) throws Exception {
		AllContentTablesIdentifier tablesIdentifier = new AllContentTablesIdentifier();
		List<String> allContentTables = tablesIdentifier.readAllContentXML();
		
		allContentTables.forEach((table) ->{
			
			try {
				if(allTablesWithPalOwner.contains(table)) {
					Files.write(outputFilePath, ("Skipped All Content table : "+table+"\n").getBytes(), StandardOpenOption.APPEND, StandardOpenOption.CREATE);
					allTablesWithPalOwner.remove(table);
				}
			} catch(IOException e) {
				throw new RuntimeException(e);
			}
			
		});
	}

	/**
	 * If user provides custom query for certain table, those tables will be
	 * removed from auto-generation as custom query may be assumed as best compared
	 * to auto-generated query 
	 * 
	 * @param allTablesWithPalOwner
	 * @throws Exception
	 */
	private static void resolveExceptionList(List<String> allTablesWithPalOwner) throws Exception{
		
		exceptionListProperties.forEach((tableName, query) -> {

			try {
				if(allTablesWithPalOwner.contains(tableName)) {
					System.out.println("Removed the table : "+tableName+" from query "
							+ "auto-generation as custom query is already present");
					
					allTablesWithPalOwner.remove(tableName);
					Files.write(outputFilePath, (tableName+" = "+query+"\n").getBytes(), 
							StandardOpenOption.APPEND, StandardOpenOption.CREATE);
				}
			} catch(IOException t) {
				throw new RuntimeException(t);
			}
		});
	}

	private Map<String, QueryTuple> identifyTableRelationUsingFKDetailsFromOracleMetaDataTables(
			Connection connection, List<String> allTablesWithPalOwner, 
			Map<String, QueryTuple> copyOfAutogeneratedQueries) throws Exception {
		
		String entryPoint = "'"+priorityProperties.getProperty(CommonConstants.ENTRY_TABLE)+"'";
		
		//Creating the new to prevent modification of passed autoGeneratedQueries Map
		Map<String, QueryTuple> autogeneratedQueries = new HashMap<>();
		autogeneratedQueries.putAll(copyOfAutogeneratedQueries);
		
		final String masterQuery = "SELECT cons.table_name AS child_table, "
				+ "col.table_name parent_table, col.column_name column_name "
				+ "FROM all_cons_columns col, all_constraints cons "
				+ "WHERE cons.r_owner = col.owner "
				+ "AND cons.r_constraint_name = col.constraint_name "
				+ "AND col.owner = 'PAL' "
				+ "AND (col.table_name in ("+CommonConstants.REPLACEMENT_STRING_IN_QUERY+")) "
				+ "ORDER BY child_table";
		
		int level = 1;
		while(entryPoint != null) {
			
			System.out.println("Starting the loop for entry point : "+entryPoint);
			List<String> tempTablesList = new ArrayList<>();
			String query = masterQuery.replace(CommonConstants.REPLACEMENT_STRING_IN_QUERY, entryPoint.trim());
			
			try(PreparedStatement statement = connection.prepareStatement(query);
					ResultSet rs = statement.executeQuery()) {
				
				while(rs.next()) {
					
					String childTable = rs.getString("child_table");
					String parentTable = rs.getString("parent_table");
					String columnName = rs.getString("column_name");
					
					//Resolving cyclic dependency where FK points to column of same table
					if(childTable.equalsIgnoreCase(parentTable) && autogeneratedQueries.containsKey(childTable)) {
						System.out.println("Parent and Child table are same hence skipping - "+childTable);
						continue;
					}
					
					tempTablesList.add(childTable);

					if(!allTablesWithPalOwner.contains(childTable)) {
						System.out.println("Skipping the table "+ childTable+" as it is not present in main tables list");
						continue;
					}
					if(autogeneratedQueries.containsKey(childTable)) {
						System.out.println("Skipping the table "+ childTable+" as it is already autogenerated");
						continue;
					}

					QueryTuple savedAutoGeneratedQuery = autogeneratedQueries.get(parentTable);

					QueryTuple newAutoGeneratedQuery = new QueryTuple();
					newAutoGeneratedQuery.setChildTable(childTable);
					newAutoGeneratedQuery.setParentTable(parentTable);
					newAutoGeneratedQuery.setFromCondition(savedAutoGeneratedQuery.getParentFromCondition());
					
					newAutoGeneratedQuery.setWhereCondition(
							columnName+" = "+parentTable+"."+columnName+" AND "+savedAutoGeneratedQuery.getParentWhereCondition(level));

					Files.write(outputFilePath, (childTable+" = "+newAutoGeneratedQuery.toString()).getBytes(), 
							StandardOpenOption.APPEND, StandardOpenOption.CREATE);

					allTablesWithPalOwner.remove(childTable);
					autogeneratedQueries.put(childTable, newAutoGeneratedQuery);
				}
				
				entryPoint = null;
				if(!tempTablesList.isEmpty()) {
					entryPoint = getListAsStringForSQLInCondition(tempTablesList).toString();
					tempTablesList.clear();
				}
				level++;
			}
		}
		
		Files.write(outputFilePath, "*********** COMPLETED AUTOGENERATED QUERIES USING FK MAPPING ***********\n".getBytes(), 
				StandardOpenOption.APPEND, StandardOpenOption.CREATE);
		
		return autogeneratedQueries;
	}

	/**
	 * This method identifies the tables which are having the columns as mentioned in the file
	 * {@link resource/PriorityDetails.properties} and then auto-generates the queries based
	 * on this identified relationship.
	 * 
	 * Finally it generates the outputfile with the name in format 
	 * "auto-generated-queries2015_08_04T22_10_34.231.properties"
	 *  
	 * @param connection
	 * @param allTablesWithPalOwner
	 * @throws Exception
	 */
	private Map<String, QueryTuple> identifyTableRelationUsingMappingToPriorityDefinedTables(
			Connection connection, List<String> allTablesWithPalOwner) throws Exception {
		
		List<String> priorityColumnsList = getPriorityColumns();
		
		Map<String, String> finalTableAndColumnRelationshipMap = new HashMap<>();
		priorityColumnsList.forEach((column) -> {
			
			StringBuilder allTablesAsCommaSeperatedString = getListAsStringForSQLInCondition(allTablesWithPalOwner);
			System.out.println("allTablesAsCommaSeperatedString : "+allTablesAsCommaSeperatedString);

			String query = "SELECT table_name, column_name "
					+ "FROM all_tab_columns "
					+ "WHERE (table_name IN ("+allTablesAsCommaSeperatedString.toString()+")) "
					+ "AND column_name = ?";


			try(PreparedStatement statement = connection.prepareStatement(query)) {
				statement.setString(1, column);
				
				try (ResultSet rs = statement.executeQuery()) {
					while(rs.next()) {
						String tableName = rs.getString(1);
						String columnName = rs.getString(2);

						if(!finalTableAndColumnRelationshipMap.containsKey(tableName)) {
							finalTableAndColumnRelationshipMap.put(tableName, columnName);
							allTablesWithPalOwner.remove(tableName);
						} else {
							//Purely for debugging purpose
							System.out.println("Table is already in the map hence skipping the tableName - "
									+ ""+tableName+" <-> "+columnName);
						}
					}
				}

			} catch (SQLException e) {
				e.printStackTrace();
				//Exit the execution if we get this exception
				System.exit(1);
			}
		});
		
		return autogenerateQueries(allTablesWithPalOwner, finalTableAndColumnRelationshipMap);
	}

	private Map<String, QueryTuple> autogenerateQueries(List<String> allTablesWithPalOwner,
			Map<String, String> finalTableAndColumnRelationshipMap) throws Exception {
		
		Map<String, QueryTuple> autogeneratedQueries = new HashMap<>();
		
		finalTableAndColumnRelationshipMap.forEach((table, column) -> {
			
			String parentTable = priorityProperties.getProperty(column).split(CommonConstants.PRIORITY_COLUMNS_SPLITTER)[0];
			String fromColumn = priorityProperties.getProperty(column).split(CommonConstants.PRIORITY_COLUMNS_SPLITTER)[1];
			String whereColumn = priorityProperties.getProperty(column).split(CommonConstants.PRIORITY_COLUMNS_SPLITTER)[2];
			
			QueryTuple queryTuple = new QueryTuple(table, parentTable.trim(), fromColumn, whereColumn);
			
			try {
				autogeneratedQueries.put(table, queryTuple);
				Files.write(outputFilePath, (table+" = "+queryTuple.toString()).getBytes(), 
						StandardOpenOption.APPEND, StandardOpenOption.CREATE);
				
			} catch (Exception e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			}
		});
		
		Files.write(outputFilePath, "*********** COMPLETED AUTOGENERATED QUERIES USING PRIORITY COLUMNS ***********\n".getBytes(), 
				StandardOpenOption.APPEND, StandardOpenOption.CREATE);
		
		return autogeneratedQueries;
	}

	private List<String> getPriorityColumns() {
		
		String priorityColumns = priorityProperties.getProperty(CommonConstants.PRIORITY_COLUMNS);
		return Arrays.asList(priorityColumns.split(CommonConstants.PRIORITY_COLUMNS_SPLITTER));
	}

	private static StringBuilder getListAsStringForSQLInCondition(List<String> tablesList) {
		StringBuilder commaSeperatedAllTables = new StringBuilder();
		int dataSizeCounter = 0;
		for(String table : tablesList) {
			if(dataSizeCounter++ >= 1000) {
				commaSeperatedAllTables.deleteCharAt(commaSeperatedAllTables.length() - 1);
				commaSeperatedAllTables.append(") or table_name in (");
				dataSizeCounter = 0;
			}
			commaSeperatedAllTables.append("'"+table+"',");
		}
		commaSeperatedAllTables.deleteCharAt(commaSeperatedAllTables.length() - 1);
		return commaSeperatedAllTables;
	}

	private static List<String> getAllTablesWithPalOwner(Connection connection) throws Exception {
		List<String> allTablesList = new ArrayList<>();
		String query = "SELECT distinct table_name "
				+ "FROM all_tables "
				+ "WHERE owner = 'PAL' "
				+ "AND status = 'VALID'";
		
		try(PreparedStatement statement = connection.prepareStatement(query);
				ResultSet rs  = statement.executeQuery()) {
			
			while (rs.next()) {
				allTablesList.add(rs.getString(1));
			}
		}

		return allTablesList;
	}
}
