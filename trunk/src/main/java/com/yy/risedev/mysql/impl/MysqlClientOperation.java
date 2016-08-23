package com.yy.risedev.mysql.impl;

import static com.yy.risedev.mysql.annotation.asm.AsmKit.isNotEmpty;
import static com.yy.risedev.mysql.annotation.asm.JdbcActionClassLoader.Loader;

import java.io.InputStream;
import java.io.Reader;
import java.lang.ref.SoftReference;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URL;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLXML;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import com.yy.risedev.mysql.ConnectionCallback;
import com.yy.risedev.mysql.MysqlClientException;
import com.yy.risedev.mysql.Page;
import com.yy.risedev.mysql.annotation.asm.AsmKit;
import com.yy.risedev.mysql.annotation.data.ClassMetaInfo;
import com.yy.risedev.mysql.config.ConfigMetaInfo;
import com.yy.risedev.mysql.config.ConfigSAXParser;
import com.yy.risedev.mysql.jdbc.JdbcAction;
import com.yy.risedev.mysql.jdbc.SqlDdlKit;
import com.yy.risedev.mysql.jdbc.SqlMeta;
import com.yy.risedev.mysql.jdbc.SqlMetaKit;

@SuppressWarnings("rawtypes")
abstract class MysqlClientOperation {

	private static final Log logger = LogFactory.getLog(MysqlClientOperation.class);

	/*****************************************************************
	 * 配置属性
	 *****************************************************************/
	DataSource dataSource; // 数据源
	String packagesToScan; // 多值用逗号分隔
	String configLocations; // 多值用逗号分隔
	boolean showSql; // 显示sql
	boolean checkConfig; // 检查配置，特别是SQL。@TODO：目前没有很的办法
	boolean updateTable; // 是否更新表结构

	/*****************************************************************
	 * 属性依赖注入方法
	 *****************************************************************/

	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	public void setPackagesToScan(String packagesToScan) {
		this.packagesToScan = packagesToScan;
	}

	public void setConfigLocations(String configLocations) {
		this.configLocations = configLocations;
	}

	public void setShowSql(boolean showSql) {
		this.showSql = showSql;
	}

	public void setUpdateTable(boolean updateTable) {
		this.updateTable = updateTable;
	}

	public void setCheckConfig(boolean checkConfig) {
		this.checkConfig = checkConfig;
	}

	/**************************************************
	 * 实体查询方法
	 **************************************************/

	protected <T> T callback(Connection conn, ConnectionCallback<T> callback) throws SQLException {
		return callback.doInConnection(conn);
	}

	protected <T> int insert(Connection conn, T tableObject) throws SQLException {

		Class<?> tableType = tableObject.getClass();
		SqlMeta meta = insertSqlMetaCache.get(tableType);

		if (meta == null) {
			throw new SQLException("Not table class: " + tableType);
		} else if (showSql) {
			logger.info("Sql for insert: " + meta);
		}

		JdbcAction action = getJdbcAction(tableType);
		PreparedStatement pstmt = null;
		try {
			pstmt = conn.prepareStatement(meta.psql);
			action.setParam(pstmt, meta.params, tableObject);
			return pstmt.executeUpdate();
		} finally {
			if (pstmt != null) {
				pstmt.close();
			}
		}
	}

	protected <T> int insertIgnore(Connection conn, T tableObject) throws SQLException {

		Class<?> tableType = tableObject.getClass();
		SqlMeta meta = insertSqlMetaCache.get(tableType);
		String psql = SqlMetaKit.modifyPsqlForInsertIgnore(meta);

		if (meta == null) {
			throw new SQLException("Not table class: " + tableType);
		} else if (showSql) {
			logger.info("Sql for insertIgnore: " + meta.toString(psql));
		}

		JdbcAction action = getJdbcAction(tableType);
		PreparedStatement pstmt = null;
		try {
			pstmt = conn.prepareStatement(psql);
			action.setParam(pstmt, meta.params, tableObject);
			return pstmt.executeUpdate();
		} finally {
			if (pstmt != null) {
				pstmt.close();
			}
		}
	}

	protected <T, R> R insert(Connection conn, T tableObject, Class<R> generatedKeyType) throws SQLException {

		Class<?> tableType = tableObject.getClass();
		SqlMeta meta = insertSqlMetaCache.get(tableType);

		if (meta == null) {
			throw new SQLException("Not table class: " + tableType);
		} else if (showSql) {
			logger.info("Sql for insert: " + meta);
		}

		JdbcAction action = getJdbcAction(tableType);
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try {
			pstmt = conn.prepareStatement(meta.psql, Statement.RETURN_GENERATED_KEYS);
			action.setParam(pstmt, meta.params, tableObject);
			pstmt.executeUpdate();
			rs = pstmt.getGeneratedKeys();
			if (rs.next()) {
				return JdbcAction.getResultByType(rs, 1, generatedKeyType);
			}
			return null;
		} finally {
			if (rs != null) {
				rs.close();
			}
			if (pstmt != null) {
				pstmt.close();
			}
		}
	}

	protected <T, R> R insertIgnore(Connection conn, T tableObject, Class<R> generatedKeyType) throws SQLException {

		Class<?> tableType = tableObject.getClass();
		SqlMeta meta = insertSqlMetaCache.get(tableType);
		String psql = SqlMetaKit.modifyPsqlForInsertIgnore(meta);

		if (meta == null) {
			throw new SQLException("Not table class: " + tableType);
		} else if (showSql) {
			logger.info("Sql for insertIgnore: " + meta.toString(psql));
		}

		JdbcAction action = getJdbcAction(tableType);
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try {
			pstmt = conn.prepareStatement(psql, Statement.RETURN_GENERATED_KEYS);
			action.setParam(pstmt, meta.params, tableObject);
			pstmt.executeUpdate();
			rs = pstmt.getGeneratedKeys();
			if (rs.next()) {
				return JdbcAction.getResultByType(rs, 1, generatedKeyType);
			}
			return null;
		} finally {
			if (rs != null) {
				rs.close();
			}
			if (pstmt != null) {
				pstmt.close();
			}
		}
	}

	protected <T> int update(Connection conn, T tableObject) throws SQLException {

		Class<?> tableType = tableObject.getClass();
		SqlMeta meta = updateSqlMetaCache.get(tableType);

		if (meta == null) {
			throw new SQLException("Not table class: " + tableType);
		} else if (showSql) {
			logger.info("Sql for update: " + meta);
		}

		JdbcAction action = getJdbcAction(tableType);
		PreparedStatement pstmt = null;
		try {
			pstmt = conn.prepareStatement(meta.psql);
			action.setParam(pstmt, meta.params, tableObject);
			return pstmt.executeUpdate();
		} finally {
			if (pstmt != null) {
				pstmt.close();
			}
		}
	}

