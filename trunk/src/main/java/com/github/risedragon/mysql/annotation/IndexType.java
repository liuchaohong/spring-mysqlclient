package com.github.risedragon.mysql.annotation;

public enum IndexType {

	NULL(null), UNIQUE("UNIQUE"), FULLTEXT("FULLTEXT");

	public final String sqlValue;

	private IndexType(String sqlValue) {
		this.sqlValue = sqlValue;
	}
}
