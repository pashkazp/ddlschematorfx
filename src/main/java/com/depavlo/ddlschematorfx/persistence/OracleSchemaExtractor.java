package com.depavlo.ddlschematorfx.persistence;

import com.depavlo.ddlschematorfx.model.ConnectionDetails;
import com.depavlo.ddlschematorfx.model.ObjectType;
import com.depavlo.ddlschematorfx.model.Schema;
import org.apache.commons.collections4.map.MultiKeyMap;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
// import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class OracleSchemaExtractor {

    private static final String GET_SCHEMA_OBJECTS_SQL_TEMPLATE =
            "SELECT object_name, object_type FROM all_objects WHERE owner = ? AND object_type IN (%s) AND status = 'VALID' AND NOT (object_type = 'SEQUENCE' AND object_name LIKE 'ISEQ$$_%%')";

    private static final String GET_DDL_SQL =
            "{ ? = call DBMS_METADATA.GET_DDL(?, ?, ?) }";

    public Schema extractSchema(ConnectionDetails connectionDetails, String schemaOwnerName) throws SQLException {
        if (connectionDetails == null || schemaOwnerName == null || schemaOwnerName.trim().isEmpty()) {
            throw new IllegalArgumentException("Connection details and schema owner name must be provided.");
        }
        if (connectionDetails.getId() == null || connectionDetails.getId().trim().isEmpty()){
            throw new IllegalArgumentException("ConnectionDetails ID must not be null or empty to create a source identifier.");
        }

        MultiKeyMap<Object, String> objectDdlsMap = new MultiKeyMap<>();

        try (Connection connection = DriverManager.getConnection(
                connectionDetails.getUrl(),
                connectionDetails.getUser(),
                connectionDetails.getPassword())) {

            ObjectType[] objectTypesToExtract = {
                    ObjectType.TABLE, ObjectType.VIEW, ObjectType.SEQUENCE,
                    ObjectType.PROCEDURE, ObjectType.FUNCTION, ObjectType.PACKAGE,
                    ObjectType.TRIGGER, ObjectType.INDEX,
                    ObjectType.MATERIALIZED_VIEW, ObjectType.SYNONYM, ObjectType.DATABASE_LINK,
                    ObjectType.DIRECTORY_OBJECT, ObjectType.JOB, ObjectType.QUEUE,
                    ObjectType.TYPE, ObjectType.JAVA_SOURCE, ObjectType.LIBRARY,
                    ObjectType.SCHEDULER, ObjectType.XML_SCHEMA
            };

            String objectTypesSqlString = buildObjectTypesSqlString(objectTypesToExtract);

            try (PreparedStatement pstmt = connection.prepareStatement(String.format(GET_SCHEMA_OBJECTS_SQL_TEMPLATE, objectTypesSqlString))) {
                pstmt.setString(1, schemaOwnerName.toUpperCase());

                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        String objectName = rs.getString("object_name");
                        String objectTypeString = rs.getString("object_type");

                        ObjectType currentObjectType = getObjectTypeFromString(objectTypeString);
                        if (currentObjectType == ObjectType.OTHER && !"CONSTRAINT".equals(objectTypeString)) {
                            System.out.println("Пропущено невідомий тип об'єкта: " + objectTypeString + " для " + objectName);
                            continue;
                        }

                        String ddl = getObjectDdl(connection, objectTypeString, objectName, schemaOwnerName);
                        if (ddl != null) {
                            objectDdlsMap.put(currentObjectType, objectName, ddl);
                            // System.out.println("Витягнуто DDL для: " + currentObjectType + "/" + objectName + " (Власник: " + schemaOwnerName + ")");
                        } else {
                            // System.out.println("Порожній DDL для: " + currentObjectType + "/" + objectName + " (Власник: " + schemaOwnerName + "). Пропущено.");
                        }
                    }
                }
            }

            String schemaId = UUID.randomUUID().toString();
            LocalDateTime extractionTimestamp = LocalDateTime.now();
            String currentSourceIdentifier = "DB::" + connectionDetails.getId() + "::" + schemaOwnerName.toUpperCase();

            // Використовуємо конструктор, який встановлює originalSourceIdentifier = currentSourceIdentifier
            return new Schema(schemaId, schemaOwnerName, objectDdlsMap, extractionTimestamp, connectionDetails, currentSourceIdentifier);

        } catch (SQLException e) {
            System.err.println("Помилка витягнення схеми '" + schemaOwnerName + "': " + e.getMessage());
            throw e;
        } catch (Exception e) {
            System.err.println("Невідома помилка при витягненні схеми '" + schemaOwnerName + "': " + e.getMessage());
            e.printStackTrace();
            throw new SQLException("Невідома помилка при витягненні схеми", e);
        }
    }

    private String buildObjectTypesSqlString(ObjectType[] objectTypes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < objectTypes.length; i++) {
            String typeForSql = objectTypes[i].name().replace("_", " ");
            sb.append("'").append(typeForSql).append("'");
            if (i < objectTypes.length - 1) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    private String getObjectDdl(Connection connection, String objectTypeForDbmsMetadata, String objectName, String schemaName) throws SQLException {
        String ddl = null;
        Map<String, String> dbmsMetadataTypeMap = new HashMap<>();
        dbmsMetadataTypeMap.put("JOB", "PROCOBJ");

        String effectiveObjectType = dbmsMetadataTypeMap.getOrDefault(objectTypeForDbmsMetadata, objectTypeForDbmsMetadata);

        try (CallableStatement cs = connection.prepareCall(GET_DDL_SQL)) {
            cs.registerOutParameter(1, java.sql.Types.CLOB);
            cs.setString(2, effectiveObjectType);
            cs.setString(3, objectName);
            cs.setString(4, schemaName);
            cs.execute();

            java.sql.Clob clob = cs.getClob(1);
            if (clob != null) {
                if (clob.length() > 0) {
                    ddl = clob.getSubString(1, (int) clob.length());
                } else {
                    ddl = "";
                }
                clob.free();
            }
        } catch (SQLException e) {
            System.err.println("Помилка отримання DDL для " + effectiveObjectType + " " + schemaName + "." + objectName + ": " + e.getMessage());
            return null;
        }
        return ddl;
    }

    private ObjectType getObjectTypeFromString(String objectTypeString) {
        if (objectTypeString == null || objectTypeString.trim().isEmpty()) {
            return ObjectType.OTHER;
        }
        String normalizedType = objectTypeString.toUpperCase().replace(" ", "_");
        try {
            return ObjectType.valueOf(normalizedType);
        } catch (IllegalArgumentException e) {
            if ("MATERIALIZED_VIEW".equals(normalizedType)) return ObjectType.MATERIALIZED_VIEW;
            if ("DATABASE_LINK".equals(normalizedType)) return ObjectType.DATABASE_LINK;
            System.err.println("Попередження: Невідомий тип об'єкта '" + objectTypeString + "' (нормалізовано як '" + normalizedType + "'). Повертається OTHER.");
            return ObjectType.OTHER;
        }
    }
}