	protected <T> int replace(Connection conn, T tableObject) throws SQLException {

		Class<?> tableType = tableObject.getClass();
		SqlMeta meta = replaceSqlMetaCache.get(tableType);

		if (meta == null) {
			throw new SQLException("Not table class: " + tableType);
		} else if (showSql) {
			logger.info("Sql for replace: " + meta);
		}

		JdbcAction action = getJdbcAction(tableType);
		PreparedStatement pstmt = null;
		try {
			pstmt = conn.prepareStatement(meta.psql);
			action.setParam(pstmt, meta.params, tableObject);
			return pstmt.executeUpdate();
		} finally {
			if (pstmt != null) {
				pstmt.close();
			}
		}
	}

	protected <T, R> R replace(Connection conn, T tableObject, Class<R> generatedKeyType) throws SQLException {

		Class<?> tableType = tableObject.getClass();
		SqlMeta meta = replaceSqlMetaCache.get(tableType);

		if (meta == null) {
			throw new SQLException("Not table class: " + tableType);
		} else if (showSql) {
			logger.info("Sql for replace: " + meta);
		}

		JdbcAction action = getJdbcAction(tableType);
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try {
			pstmt = conn.prepareStatement(meta.psql, Statement.RETURN_GENERATED_KEYS);
			action.setParam(pstmt, meta.params, tableObject);
			pstmt.executeUpdate();
			rs = pstmt.getGeneratedKeys();
			if (rs.next()) {
				return JdbcAction.getResultByType(rs, 1, generatedKeyType);
			}
			return null;
		} finally {
			if (rs != null) {
				rs.close();
			}
			if (pstmt != null) {
				pstmt.close();
			}
		}
	}

	protected <T> int merge(Connection conn, T tableObject) throws SQLException {

		Class<?> tableType = tableObject.getClass();
		SqlMeta meta = mergeSqlMetaCache.get(tableType);

		if (meta == null) {
			throw new SQLException("Not table class: " + tableType);
		} else if (showSql) {
			logger.info("Sql for merge: " + meta);
		}

		JdbcAction action = getJdbcAction(tableType);
		PreparedStatement pstmt = null;
		try {
			pstmt = conn.prepareStatement(meta.psql);
			action.setParam(pstmt, meta.params, tableObject);
			return pstmt.executeUpdate();
		} finally {
			if (pstmt != null) {
				pstmt.close();
			}
		}
	}

	protected <T, R> R merge(Connection conn, T tableObject, Class<R> generatedKeyType) throws SQLException {

		Class<?> tableType = tableObject.getClass();
		SqlMeta meta = mergeSqlMetaCache.get(tableType);

		if (meta == null) {
			throw new SQLException("Not table class: " + tableType);
		} else if (showSql) {
			logger.info("Sql for merge: " + meta);
		}

		JdbcAction action = getJdbcAction(tableType);
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try {
			pstmt = conn.prepareStatement(meta.psql, Statement.RETURN_GENERATED_KEYS);
			action.setParam(pstmt, meta.params, tableObject);
			pstmt.executeUpdate();
			rs = pstmt.getGeneratedKeys();
			if (rs.next()) {
				return JdbcAction.getResultByType(rs, 1, generatedKeyType);
			}
			return null;
		} finally {
			if (rs != null) {
				rs.close();
			}
			if (pstmt != null) {
				pstmt.close();
			}
		}
	}

	protected <T> int delete(Connection conn, T tableObject) throws SQLException {

		Class<?> tableType = tableObject.getClass();
		SqlMeta meta = deleteSqlMetaCache.get(tableType);

		if (meta == null) {
			throw new SQLException("Not table class: " + tableType);
		} else if (showSql) {
			logger.info("Sql for delete: " + meta);
		}

		JdbcAction action = getJdbcAction(tableType);
		PreparedStatement pstmt = null;
		try {
			pstmt = conn.prepareStatement(meta.psql);
			action.setParam(pstmt, meta.params, tableObject);
			return pstmt.executeUpdate();
		} finally {
			if (pstmt != null) {
				pstmt.close();
			}
		}
	}

	protected <T> int deleteByKey(Connection conn, Class<?> tableType, Object... keys) throws SQLException {

		SqlMeta meta = deleteSqlMetaCache.get(tableType);

		if (meta == null) {
			throw new SQLException("Not table class: " + tableType);
		} else if (showSql) {
			logger.info("Sql for delete: " + meta);
		}

		JdbcAction setAction = JdbcAction.ARRAY_JDBC_ACTION;
		PreparedStatement pstmt = null;
		try {
			pstmt = conn.prepareStatement(meta.psql);
			setAction.setParam(pstmt, meta.params, keys);
			return pstmt.executeUpdate();
		} finally {
			if (pstmt != null) {
				pstmt.close();
			}
		}
	}

	@SuppressWarnings("unchecked")
	protected <T> T select(Connection conn, T tableObject) throws SQLException {

		Class<?> tableType = tableObject.getClass();
		SqlMeta meta = selectSqlMetaCache.get(tableType);

		if (meta == null) {
			throw new SQLException("Not table class: " + tableType);
		} else if (showSql) {
			logger.info("Sql for select: " + meta);
		}

		JdbcAction action = getJdbcAction(tableType);
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try {
			pstmt = conn.prepareStatement(meta.psql);
			action.setParam(pstmt, meta.params, tableObject);
			rs = pstmt.executeQuery();
			if (meta.labels == null) {
				SqlMetaKit.fillSqlMetaLables(rs, meta);
			}
			if (rs.next()) {
				return (T) action.getResult(rs, meta.labels);
			}

			return null;
		} finally {
			if (rs != null) {
				rs.close();
			}
			if (pstmt != null) {
				pstmt.close();
			}
		}

	}

	protected <T> void select2(Connection conn, T tableObject) throws SQLException {

		Class<?> tableType = tableObject.getClass();
		SqlMeta meta = selectSqlMetaCache.get(tableType);

		if (meta == null) {
			throw new SQLException("Not table class: " + tableType);
		} else if (showSql) {
			logger.info("Sql for select2: " + meta);
		}

		JdbcAction action = getJdbcAction(tableType);
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try {
			pstmt = conn.prepareStatement(meta.psql);
			action.setParam(pstmt, meta.params, tableObject);
			rs = pstmt.executeQuery();
			if (meta.labels == null) {
				SqlMetaKit.fillSqlMetaLables(rs, meta);
			}
			if (rs.next()) {
				action.getResult2(rs, meta.labels, tableObject);
			}
		} finally {
			if (rs != null) {
				rs.close();
			}
			if (pstmt != null) {
				pstmt.close();
			}
		}
	}

