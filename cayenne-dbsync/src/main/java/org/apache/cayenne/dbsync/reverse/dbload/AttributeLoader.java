/*****************************************************************
 *   Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 ****************************************************************/

package org.apache.cayenne.dbsync.reverse.dbload;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Map;

import org.apache.cayenne.dba.DbAdapter;
import org.apache.cayenne.dba.TypesMapping;
import org.apache.cayenne.dbsync.reverse.filters.CatalogFilter;
import org.apache.cayenne.dbsync.reverse.filters.PatternFilter;
import org.apache.cayenne.dbsync.reverse.filters.SchemaFilter;
import org.apache.cayenne.map.DbAttribute;
import org.apache.cayenne.map.DbEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class AttributeLoader extends PerCatalogAndSchemaLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(DbLoader.class);

    private boolean firstRow;
    private boolean supportAutoIncrement;

    AttributeLoader(final DbAdapter adapter, final DbLoaderConfiguration config, final DbLoaderDelegate delegate) {
        super(adapter, config, delegate);
        firstRow = true;
        supportAutoIncrement = false;
    }

    protected ResultSet getResultSet(final String catalogName, final String schemaName, final DatabaseMetaData metaData)
            throws SQLException {
        return metaData.getColumns(catalogName, schemaName, WILDCARD, WILDCARD);
    }

    @Override
    protected void processResultSetRow(final CatalogFilter catalog, final SchemaFilter schema, final DbLoadDataStore map,
                                       final ResultSet rs, final Map<String, Integer> lengthMap) throws SQLException {
        if (firstRow) {
            supportAutoIncrement = checkForAutoIncrement(rs);
            firstRow = false;
        }

        // for a reason not quiet apparent to me, Oracle sometimes
        // returns duplicate record sets for the same table, messing up
        // table names. E.g. for the system table "WK$_ATTR_MAPPING" columns
        // are returned twice - as "WK$_ATTR_MAPPING" and "WK$$_ATTR_MAPPING"...
        // Go figure
        final String tableName = rs.getString("TABLE_NAME");
        final DbEntity entity = map.getDbEntity(tableName);
        if (entity == null) {
            return;
        }

        // Filter out columns by name
        final String columnName = rs.getString("COLUMN_NAME");
        final PatternFilter columnFilter = schema.tables.getIncludeTableColumnFilter(tableName);
        if (columnFilter == null || !columnFilter.isIncluded(columnName)) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Skip column '" + tableName + "." + columnName +
                        "' (Path: " + catalog.name + "/" + schema.name + "; Filter: " + columnFilter + ")");
            }
            return;
        }

        final DbAttribute attribute = createDbAttribute(rs, lengthMap);
        addToDbEntity(entity, attribute);
    }

    private boolean checkForAutoIncrement(final ResultSet rs) throws SQLException {
        final ResultSetMetaData rsMetaData = rs.getMetaData();
        for (int i = 1; i <= rsMetaData.getColumnCount(); i++) {
            if ("IS_AUTOINCREMENT".equals(rsMetaData.getColumnLabel(i))) {
                return true;
            }
        }
        return false;
    }

    private void addToDbEntity(final DbEntity entity, final DbAttribute attribute) {
        attribute.setEntity(entity);

        // override existing attributes if it comes again
        if (entity.getAttribute(attribute.getName()) != null) {
            entity.removeAttribute(attribute.getName());
        }
        entity.addAttribute(attribute);
    }

    private DbAttribute createDbAttribute(final ResultSet rs, final Map<String, Integer> typeMaxSizeMap) throws SQLException {

        // gets attribute's (column's) information
        final int columnType = rs.getInt("DATA_TYPE");

        // ignore precision of non-decimal columns
        int decimalDigits = -1;
        if (TypesMapping.isDecimal(columnType)) {
            decimalDigits = rs.getInt("DECIMAL_DIGITS");
            if (rs.wasNull()) {
                decimalDigits = -1;
            }
        }

        // create attribute delegating this task to adapter
        final String columnName = rs.getString("COLUMN_NAME");
        final Integer maxLength = typeMaxSizeMap.get(columnName);
        final int columnSize = rs.getInt("COLUMN_SIZE");
        final DbAttribute attr = adapter.buildAttribute(
                columnName,
                rs.getString("TYPE_NAME"),
                columnType,
                maxLength != null && maxLength < columnSize ? maxLength : columnSize,
                decimalDigits,
                rs.getBoolean("NULLABLE"));

        if (supportAutoIncrement) {
            if ("YES".equals(rs.getString("IS_AUTOINCREMENT"))) {
                attr.setGenerated(true);
            }
        }
        return attr;
    }
}
