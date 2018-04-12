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
import java.sql.SQLException;
import java.util.Map;

import org.apache.cayenne.dba.DbAdapter;
import org.apache.cayenne.dba.TypesMapping;
import org.apache.cayenne.dbsync.reverse.filters.CatalogFilter;
import org.apache.cayenne.dbsync.reverse.filters.PatternFilter;
import org.apache.cayenne.dbsync.reverse.filters.SchemaFilter;
import org.apache.cayenne.map.Procedure;
import org.apache.cayenne.map.ProcedureParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcedureColumnLoader extends PerCatalogAndSchemaLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(DbLoader.class);

    ProcedureColumnLoader(final DbAdapter adapter, final DbLoaderConfiguration config, final DbLoaderDelegate delegate) {
        super(adapter, config, delegate);
    }

    @Override
    protected ResultSet getResultSet(final String catalogName, final String schemaName, final DatabaseMetaData metaData) throws SQLException {
        return metaData.getProcedureColumns(catalogName, schemaName, WILDCARD, WILDCARD);
    }

    @Override
    protected boolean shouldLoad(final CatalogFilter catalog, final SchemaFilter schema) {
        final PatternFilter filter = config.getFiltersConfig().proceduresFilter(catalog.name, schema.name);
        return !filter.isEmpty();
    }

    @Override
    protected void processResultSetRow(final CatalogFilter catalog, final SchemaFilter schema, final DbLoadDataStore map, final ResultSet rs,
                                       final Map<String, Integer> typeMaxSizeMap) throws SQLException {
        final String procSchema = rs.getString("PROCEDURE_SCHEM");
        final String procCatalog = rs.getString("PROCEDURE_CAT");
        final String name = rs.getString("PROCEDURE_NAME");
        final String key = Procedure.generateFullyQualifiedName(procCatalog, procSchema, name);
        final Procedure procedure = map.getProcedure(key);
        if (procedure == null) {
            return;
        }

        final ProcedureParameter column = loadProcedureParams(rs, key, procedure);
        if (column == null) {
            return;
        }
        procedure.addCallParameter(column);
    }

    private ProcedureParameter loadProcedureParams(final ResultSet rs, final String key, final Procedure procedure) throws SQLException {
        String columnName = rs.getString("COLUMN_NAME");

        // skip ResultSet columns, as they are not described in Cayenne procedures yet...
        short type = rs.getShort("COLUMN_TYPE");
        if (type == DatabaseMetaData.procedureColumnResult) {
            LOGGER.debug("skipping ResultSet column: " + key + "." + columnName);
            return null;
        }

        if (columnName == null) {
            if (type == DatabaseMetaData.procedureColumnReturn) {
                LOGGER.debug("null column name, assuming result column: " + key);
                columnName = "_return_value";
                procedure.setReturningValue(true);
            } else {
                LOGGER.info("invalid null column name, skipping column : " + key);
                return null;
            }
        }

        int columnType = rs.getInt("DATA_TYPE");

        // ignore precision of non-decimal columns
        int decimalDigits = -1;
        if (TypesMapping.isDecimal(columnType)) {
            decimalDigits = rs.getShort("SCALE");
            if (rs.wasNull()) {
                decimalDigits = -1;
            }
        }

        final ProcedureParameter column = new ProcedureParameter(columnName);
        column.setDirection(getDirection(type));
        column.setType(columnType);
        column.setMaxLength(rs.getInt("LENGTH"));
        column.setPrecision(decimalDigits);
        column.setProcedure(procedure);

        return column;
    }

    private int getDirection(short type) {
        switch (type) {
            case DatabaseMetaData.procedureColumnIn:
                return ProcedureParameter.IN_PARAMETER;
            case DatabaseMetaData.procedureColumnInOut:
                return ProcedureParameter.IN_OUT_PARAMETER;
            case DatabaseMetaData.procedureColumnOut:
                return ProcedureParameter.OUT_PARAMETER;
            default:
                return -1;
        }
    }
}
