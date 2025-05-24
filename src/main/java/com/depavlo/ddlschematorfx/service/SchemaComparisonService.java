package com.depavlo.ddlschematorfx.service;

import com.depavlo.ddlschematorfx.model.Difference;
import com.depavlo.ddlschematorfx.model.DifferenceType;
import com.depavlo.ddlschematorfx.model.ObjectType;
import com.depavlo.ddlschematorfx.model.Schema;
import com.depavlo.ddlschematorfx.utils.DdlUtils; // Імпорт утиліт
import org.apache.commons.collections4.keyvalue.MultiKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SchemaComparisonService {

    public List<Difference> compareSchemas(Schema sourceSchema, Schema targetSchema) {
        if (sourceSchema == null || targetSchema == null) {
            throw new IllegalArgumentException("Source and target schemas cannot be null.");
        }

        List<Difference> differences = new ArrayList<>();
        Map<MultiKey<?>, String> sourceObjects = sourceSchema.getObjectDdls();
        Map<MultiKey<?>, String> targetObjects = targetSchema.getObjectDdls();

        // Об'єкти, що є в source, але відсутні в target (REMOVED)
        for (Map.Entry<MultiKey<?>, String> sourceEntry : sourceObjects.entrySet()) {
            MultiKey<?> sourceMultiKey = sourceEntry.getKey();
            if (!targetObjects.containsKey(sourceMultiKey)) {
                if (sourceMultiKey.size() == 2 && sourceMultiKey.getKey(0) instanceof ObjectType && sourceMultiKey.getKey(1) instanceof String) {
                    ObjectType objectType = (ObjectType) sourceMultiKey.getKey(0);
                    String objectName = (String) sourceMultiKey.getKey(1);
                    differences.add(new Difference(
                            DifferenceType.REMOVED,
                            objectType,
                            objectName,
                            sourceSchema.getName(),
                            sourceEntry.getValue(),
                            null,
                            "Object removed from target schema (Owner: " + sourceSchema.getName() + ")"
                    ));
                } else {
                    System.err.println("Warning: Invalid MultiKey structure in sourceObjects for key: " + sourceMultiKey);
                }
            }
        }

        // Об'єкти, що є в target (ADDED або MODIFIED)
        for (Map.Entry<MultiKey<?>, String> targetEntry : targetObjects.entrySet()) {
            MultiKey<?> targetMultiKey = targetEntry.getKey();
            String originalTargetDdl = targetEntry.getValue();

            if (targetMultiKey.size() == 2 && targetMultiKey.getKey(0) instanceof ObjectType && targetMultiKey.getKey(1) instanceof String) {
                ObjectType objectType = (ObjectType) targetMultiKey.getKey(0);
                String objectName = (String) targetMultiKey.getKey(1);

                if (!sourceObjects.containsKey(targetMultiKey)) {
                    // Об'єкт додано
                    differences.add(new Difference(
                            DifferenceType.ADDED,
                            objectType,
                            objectName,
                            targetSchema.getName(),
                            null,
                            originalTargetDdl,
                            "Object added to target schema (Owner: " + targetSchema.getName() + ")"
                    ));
                } else {
                    // Об'єкт існує в обох схемах, перевіряємо на зміни
                    String originalSourceDdl = sourceObjects.get(targetMultiKey);

                    // Нормалізуємо DDL для порівняння, видаляючи префікси схем
                    String normalizedSourceDdl = DdlUtils.stripSchemaPrefixesForComparison(originalSourceDdl, sourceSchema.getName());
                    String normalizedTargetDdl = DdlUtils.stripSchemaPrefixesForComparison(originalTargetDdl, targetSchema.getName());

                    // Додатково, можна нормалізувати регістр та пробільні символи, якщо потрібно
                    // normalizedSourceDdl = normalizedSourceDdl.toUpperCase().replaceAll("\\s+", " ").trim();
                    // normalizedTargetDdl = normalizedTargetDdl.toUpperCase().replaceAll("\\s+", " ").trim();


                    boolean areDdlsEqual = (normalizedSourceDdl == null && normalizedTargetDdl == null) ||
                            (normalizedSourceDdl != null && normalizedSourceDdl.equals(normalizedTargetDdl));

                    if (!areDdlsEqual) {
                        differences.add(new Difference(
                                DifferenceType.MODIFIED,
                                objectType,
                                objectName,
                                targetSchema.getName(),
                                originalSourceDdl, // Зберігаємо оригінальні DDL
                                originalTargetDdl,
                                "Object DDL has been modified (Compared owners: Source '" + sourceSchema.getName() + "', Target '" + targetSchema.getName() + "')"
                        ));
                    }
                }
            } else {
                System.err.println("Warning: Invalid MultiKey structure in targetObjects for key: " + targetMultiKey);
            }
        }
        return differences;
    }
}