	@SuppressWarnings("unchecked")
	protected <T> T selectByKey(Connection conn, Class<T> tableType, Object... keys) throws SQLException {

		SqlMeta meta = selectSqlMetaCache.get(tableType);

		if (meta == null) {
			throw new SQLException("Not table class: " + tableType);
		} else if (showSql) {
			logger.info("Sql for selectByKey: " + meta);
		}

		JdbcAction setAction = JdbcAction.ARRAY_JDBC_ACTION;
		JdbcAction getAction = getJdbcAction(tableType);
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try {
			pstmt = conn.prepareStatement(meta.psql);
			setAction.setParam(pstmt, meta.params, keys);
			rs = pstmt.executeQuery();
			if (meta.labels == null) {
				SqlMetaKit.fillSqlMetaLables(rs, meta);
			}
			if (rs.next()) {
				return (T) getAction.getResult(rs, meta.labels);
			}

			return null;
		} finally {
			if (rs != null) {
				rs.close();
			}
			if (pstmt != null) {
				pstmt.close();
			}
		}

	}

	@SuppressWarnings("unchecked")
	protected <T> List<T> selectAll(Connection conn, Class<T> tableType) throws SQLException {

		SqlMeta meta = selectAllSqlMetaCache.get(tableType);

		if (meta == null) {
			throw new SQLException("Not table class: " + tableType);
		} else if (showSql) {
			logger.info("Sql for selectAll: " + meta);
		}

		JdbcAction action = getJdbcAction(tableType);
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try {
			pstmt = conn.prepareStatement(meta.psql);
			rs = pstmt.executeQuery();
			if (meta.labels == null) {
				SqlMetaKit.fillSqlMetaLables(rs, meta);
			}
			List<T> result = new LinkedList<T>();
			while (rs.next()) {
				result.add((T) action.getResult(rs, meta.labels));
			}
			return result;
		} finally {
			if (rs != null) {
				rs.close();
			}
			if (pstmt != null) {
				pstmt.close();
			}
		}
	}

	@SuppressWarnings("unchecked")
	protected <T> List<T> selectRange(Connection conn, Class<T> tableType, int start, int max) throws SQLException {

		SqlMeta meta = selectAllSqlMetaCache.get(tableType);
		String psql = SqlMetaKit.modifyPsqlForLimit(meta, start, max);
		if (meta == null) {
			throw new SQLException("Not table class: " + tableType);
		} else if (showSql) {
			logger.info("Sql for selectRange: " + meta.toString(psql));
		}

		JdbcAction action = getJdbcAction(tableType);
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try {
			pstmt = conn.prepareStatement(psql);
			rs = pstmt.executeQuery();
			if (meta.labels == null) {
				SqlMetaKit.fillSqlMetaLables(rs, meta);
			}
			List<T> result = new LinkedList<T>();
			while (rs.next()) {
				result.add((T) action.getResult(rs, meta.labels));
			}

			return result;
		} finally {
			if (rs != null) {
				rs.close();
			}
			if (pstmt != null) {
				pstmt.close();
			}
		}
	}

	@SuppressWarnings("unchecked")
	protected <T> T selectFirst(Connection conn, Class<T> tableType) throws SQLException {
		SqlMeta meta = selectAllSqlMetaCache.get(tableType);
		String psql = SqlMetaKit.modifyPsqlForLimit(meta, 0, 1);

		if (meta == null) {
			throw new SQLException("Not table class: " + tableType);
		} else if (showSql) {
			logger.info("Sql for selectFirst: " + meta.toString(psql));
		}

		JdbcAction action = getJdbcAction(tableType);
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try {
			pstmt = conn.prepareStatement(psql);
			rs = pstmt.executeQuery();
			if (meta.labels == null) {
				SqlMetaKit.fillSqlMetaLables(rs, meta);
			}
			if (rs.next()) {
				return (T) action.getResult(rs, meta.labels);
			}
			return null;
		} finally {
			if (rs != null) {
				rs.close();
			}
			if (pstmt != null) {
				pstmt.close();
			}
		}
	}

	@SuppressWarnings("unchecked")
	protected <T> void selectPage(Connection conn, Class<T> tableType, Page<T> page) throws SQLException {

		int start = page.getStart();
		if (start < 0) {
			start = 0;
		}
		int max = page.getMax();
		if (max <= 0) {
			max = Integer.MAX_VALUE;
		}

		SqlMeta meta = selectAllSqlMetaCache.get(tableType);
		String psql = SqlMetaKit.modifyPsqlForPageLimit(meta, start, max, page.getOrderBy(), page.isOrderDesc());
		if (meta == null) {
			throw new SQLException("Not table class: " + tableType);
		} else if (showSql) {
			logger.info("Sql for selectPage: " + meta.toString(psql));
		}

		JdbcAction action = getJdbcAction(tableType);
		PreparedStatement pstmt = null;
		ResultSet rs = null;

		try {
			pstmt = conn.prepareStatement(psql);
			rs = pstmt.executeQuery();
			if (meta.labels == null) {
				SqlMetaKit.fillSqlMetaLables(rs, meta);
			}
			List<T> result = new LinkedList<T>();
			while (rs.next()) {
				result.add((T) action.getResult(rs, meta.labels));
			}
			page.setData(result);
		} finally {
			if (rs != null) {
				rs.close();
			}
			if (pstmt != null) {
				pstmt.close();
			}
		}

		if ((start == 0 && max == Integer.MAX_VALUE) || (page.getData().size() < max)) {
			page.setTotal(page.getData().size() + start); // 加上起点
		} else {
			try {
				pstmt = conn.prepareStatement(SqlMetaKit.modifyPsqlForPageTotal(meta));
				rs = pstmt.executeQuery();
				int total = 0;
				if (rs.next()) {
					total = rs.getInt(1);
				}
				page.setTotal(total);
			} finally {
				if (rs != null) {
					rs.close();
				}
				if (pstmt != null) {
					pstmt.close();
				}
			}
		}
	}

	protected <T> int[] batchInsert(Connection conn, T[] tableObjects) throws SQLException {

		Class<?> tableType = tableObjects[0].getClass();
		SqlMeta meta = insertSqlMetaCache.get(tableType);

		if (meta == null) {
			throw new SQLException("Not table class: " + tableType);
		} else if (showSql) {
			logger.info("Sql for batchInsert: " + meta);
		}

		if (conn.getAutoCommit()) {
			logger.warn("Not setAutoCommit(false) before executing batchInsert");
		}

		JdbcAction action = getJdbcAction(tableType);
		PreparedStatement pstmt = null;
		try {
			pstmt = conn.prepareStatement(meta.psql);
			for (T tableObject : tableObjects) {
				action.setParam(pstmt, meta.params, tableObject);
				pstmt.addBatch();
			}
			return pstmt.executeBatch();
		} finally {
			if (pstmt != null) {
				pstmt.close();
			}
		}
	}

