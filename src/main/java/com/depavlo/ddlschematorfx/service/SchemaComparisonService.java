package com.depavlo.ddlschematorfx.service;

import com.depavlo.ddlschematorfx.model.Difference;
import com.depavlo.ddlschematorfx.model.DifferenceType;
import com.depavlo.ddlschematorfx.model.ObjectType;
import com.depavlo.ddlschematorfx.model.Schema;
import com.manticore.jsqlformatter.JSQLFormatter;
import org.apache.commons.collections4.keyvalue.MultiKey; // Імпорт MultiKey

import java.util.ArrayList;
import java.util.List;
import java.util.Map; // Для ітерації по entrySet

public class SchemaComparisonService {

    public List<Difference> compareSchemas(Schema sourceSchema, Schema targetSchema) {
        if (sourceSchema == null || targetSchema == null) {
            throw new IllegalArgumentException("Source and target schemas cannot be null.");
        }

        List<Difference> differences = new ArrayList<>();
        // Отримуємо MultiKeyMap з DDL об'єктів
        Map<MultiKey<?>, String> sourceObjects = sourceSchema.getObjectDdls();
        Map<MultiKey<?>, String> targetObjects = targetSchema.getObjectDdls();

        JSQLFormatter formatter = new JSQLFormatter();

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
                            sourceSchema.getName(), // Власник схеми-джерела
                            sourceEntry.getValue(),
                            null,
                            "Object removed from target schema (Owner: " + sourceSchema.getName() + ")"
                    ));
                }
            }
        }

        // Об'єкти, що є в target (ADDED або MODIFIED)
        for (Map.Entry<MultiKey<?>, String> targetEntry : targetObjects.entrySet()) {
            MultiKey<?> targetMultiKey = targetEntry.getKey();
            String targetDdl = targetEntry.getValue();

            if (targetMultiKey.size() == 2 && targetMultiKey.getKey(0) instanceof ObjectType && targetMultiKey.getKey(1) instanceof String) {
                ObjectType objectType = (ObjectType) targetMultiKey.getKey(0);
                String objectName = (String) targetMultiKey.getKey(1);

                String formattedTargetDdl = "";
                if (targetDdl != null && !targetDdl.trim().isEmpty()) {
                    try {
                        formattedTargetDdl = formatter.format(targetDdl);
                    } catch (Exception e) {
                        System.err.println("Warning: Could not format target DDL for object " + objectType + "/" + objectName + ". Using raw DDL. Error: " + e.getMessage());
                        formattedTargetDdl = targetDdl;
                    }
                }

                if (!sourceObjects.containsKey(targetMultiKey)) {
                    // Об'єкт додано (є в target, але немає в source)
                    differences.add(new Difference(
                            DifferenceType.ADDED,
                            objectType,
                            objectName,
                            targetSchema.getName(), // Власник цільової схеми
                            null,
                            targetDdl,
                            "Object added to target schema (Owner: " + targetSchema.getName() + ")"
                    ));
                } else {
                    // Об'єкт існує в обох схемах, перевіряємо на зміни
                    String sourceDdl = sourceObjects.get(targetMultiKey);
                    String formattedSourceDdl = "";
                    if (sourceDdl != null && !sourceDdl.trim().isEmpty()) {
                        try {
                            formattedSourceDdl = formatter.format(sourceDdl);
                        } catch (Exception e) {
                            System.err.println("Warning: Could not format source DDL for object " + objectType + "/" + objectName + ". Using raw DDL. Error: " + e.getMessage());
                            formattedSourceDdl = sourceDdl;
                        }
                    }

                    if (!formattedSourceDdl.equals(formattedTargetDdl)) {
                        differences.add(new Difference(
                                DifferenceType.MODIFIED,
                                objectType,
                                objectName,
                                targetSchema.getName(), // Або sourceSchema.getName(), залежно від логіки
                                sourceDdl,
                                targetDdl,
                                "Object DDL has been modified (Compared owners: Source '" + sourceSchema.getName() + "', Target '" + targetSchema.getName() + "')"
                        ));
                    }
                }
            }
        }
        return differences;
    }
    // Допоміжні методи parseObjectKey та getObjectTypeFromString тут більше не потрібні,
    // оскільки ми працюємо безпосередньо з ObjectType та objectName з MultiKey.
}
