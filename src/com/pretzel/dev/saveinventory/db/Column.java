package com.pretzel.dev.saveinventory.db;

public class Column {
	private String name;
	private String type;
	private boolean notNull;
	private boolean primary;
	
	public Column(String name, String type) {
		this(name, type, false, false);
	}
	public Column(String name, String type, boolean notNull) {
		this(name, type, notNull, false);
	}
	public Column(String name, String type, boolean notNull, boolean primary) {
		this.name = name;
		this.type = type;
		this.notNull = notNull;
		this.primary = primary;
	}
	
	public String getName() {
		return this.name;
	}
	
	public boolean isPrimary() {
		return this.primary;
	}
	
	@Override
	public String toString() {
		return this.name+" "+this.type+(this.notNull?" NOT NULL":"");
	}
}