	protected <T> int[] batchInsertIgnore(Connection conn, T[] tableObjects) throws SQLException {

		Class<?> tableType = tableObjects[0].getClass();
		SqlMeta meta = insertSqlMetaCache.get(tableType);
		String psql = SqlMetaKit.modifyPsqlForInsertIgnore(meta);

		if (meta == null) {
			throw new SQLException("Not table class: " + tableType);
		} else if (showSql) {
			logger.info("Sql for batchInsertIgnore: " + psql);
		}

		if (conn.getAutoCommit()) {
			logger.warn("Not setAutoCommit(false) before executing batchInsertIgnore");
		}

		JdbcAction action = getJdbcAction(tableType);
		PreparedStatement pstmt = null;
		try {
			pstmt = conn.prepareStatement(psql);
			for (T tableObject : tableObjects) {
				action.setParam(pstmt, meta.params, tableObject);
				pstmt.addBatch();
			}
			return pstmt.executeBatch();
		} finally {
			if (pstmt != null) {
				pstmt.close();
			}
		}
	}

	protected <T, R> R[] batchInsert(Connection conn, T[] tableObjects, Class<R> generatedKeyType) throws SQLException {

		Class<?> tableType = tableObjects[0].getClass();
		SqlMeta meta = insertSqlMetaCache.get(tableType);
		if (meta == null) {
			throw new SQLException("Not table class: " + tableType);
		} else if (showSql) {
			logger.info("Sql for batchInsert: " + meta);
		}

		if (conn.getAutoCommit()) {
			logger.warn("Not setAutoCommit(false) before executing batchInsert");
		}

		JdbcAction action = getJdbcAction(tableType);
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try {
			pstmt = conn.prepareStatement(meta.psql, Statement.RETURN_GENERATED_KEYS);
			for (T tableObject : tableObjects) {
				action.setParam(pstmt, meta.params, tableObject);
				pstmt.addBatch();
			}
			int[] rows = pstmt.executeBatch();

			@SuppressWarnings("unchecked")
			R[] result = (R[]) Array.newInstance(generatedKeyType, rows.length);
			rs = pstmt.getGeneratedKeys();
			for (int i = 0; i < result.length; i++) {
				if (rows[i] > 0 && rs.next()) {
					result[i] = JdbcAction.getResultByType(rs, 1, generatedKeyType);
				}
			}
			return result;
		} finally {
			if (rs != null) {
				rs.close();
			}
			if (pstmt != null) {
				pstmt.close();
			}
		}
	}

	protected <T, R> R[] batchInsertIgnore(Connection conn, T[] tableObjects, Class<R> generatedKeyType) throws SQLException {

		Class<?> tableType = tableObjects[0].getClass();
		SqlMeta meta = insertSqlMetaCache.get(tableType);
		String psql = SqlMetaKit.modifyPsqlForInsertIgnore(meta);
		if (meta == null) {
			throw new SQLException("Not table class: " + tableType);
		} else if (showSql) {
			logger.info("Sql for batchInsertIgnore: " + meta.toString(psql));
		}

		if (conn.getAutoCommit()) {
			logger.warn("Not setAutoCommit(false) before executing batchInsertIgnore");
		}

		JdbcAction action = getJdbcAction(tableType);
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try {
			pstmt = conn.prepareStatement(psql, Statement.RETURN_GENERATED_KEYS);
			for (T tableObject : tableObjects) {
				action.setParam(pstmt, meta.params, tableObject);
				pstmt.addBatch();
			}
			int[] rows = pstmt.executeBatch();

			@SuppressWarnings("unchecked")
			R[] result = (R[]) Array.newInstance(generatedKeyType, rows.length);
			rs = pstmt.getGeneratedKeys();
			for (int i = 0; i < result.length; i++) {
				if (rows[i] > 0 && rs.next()) {
					result[i] = JdbcAction.getResultByType(rs, 1, generatedKeyType);
				}
			}
			return result;
		} finally {
			if (rs != null) {
				rs.close();
			}
			if (pstmt != null) {
				pstmt.close();
			}
		}
	}

	protected <T> int[] batchUpdate(Connection conn, T[] tableObjects) throws SQLException {

		Class<?> tableType = tableObjects[0].getClass();
		SqlMeta meta = updateSqlMetaCache.get(tableType);
		if (meta == null) {
			throw new SQLException("Not table class: " + tableType);
		} else if (showSql) {
			logger.info("Sql for batchUpdate: " + meta);
		}

		if (conn.getAutoCommit()) {
			logger.warn("Not setAutoCommit(false) before executing batchUpdate");
		}

		JdbcAction action = getJdbcAction(tableType);
		PreparedStatement pstmt = null;
		try {
			pstmt = conn.prepareStatement(meta.psql);
			for (T tableObject : tableObjects) {
				action.setParam(pstmt, meta.params, tableObject);
				pstmt.addBatch();
			}
			return pstmt.executeBatch();
		} finally {
			if (pstmt != null) {
				pstmt.close();
			}
		}
	}

	protected <T> int[] batchReplace(Connection conn, T[] tableObjects) throws SQLException {

		Class<?> tableType = tableObjects[0].getClass();
		SqlMeta meta = replaceSqlMetaCache.get(tableType);
		if (meta == null) {
			throw new SQLException("Not table class: " + tableType);
		} else if (showSql) {
			logger.info("Sql for batchReplace: " + meta);
		}

		if (conn.getAutoCommit()) {
			logger.warn("Not setAutoCommit(false) before executing batchReplace");
		}

		JdbcAction action = getJdbcAction(tableType);
		PreparedStatement pstmt = null;
		try {
			pstmt = conn.prepareStatement(meta.psql);
			for (T tableObject : tableObjects) {
				action.setParam(pstmt, meta.params, tableObject);
				pstmt.addBatch();
			}
			return pstmt.executeBatch();
		} finally {
			if (pstmt != null) {
				pstmt.close();
			}
		}
	}

	protected <T, R> R[] batchReplace(Connection conn, T[] tableObjects, Class<R> generatedKeyType) throws SQLException {

		Class<?> tableType = tableObjects[0].getClass();
		SqlMeta meta = replaceSqlMetaCache.get(tableType);
		if (meta == null) {
			throw new SQLException("Not table class: " + tableType);
		} else if (showSql) {
			logger.info("Sql for batchReplace: " + meta);
		}

		if (conn.getAutoCommit()) {
			logger.warn("Not setAutoCommit(false) before executing batchReplace");
		}

		JdbcAction action = getJdbcAction(tableType);
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try {
			pstmt = conn.prepareStatement(meta.psql, Statement.RETURN_GENERATED_KEYS);
			for (T tableObject : tableObjects) {
				action.setParam(pstmt, meta.params, tableObject);
				pstmt.addBatch();
			}
			int[] rows = pstmt.executeBatch();

			@SuppressWarnings("unchecked")
			R[] result = (R[]) Array.newInstance(generatedKeyType, rows.length);
			rs = pstmt.getGeneratedKeys();
			for (int i = 0; i < result.length; i++) {
				if (rows[i] > 0 && rs.next()) {
					result[i] = JdbcAction.getResultByType(rs, 1, generatedKeyType);
				}
			}
			return result;
		} finally {
			if (rs != null) {
				rs.close();
			}
			if (pstmt != null) {
				pstmt.close();
			}
		}
	}

