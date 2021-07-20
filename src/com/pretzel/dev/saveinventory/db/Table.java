package com.pretzel.dev.saveinventory.db;

import java.util.ArrayList;

public class Table {
	private String name;
	private Column[] columns;
	private boolean mysql;
	
	public Table(String name, Column[] columns) {
		this.name = name;
		this.columns = columns;
		this.mysql = false;
	}
	
	public void setMysql(boolean mysql) {
		this.mysql = mysql;
	}
	
	public String getName() {
		return this.name;
	}
	
	public String getColumnNames() {
		String out = "(";
		for(int i = 0; i < this.columns.length; i++)
			out += columns[i].getName()+(i < this.columns.length-1 ? ",":"");
		return out+")";
	}
	
	public String getAddColumnsSQL(Column[] columns) {
		if(columns.length == 0) return "";
		
		String out = "ALTER TABLE "+this.name+" ";
		for(int i = 0; i < columns.length; i++) {
			out += "ADD "+columns[i].toString()+(i < columns.length-1 ? ",":"");
		}
		return out+";";
	}
	
	public String getCreateSQL() {
		if(this.columns.length == 0) return "";
		String sql = "CREATE TABLE IF NOT EXISTS "+this.name+"(";
		final ArrayList<String> primaryKeys = new ArrayList<String>();
		for(int i = 0; i < this.columns.length; i++) {
			sql += columns[i].toString()+(i < this.columns.length-1 ? ",":"");
			if(columns[i].isPrimary()) primaryKeys.add(columns[i].getName());
		}
		
		final int n = primaryKeys.size();
		if(n > 0) sql += ", PRIMARY KEY(";
		for(int i = 0; i < n; i++) sql += primaryKeys.get(i)+(i < n-1 ? ",":"");
		sql += (n > 0 ? "));":");");
		return (this.mysql ? sql.replace("AUTOINCREMENT", "AUTO_INCREMENT"):sql.replace("AUTO_INCREMENT", "AUTOINCREMENT"));
	}
}
