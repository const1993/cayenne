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
import java.util.HashMap;
import java.util.Map;

import org.apache.cayenne.dba.DbAdapter;
import org.apache.cayenne.dbsync.reverse.filters.CatalogFilter;
import org.apache.cayenne.dbsync.reverse.filters.SchemaFilter;

import static java.util.Collections.EMPTY_MAP;

public abstract class PerCatalogAndSchemaLoader extends AbstractLoader {

    public static final String MY_SQL = "MySQL";

    private Map<String, Integer> typeMaxSizehMap = new HashMap<>();

    PerCatalogAndSchemaLoader(final DbAdapter adapter, final DbLoaderConfiguration config, final DbLoaderDelegate delegate) {
        super(adapter, config, delegate);
        typeMaxSizehMap.put("datetime", 6);
        typeMaxSizehMap.put("time", 6);
        typeMaxSizehMap.put("timestamp", 6);
        typeMaxSizehMap.put("date", 6);
    }

    public void load(final DatabaseMetaData metaData, final DbLoadDataStore map) throws SQLException {

        final Map<String, Integer> lengthMap = MY_SQL.equals(metaData.getDatabaseProductName()) ? typeMaxSizehMap : EMPTY_MAP;
        for (CatalogFilter catalog : config.getFiltersConfig().getCatalogs()) {
            for (SchemaFilter schema : catalog.schemas) {
                if(!shouldLoad(catalog, schema)) {
                    continue;
                }
                try (ResultSet rs = getResultSet(catalog.name, schema.name, metaData)) {
                    while (rs.next()) {
                        processResultSetRow(catalog, schema, map, rs, lengthMap);
                    }
                }
            }
        }
    }

    boolean shouldLoad(final CatalogFilter catalog, final SchemaFilter schema) {
        return true;
    }

    abstract ResultSet getResultSet(final String catalogName, final String schemaName, final DatabaseMetaData metaData) throws SQLException;

    abstract void processResultSetRow(final CatalogFilter catalog, final SchemaFilter schema, final DbLoadDataStore map, final ResultSet rs,
                                      final Map<String, Integer> lengthMap) throws SQLException;
}