	protected <T> int[] batchMerge(Connection conn, T[] tableObjects) throws SQLException {

		Class<?> tableType = tableObjects[0].getClass();
		SqlMeta meta = mergeSqlMetaCache.get(tableType);
		if (meta == null) {
			throw new SQLException("Not table class: " + tableType);
		} else if (showSql) {
			logger.info("Sql for batchMerge: " + meta);
		}

		if (conn.getAutoCommit()) {
			logger.warn("Not setAutoCommit(false) before executing batchMerge");
		}

		JdbcAction action = getJdbcAction(tableType);
		PreparedStatement pstmt = null;
		try {
			pstmt = conn.prepareStatement(meta.psql);
			for (T tableObject : tableObjects) {
				action.setParam(pstmt, meta.params, tableObject);
				pstmt.addBatch();
			}
			return pstmt.executeBatch();
		} finally {
			if (pstmt != null) {
				pstmt.close();
			}
		}
	}

	protected <T, R> R[] batchMerge(Connection conn, T[] tableObjects, Class<R> generatedKeyType) throws SQLException {

		Class<?> tableType = tableObjects[0].getClass();
		SqlMeta meta = mergeSqlMetaCache.get(tableType);
		if (meta == null) {
			throw new SQLException("Not table class: " + tableType);
		} else if (showSql) {
			logger.info("Sql for batchMerge: " + meta);
		}

		if (conn.getAutoCommit()) {
			logger.warn("Not setAutoCommit(false) before executing batchMerge");
		}

		JdbcAction action = getJdbcAction(tableType);
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try {
			pstmt = conn.prepareStatement(meta.psql, Statement.RETURN_GENERATED_KEYS);
			for (T tableObject : tableObjects) {
				action.setParam(pstmt, meta.params, tableObject);
				pstmt.addBatch();
			}
			int[] rows = pstmt.executeBatch();
			@SuppressWarnings("unchecked")
			R[] result = (R[]) Array.newInstance(generatedKeyType, rows.length);
			rs = pstmt.getGeneratedKeys();
			for (int i = 0; i < result.length; i++) {
				if (rows[i] > 0 && rs.next()) {
					result[i] = JdbcAction.getResultByType(rs, 1, generatedKeyType);
				}
			}
			return result;
		} finally {
			if (rs != null) {
				rs.close();
			}
			if (pstmt != null) {
				pstmt.close();
			}
		}
	}

	protected <T> int[] batchDelete(Connection conn, T[] tableObjects) throws SQLException {

		Class<?> tableType = tableObjects[0].getClass();
		SqlMeta meta = deleteSqlMetaCache.get(tableType);
		if (meta == null) {
			throw new SQLException("Not table class: " + tableType);
		} else if (showSql) {
			logger.info("Sql for batchDelete: " + meta);
		}

		if (conn.getAutoCommit()) {
			logger.warn("Not setAutoCommit(false) before executing batchDelete");
		}

		JdbcAction action = getJdbcAction(tableType);
		PreparedStatement pstmt = null;
		try {
			pstmt = conn.prepareStatement(meta.psql);
			for (T tableObject : tableObjects) {
				action.setParam(pstmt, meta.params, tableObject);
				pstmt.addBatch();
			}
			return pstmt.executeBatch();
		} finally {
			if (pstmt != null) {
				pstmt.close();
			}
		}
	}

	protected <T> int[] batchDeleteByKey(Connection conn, Class<T> tableType, Object[][] tableObjects) throws SQLException {

		SqlMeta meta = deleteSqlMetaCache.get(tableType);
		if (meta == null) {
			throw new SQLException("Not table class: " + tableType);
		} else if (showSql) {
			logger.info("Sql for batchDeleteByKey: " + meta);
		}

		if (conn.getAutoCommit()) {
			logger.warn("Not setAutoCommit(false) before executing batchDeleteByKey");
		}

		JdbcAction action = JdbcAction.ARRAY_JDBC_ACTION;
		PreparedStatement pstmt = null;
		try {
			pstmt = conn.prepareStatement(meta.psql);
			for (Object[] tableObject : tableObjects) {
				action.setParam(pstmt, meta.params, tableObject);
				pstmt.addBatch();
			}
			return pstmt.executeBatch();
		} finally {
			if (pstmt != null) {
				pstmt.close();
			}
		}
	}

	/**************************************************
	 * 非实体查询方法
	 **************************************************/

	@SuppressWarnings("unchecked")
	protected <T> List<T> query(Connection conn, String queryId, Class<T> elemType, Object params) throws SQLException {

		SqlMeta meta = configSqlMetaCache.get(queryId);
		if (meta == null) {
			throw new SQLException("Not sql config: " + queryId);
		} else if (showSql) {
			logger.info("Sql for query: " + meta);
		}

		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try {
			pstmt = conn.prepareStatement(meta.psql);
			if (params != null) {
				JdbcAction setAction = getJdbcAction(params.getClass());
				setAction.setParam(pstmt, meta.params, params);
			}
			rs = pstmt.executeQuery();
			if (meta.labels == null) {
				SqlMetaKit.fillSqlMetaLables(rs, meta);
			}
			List<T> list = new LinkedList<T>();
			JdbcAction getAction = getJdbcAction(elemType);
			while (rs.next()) {
				list.add((T) getAction.getResult(rs, meta.labels));
			}
			return list;
		} finally {
			if (rs != null) {
				rs.close();
			}
			if (pstmt != null) {
				pstmt.close();
			}
		}
	}

	@SuppressWarnings("unchecked")
	protected <T> List<T> queryRange(Connection conn, String queryId, Class<T> elemType, int start, int max, Object params) throws SQLException {

		SqlMeta meta = configSqlMetaCache.get(queryId);
		String psql = SqlMetaKit.modifyPsqlForLimit(meta, start, max);
		if (meta == null) {
			throw new SQLException("Not sql config: " + queryId);
		} else if (showSql) {
			logger.info("Sql for queryRange: " + meta.toString(psql));
		}

		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try {
			pstmt = conn.prepareStatement(psql);
			if (params != null) {
				JdbcAction setAction = getJdbcAction(params.getClass());
				setAction.setParam(pstmt, meta.params, params);
			}
			rs = pstmt.executeQuery();
			if (meta.labels == null) {
				SqlMetaKit.fillSqlMetaLables(rs, meta);
			}
			List<T> list = new LinkedList<T>();
			JdbcAction getAction = getJdbcAction(elemType);
			while (rs.next()) {
				list.add((T) getAction.getResult(rs, meta.labels));
			}
			return list;
		} finally {
			if (rs != null) {
				rs.close();
			}
			if (pstmt != null) {
				pstmt.close();
			}
		}
	}

