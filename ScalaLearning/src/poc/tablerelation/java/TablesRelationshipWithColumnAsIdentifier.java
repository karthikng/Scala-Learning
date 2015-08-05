package poc.tablerelation.java;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TablesRelationshipWithColumnAsIdentifier {

	public static void main(String[] args) throws Exception {

		Class.forName("oracle.jdbc.driver.OracleDriver");
		try(Connection connection = 
				DriverManager.getConnection("jdbc:oracle:thin:@192.168.211.161:1521:ibdv1012","app","app")) {

			List<String> allTablesList = getAllTablesList(connection);
			Map<String, String> tempColumnList = new HashMap<>();
			tempColumnList.put("COBRAND_ID", "COBRAND");

			Map<String, String> allTableIndexes = getAllindexedColumns(connection);
			identifyRelationship(connection, allTablesList, tempColumnList, allTableIndexes);
		}
	}

	private static Map<String, String> getAllindexedColumns(Connection connection) throws Exception {

		Map<String, String> allTableIndexes = new HashMap<>();
		String query = "SELECT COLUMN_NAME, TABLE_NAME "
				+ "FROM all_ind_columns "
				+ "where index_owner = 'PAL'";
		
		try(PreparedStatement statement = connection.prepareStatement(query);
				ResultSet rs  = statement.executeQuery()) {
			
			while (rs.next()) {
				allTableIndexes.put(rs.getString(1)+":"+rs.getString(2), "");
			}
		}

		return allTableIndexes;
	}

	private static void identifyRelationship(
			Connection connection, List<String> allTablesList, Map<String, String> tempColumnMap, 
			Map<String, String> allTableIndexes) throws Exception {

		Path path = Paths.get("./RelationshipMap"+LocalDateTime.now()
				.toString().replaceAll("\\.", "_").replaceAll(":", "_")+".out");

		HashMap<String, String> tempTrackingMap = new HashMap<>();
		int levelCounter = 0;
		while(!allTablesList.isEmpty() && !tempColumnMap.isEmpty()) {

			System.out.println("Size of tempColumnMap : "+tempColumnMap.size());
			List<String> tempTableList = getTablesWithColumn(
					connection, allTablesList, tempColumnMap, tempTrackingMap, path, levelCounter++, allTableIndexes);

			System.out.println("before : "+allTablesList.size());
			allTablesList.removeAll(tempTableList);
			System.out.println("after"+allTablesList.size());

			tempColumnMap = getAllIDColumnsForTable(connection, tempTableList);
		}

	}

	private static Map<String, String> getAllIDColumnsForTable(Connection connection, List<String> tempTableList) {
		Map<String, String> tempColumnMap = new HashMap<>();

		String query = "SELECT column_name, table_name "
				+ "FROM all_tab_columns "
				+ "WHERE table_name = ? "
				+ "AND owner = 'PAL' "
				+ "AND column_name LIKE '%_ID'";
		
		tempTableList.forEach((table) -> {

			ResultSet rs  = null;
			try(PreparedStatement statement = connection.prepareStatement(query))
			{
				statement.setString(1, table);
				rs = statement.executeQuery();

				while (rs.next()) {
					tempColumnMap.put(rs.getString(1), rs.getString(2));
				}

				rs.close();

					} catch(SQLException e) {
						e.printStackTrace();
					} finally {
						if(rs != null) {
							try {
								rs.close();
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
					}
		});

		return tempColumnMap;
	}

	private static List<String> getTablesWithColumn(
			Connection connection, List<String> allTablesList, Map<String, String> tempColumnMap, 
			HashMap<String, String> tempTrackingMap, Path path, int level, Map<String, String> allTableIndexes)	throws Exception {

		List<String> tempTableList = new ArrayList<>();
		StringBuilder commaSeperatedAllTables = getAllTablesListAsString(allTablesList);

		String query = "SELECT table_name, column_name "
				+ "FROM all_tab_columns "
				+ "WHERE owner = 'PAL' "
				+ "and  column_name = ? "
				+ "and (table_name in ("+commaSeperatedAllTables+"))";

		System.out.println("query : "+query);
		tempColumnMap.forEach((column, table) -> {

			ResultSet rs  = null;
			try(PreparedStatement statement = connection.prepareStatement(query))
			{
				statement.setString(1, column);
				rs = statement.executeQuery();

				while (rs.next()) {
					String tableFromQuery = rs.getString(1);
					tempTableList.add(tableFromQuery);

//					if(!tempTrackingMap.containsKey(tableFromQuery)) {
						tempTrackingMap.put(tableFromQuery, "");
						Files.write(path, getPrintStatement(table, column, tableFromQuery, level, allTableIndexes).getBytes(), 
							StandardOpenOption.CREATE, StandardOpenOption.APPEND);
//					}
				}

				rs.close();

			} catch(Exception e) {
				e.printStackTrace();
			} finally {
				if(rs != null) {
					try {
						rs.close();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		}); 

		return tempTableList;
	}

	private static String getPrintStatement(String table, String column, 
			String tableFromQuery, int level, Map<String, String> allTableIndexes) {
		
		String printStmt = (tableFromQuery+ "|"+level+"|"+column+"|"+table+"");

		if(allTableIndexes.containsKey(column+":"+tableFromQuery)) {
			printStmt += "|COLUMN_INDEXED";
		} else {
			printStmt += "|COLUMN_NOT_INDEXED";
		}
		
		//Checking if the column is primary of table
		if(column.equals(tableFromQuery+"_ID")) {
			printStmt += "|PRIMARY_KEY";
		}
		printStmt += "\n";
		return printStmt;
	}

	private static StringBuilder getAllTablesListAsString(List<String> allTablesList) {
		StringBuilder commaSeperatedAllTables = new StringBuilder();
		int dataSizeCounter = 0;
		for(String table : allTablesList) {
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

	private static List<String> getAllTablesList(Connection connection) throws Exception {
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
