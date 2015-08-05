package poc.tablerelation.java.pojo;

import poc.tablerelation.java.constants.CommonConstants;

public class QueryTuple {
	
	private String childTable;
	
	private String parentTable;
	
	private String fromCondition;
	
	private String whereCondition;
	
	public QueryTuple() {
	}
	
	public QueryTuple(String childTable, String parentTable, String fromCondition, String whereCondition) {
		this.childTable = childTable;
		this.parentTable = parentTable;
		this.fromCondition = fromCondition;
		this.whereCondition = whereCondition;
	}

	public String getChildTable() {
		return childTable;
	}
	
	public void setChildTable(String childTable) {
		this.childTable = childTable;
	}
	
	public String getParentTable() {
		return parentTable;
	}
	
	public void setParentTable(String parentTable) {
		this.parentTable = parentTable;
	}
	
	public String getFromCondition() {
		return fromCondition;
	}
	
	public String getParentFromCondition() {
		if(fromCondition == null || fromCondition.isEmpty()) {
			return childTable;
		} else {
			return childTable+", "+fromCondition;
		}
	}
	
	public void setFromCondition(String fromCondition) {
		this.fromCondition = fromCondition;
	}
	
	public String getWhereCondition() {
		return whereCondition;
	}
	
	public String getParentWhereCondition(int level) {
		if(level <= 2) {
			return whereCondition;
		} else {
			return parentTable +"."+ whereCondition;
		}
	}
	
	public void setWhereCondition(String whereCondition) {
		this.whereCondition = whereCondition;
	}

	@Override
	public String toString() {
		StringBuilder queryBuilder = new StringBuilder();
		queryBuilder.append("SELECT "+ childTable+".* FROM "+childTable);
		if(fromCondition != null && !fromCondition.trim().equalsIgnoreCase("")) {
			queryBuilder.append(", "+fromCondition);
		}
		if(parentTable.equalsIgnoreCase("COBRAND")) {
			queryBuilder.append(" WHERE "+ whereCondition+CommonConstants.NEW_LINE);
		} else {
			queryBuilder.append(" WHERE "+ childTable +"."+ whereCondition+CommonConstants.NEW_LINE);
		}
		
		return queryBuilder.toString();
	}
}