	// 对于SQL，不能简单使用LIMIT语法获取第一条记录
	@SuppressWarnings("unchecked")
	protected <T> T queryFirst(Connection conn, String queryId, Class<T> elemType, Object params) throws SQLException {
		SqlMeta meta = configSqlMetaCache.get(queryId);

		if (meta == null) {
			throw new SQLException("Not sql config: " + queryId);
		} else if (showSql) {
			logger.info("Sql for queryFirst: " + meta);
		}

		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try {
			pstmt = conn.prepareStatement(meta.psql);
			if (params != null) {
				JdbcAction setAction = getJdbcAction(params.getClass());
				setAction.setParam(pstmt, meta.params, params);
			}
			rs = pstmt.executeQuery();
			if (meta.labels == null) {
				SqlMetaKit.fillSqlMetaLables(rs, meta);
			}
			JdbcAction getAction = getJdbcAction(elemType);
			if (rs.next()) {
				return (T) getAction.getResult(rs, meta.labels);
			}
			return null;
		} finally {
			if (rs != null) {
				rs.close();
			}
			if (pstmt != null) {
				pstmt.close();
			}
		}
	}

	@SuppressWarnings("unchecked")
	protected <T> void queryPage(Connection conn, String queryId, Class<T> elemType, Page<T> page, Object params) throws SQLException {

		int start = page.getStart();
		if (start < 0) {
			start = 0;
		}
		int max = page.getMax();
		if (max <= 0) {
			max = Integer.MAX_VALUE;
		}

		SqlMeta meta = configSqlMetaCache.get(queryId);
		String psql = SqlMetaKit.modifyPsqlForPageLimit(meta, start, max, page.getOrderBy(), page.isOrderDesc());
		if (meta == null) {
			throw new SQLException("Not sql config: " + queryId);
		} else if (showSql) {
			logger.info("Sql for queryPage: " + meta.toString(psql));
		}

		PreparedStatement pstmt = null;
		ResultSet rs = null;
		JdbcAction setAction = null;

		try {
			pstmt = conn.prepareStatement(psql);
			if (params != null) {
				if (setAction == null) {
					setAction = getJdbcAction(params.getClass());
				}
				setAction.setParam(pstmt, meta.params, params);
			}
			rs = pstmt.executeQuery();
			if (meta.labels == null) {
				SqlMetaKit.fillSqlMetaLables(rs, meta);
			}
			JdbcAction getAction = getJdbcAction(elemType);
			List<T> data = new LinkedList<T>();
			while (rs.next()) {
				data.add((T) getAction.getResult(rs, meta.labels));
			}
			page.setData(data);
		} finally {
			if (rs != null) {
				rs.close();
			}
			if (pstmt != null) {
				pstmt.close();
			}
		}

		if ((start == 0 && max == Integer.MAX_VALUE) || (page.getData().size() < max)) {
			page.setTotal(page.getData().size() + start); // 加上起点
		} else {
			try {
				pstmt = conn.prepareStatement(SqlMetaKit.modifyPsqlForPageTotal(meta));
				if (params != null) {
					if (setAction == null) {
						setAction = getJdbcAction(params.getClass());
					}
					setAction.setParam(pstmt, meta.params, params);
				}
				rs = pstmt.executeQuery();
				if (rs.next()) {
					page.setTotal(rs.getInt(1));
				}
			} finally {
				if (rs != null) {
					rs.close();
				}
				if (pstmt != null) {
					pstmt.close();
				}
			}
		}
	}

	protected <T> int execute(Connection conn, String updateId, Object params) throws SQLException {

		SqlMeta meta = configSqlMetaCache.get(updateId);

		if (meta == null) {
			throw new SQLException("Not sql config: " + updateId);
		} else if (showSql) {
			logger.info("Sql for execute: " + meta);
		}

		PreparedStatement pstmt = null;
		try {
			pstmt = conn.prepareStatement(meta.psql);
			if (params != null) {
				JdbcAction setAction = getJdbcAction(params.getClass());
				setAction.setParam(pstmt, meta.params, params);
			}
			return pstmt.executeUpdate();
		} finally {
			if (pstmt != null) {
				pstmt.close();
			}
		}
	}

	protected <R> R execute(Connection conn, String updateId, Object params, Class<R> generatedKeyType) throws SQLException {

		SqlMeta meta = configSqlMetaCache.get(updateId);

		if (meta == null) {
			throw new SQLException("Not sql config: " + updateId);
		} else if (showSql) {
			logger.info("Sql for execute: " + meta);
		}

		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try {
			pstmt = conn.prepareStatement(meta.psql, Statement.RETURN_GENERATED_KEYS);
			if (params != null) {
				JdbcAction setAction = getJdbcAction(params.getClass());
				setAction.setParam(pstmt, meta.params, params);
			}
			pstmt.executeUpdate();
			rs = pstmt.getGeneratedKeys();
			if (rs.next()) {
				return JdbcAction.getResultByType(rs, 1, generatedKeyType);
			}
			return null;
		} finally {
			if (rs != null) {
				rs.close();
			}
			if (pstmt != null) {
				pstmt.close();
			}
		}
	}

	protected int[] batchExecute(Connection conn, String updateId, Object[] params) throws SQLException {
		SqlMeta meta = configSqlMetaCache.get(updateId);

		if (meta == null) {
			throw new SQLException("Not sql config: " + updateId);
		} else if (showSql) {
			logger.info("Sql for batchExecute: " + meta);
		}

		if (conn.getAutoCommit()) {
			logger.warn("Not setAutoCommit(false) before executing batchExecute");
		}

		JdbcAction setAction = getJdbcAction(params[0].getClass());
		PreparedStatement pstmt = null;
		try {
			pstmt = conn.prepareStatement(meta.psql);
			for (Object param : params) {
				setAction.setParam(pstmt, meta.params, param);
				pstmt.addBatch();
			}
			return pstmt.executeBatch();
		} finally {
			if (pstmt != null) {
				pstmt.close();
			}
		}
	}

	protected <R> R[] batchExecute(Connection conn, String updateId, Object[] params, Class<R> generatedKeyType) throws SQLException {

		SqlMeta meta = configSqlMetaCache.get(updateId);

		if (meta == null) {
			throw new SQLException("Not sql config: " + updateId);
		} else if (showSql) {
			logger.info("Sql for batchExecute: " + meta);
		}

		if (conn.getAutoCommit()) {
			logger.warn("Not setAutoCommit(false) before executing batchExecute");
		}

		JdbcAction setAction = getJdbcAction(params[0].getClass());
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try {
			pstmt = conn.prepareStatement(meta.psql, Statement.RETURN_GENERATED_KEYS);
			for (Object param : params) {
				setAction.setParam(pstmt, meta.params, param);
				pstmt.addBatch();
			}
			int[] rows = pstmt.executeBatch();
			@SuppressWarnings("unchecked")
			R[] result = (R[]) Array.newInstance(generatedKeyType, rows.length);
			rs = pstmt.getGeneratedKeys();
			for (int i = 0; i < result.length; i++) {
				if (rows[i] > 0 && rs.next()) {
					result[i] = JdbcAction.getResultByType(rs, 1, generatedKeyType);
				}
			}
			return result;
		} finally {
			if (rs != null) {
				rs.close();
			}
			if (pstmt != null) {
				pstmt.close();
			}
		}
	}

	/*****************************************************************
	 * 初始化方法，解析Meta. 注意ClassLoader的影响
	 *****************************************************************/
	protected void init(Connection conn) throws Exception {

		final Pattern separator = Pattern.compile("\\s*,\\s*");
		final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

		// 提取classMetaInfo与sql. 如果重复则自动忽略
		Map<String, ClassMetaInfo> classMetaInfoMap = new HashMap<String, ClassMetaInfo>(); // 以className为key
		Map<String, ClassMetaInfo> tableMetaInfoMap = new HashMap<String, ClassMetaInfo>(); // 以tableName为key
		Map<String, String> sqlMap = new HashMap<String, String>();

		ClassMetaInfo classMetaInfo, tableMetaInfo;
		StringBuilder sb = new StringBuilder(128);
		String key;

		if (isNotEmpty(packagesToScan)) {
			String[] pkgs = separator.split(packagesToScan);
			for (String pkg : pkgs) {
				if (isNotEmpty(pkg)) {
					sb.setLength(0);
					String packageSearchPath = sb.append(ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX).append(AsmKit.getInternalNameFromClassName(pkg)).append("/**/*.class").toString();
					Resource[] rss = resolver.getResources(packageSearchPath);
					for (Resource rs : rss) {
						classMetaInfo = AsmKit.getAnnotationClassMetaInfo(rs);
						// 带有@Table或@Meta才处理
						if (classMetaInfo.tableAnnotation != null || classMetaInfo.metaAnnotation != null) {
							classMetaInfoMap.put(AsmKit.getClassNameFromInternalName(classMetaInfo.internalName), classMetaInfo);
							if (classMetaInfo.tableAnnotation != null) {
								if (logger.isInfoEnabled()) {
									logger.info(String.format("Load @table：%s %s", classMetaInfo.tableName, classMetaInfo.columns));
								}
								if ((tableMetaInfo = tableMetaInfoMap.put(classMetaInfo.tableName, classMetaInfo)) != null) {
									throw new MysqlClientException("Duplicate @Table：" + classMetaInfo.tableName + ", please check class:" + classMetaInfo.internalName + "," + tableMetaInfo.internalName);
								}
							}
						}
					}
				}
			}
		}
		if (isNotEmpty(configLocations)) {
			String[] locations = separator.split(configLocations);
			for (String location : locations) {
				if (isNotEmpty(location)) {
					Resource[] rss = resolver.getResources(location);
					for (Resource rs : rss) {
						ConfigMetaInfo configMetaInfo = ConfigSAXParser.parse(rs);
						for (String className : configMetaInfo.tables) {
							// 如果存在不再解析
							if (!classMetaInfoMap.containsKey(className)) {
								classMetaInfo = AsmKit.getAnnotationClassMetaInfo(className);
								// 带有@Table或@Meta才处理
								if (classMetaInfo.tableAnnotation != null || classMetaInfo.metaAnnotation != null) {
									classMetaInfoMap.put(className, classMetaInfo);
									if (classMetaInfo.tableAnnotation != null) {
										if (logger.isInfoEnabled()) {
											logger.info(String.format("Load @table：%s %s", classMetaInfo.tableName, classMetaInfo.columns));
										}
										if ((tableMetaInfo = tableMetaInfoMap.put(classMetaInfo.tableName, classMetaInfo)) != null) {
											throw new MysqlClientException("Duplicate @Table：" + classMetaInfo.tableName + ", please check class:" + classMetaInfo.internalName + "," + tableMetaInfo.internalName);
										}
									}
								}
							}
						}
						for (String meta : configMetaInfo.metas) {
							// 如果存在不再解析
							if (!classMetaInfoMap.containsKey(meta)) {
								classMetaInfo = AsmKit.getClassMetaInfo(meta);
								classMetaInfo.tableAnnotation = null; // FIXBUG:<meta>的类不是t<table>
								classMetaInfoMap.put(meta, classMetaInfo);
							}
						}
						for (Map.Entry<String, String> entry : configMetaInfo.sqls.entrySet()) {
							// key = namespace + . + id
							sb.setLength(0);
							if (AsmKit.isNotEmpty(configMetaInfo.namespace)) {
								sb.append(configMetaInfo.namespace).append('.');// 没有namespace,自动忽略
							}
							key = sb.append(entry.getKey()).toString();
							if (!sqlMap.containsKey(key)) {
								sqlMap.put(key, entry.getValue());
							}
						}
					}
				}
			}
		}

		// 从classMetaInfo解析出SqlAction， SqlMeta.并存入Repository
		Class<?> clazz;
		for (Map.Entry<String, ClassMetaInfo> entry : classMetaInfoMap.entrySet()) {

			clazz = Loader.loadClassAssertNotFailed(entry.getKey());
			classMetaInfo = entry.getValue();

			setJdbcAction(clazz, AsmKit.newJdbcAction(classMetaInfo));

			selectAllSqlMetaCache.put(clazz, SqlMetaKit.genSelectListSqlMeta(classMetaInfo));
			selectSqlMetaCache.put(clazz, SqlMetaKit.genSelectSqlMeta(classMetaInfo));
			insertSqlMetaCache.put(clazz, SqlMetaKit.genInsertSqlMeta(classMetaInfo));
			updateSqlMetaCache.put(clazz, SqlMetaKit.genUpdateSqlMeta(classMetaInfo));
			replaceSqlMetaCache.put(clazz, SqlMetaKit.genReplaceSqlMeta(classMetaInfo));
			mergeSqlMetaCache.put(clazz, SqlMetaKit.genMergeSqlMeta(classMetaInfo));
			deleteSqlMetaCache.put(clazz, SqlMetaKit.genDeleteSqlMeta(classMetaInfo));

		}
		// 从sql解析出SqlMeta并存入Repository
		for (Map.Entry<String, String> entry : sqlMap.entrySet()) {
			configSqlMetaCache.put(entry.getKey(), SqlMetaKit.genConfigSqlMeta(entry.getKey(), entry.getValue()));
		}

		if (updateTable) {
			// 从classMetaInfo解析出ddlMeta,并更新表结构
			SqlDdlKit.processUpdateTable(conn, tableMetaInfoMap.values());
		}

		if (checkConfig) {
			SqlDdlKit.processCheckConfig(conn, selectAllSqlMetaCache.values());
			SqlDdlKit.processCheckConfig(conn, selectSqlMetaCache.values());
			SqlDdlKit.processCheckConfig(conn, insertSqlMetaCache.values());
			SqlDdlKit.processCheckConfig(conn, updateSqlMetaCache.values());
			SqlDdlKit.processCheckConfig(conn, replaceSqlMetaCache.values());
			SqlDdlKit.processCheckConfig(conn, mergeSqlMetaCache.values());
			SqlDdlKit.processCheckConfig(conn, deleteSqlMetaCache.values());
			SqlDdlKit.processCheckConfig(conn, configSqlMetaCache.values());
		}

		if (logger.isInfoEnabled()) {
			logger.info(String.format("mysqlclient initialization successful, loading %d Tables, %d Metas, and %d SQLs", tableMetaInfoMap.size(), classMetaInfoMap.size(), sqlMap.size()));
		}
	}

	/******************************************************
	 * SqlMeta全局缓存池
	 ******************************************************/
	final Map<Class, SqlMeta> selectAllSqlMetaCache = new HashMap<Class, SqlMeta>();
	final Map<Class, SqlMeta> selectSqlMetaCache = new HashMap<Class, SqlMeta>();
	final Map<Class, SqlMeta> insertSqlMetaCache = new HashMap<Class, SqlMeta>();
	final Map<Class, SqlMeta> updateSqlMetaCache = new HashMap<Class, SqlMeta>();
	final Map<Class, SqlMeta> replaceSqlMetaCache = new HashMap<Class, SqlMeta>();
	final Map<Class, SqlMeta> mergeSqlMetaCache = new HashMap<Class, SqlMeta>();
	final Map<Class, SqlMeta> deleteSqlMetaCache = new HashMap<Class, SqlMeta>();
	final Map<String, SqlMeta> configSqlMetaCache = new HashMap<String, SqlMeta>();

	/******************************************************
	 * SqlAction全局缓存池,可能产生很多，允许释放再生成
	 ******************************************************/
	// JdbcAction分成2种：持久 与临时，后者内存不足时会去除
	final Map<Class, JdbcAction> persistJdbcActionCache = new HashMap<Class, JdbcAction>();
	final Map<Class, SoftReference<JdbcAction>> tempoJdbcActionCache = new HashMap<Class, SoftReference<JdbcAction>>();

	{ // scalar jdbcAction
		persistJdbcActionCache.put(boolean.class, JdbcAction.BooleanJdbcAction);
		persistJdbcActionCache.put(Boolean.class, JdbcAction.BooleanJdbcAction);
		persistJdbcActionCache.put(char.class, JdbcAction.CharacterJdbcAction);
		persistJdbcActionCache.put(Character.class, JdbcAction.CharacterJdbcAction);
		persistJdbcActionCache.put(byte.class, JdbcAction.ByteJdbcAction);
		persistJdbcActionCache.put(Byte.class, JdbcAction.ByteJdbcAction);
		persistJdbcActionCache.put(short.class, JdbcAction.ShortJdbcAction);
		persistJdbcActionCache.put(Short.class, JdbcAction.ShortJdbcAction);
		persistJdbcActionCache.put(int.class, JdbcAction.IntegerJdbcAction);
		persistJdbcActionCache.put(Integer.class, JdbcAction.IntegerJdbcAction);
		persistJdbcActionCache.put(long.class, JdbcAction.LongJdbcAction);
		persistJdbcActionCache.put(Long.class, JdbcAction.LongJdbcAction);
		persistJdbcActionCache.put(float.class, JdbcAction.FloatJdbcAction);
		persistJdbcActionCache.put(Float.class, JdbcAction.FloatJdbcAction);
		persistJdbcActionCache.put(double.class, JdbcAction.DoubleJdbcAction);
		persistJdbcActionCache.put(String.class, JdbcAction.StringJdbcAction);
		persistJdbcActionCache.put(Double.class, JdbcAction.DoubleJdbcAction);
		persistJdbcActionCache.put(BigDecimal.class, JdbcAction.BigDecimalJdbcAction);
		persistJdbcActionCache.put(BigInteger.class, JdbcAction.BigIntegerJdbcAction);
		persistJdbcActionCache.put(java.util.Date.class, JdbcAction.JavaUtilDateJdbcAction);
		persistJdbcActionCache.put(Date.class, JdbcAction.DateJdbcAction);
		persistJdbcActionCache.put(Time.class, JdbcAction.TimeJdbcAction);
		persistJdbcActionCache.put(Timestamp.class, JdbcAction.TimestampJdbcAction);
		persistJdbcActionCache.put(Ref.class, JdbcAction.RefJdbcAction);
		persistJdbcActionCache.put(URL.class, JdbcAction.URLJdbcAction);
		persistJdbcActionCache.put(SQLXML.class, JdbcAction.SQLXMLJdbcAction);
		persistJdbcActionCache.put(Blob.class, JdbcAction.BlobJdbcAction);
		persistJdbcActionCache.put(Clob.class, JdbcAction.ClobJdbcAction);
		persistJdbcActionCache.put(InputStream.class, JdbcAction.InputStreamJdbcAction);
		persistJdbcActionCache.put(Reader.class, JdbcAction.ReaderJdbcAction);
		persistJdbcActionCache.put(byte[].class, JdbcAction.BytesJdbcAction);
	}

	public final void setJdbcAction(Class type, JdbcAction jdbcAction) {
		if (!persistJdbcActionCache.containsKey(type)) {
			synchronized (persistJdbcActionCache) {
				if (!persistJdbcActionCache.containsKey(type)) {
					persistJdbcActionCache.put(type, jdbcAction);
				}
			}
		}
	}

	protected final JdbcAction getJdbcAction(Class type) throws MysqlClientException {

		// 特殊
		if (type == null) {
			return JdbcAction.ARRAY_JDBC_ACTION;
		} else if (Map.class.isAssignableFrom(type)) {
			return JdbcAction.MAP_JDBC_ACTION;
		} else if (List.class.isAssignableFrom(type)) {
			return JdbcAction.LIST_JDBC_ACTION;
		}
		// 持久
		JdbcAction action = persistJdbcActionCache.get(type);
		if (action == null) {
			// 临时
			SoftReference<JdbcAction> ref = tempoJdbcActionCache.get(type);
			if (ref == null || (action = ref.get()) == null) {
				// 不支持基本类型与数组类型
				if (type.isArray() || type.isEnum() || type.isInterface() || type.isAnnotation()) {
					throw new MysqlClientException("JdbcAction don't support array, enum, interface, or annoation type:" + type.getCanonicalName());
				}
				synchronized (tempoJdbcActionCache) {
					ref = tempoJdbcActionCache.get(type);
					if (ref == null || (action = ref.get()) == null) {
						try {
							action = AsmKit.newJdbcAction(type.getCanonicalName());
							ref = new SoftReference<JdbcAction>(action);
							tempoJdbcActionCache.put(type, ref);
						} catch (Exception e) {
							throw new MysqlClientException(e);
						}
					}
				}
			}
		}
		return action;
	}

}
