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

    private static final String FAILED_TO_FORMAT_START = "-- failed to format start";
    private static final String FAILED_TO_FORMAT_END = "-- failed to format end";

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
            String originalTargetDdl = targetEntry.getValue(); // Зберігаємо оригінальний DDL

            if (targetMultiKey.size() == 2 && targetMultiKey.getKey(0) instanceof ObjectType && targetMultiKey.getKey(1) instanceof String) {
                ObjectType objectType = (ObjectType) targetMultiKey.getKey(0);
                String objectName = (String) targetMultiKey.getKey(1);

                String ddlToCompareTarget = originalTargetDdl; // За замовчуванням використовуємо оригінальний DDL
                if (originalTargetDdl != null && !originalTargetDdl.trim().isEmpty()) {
                    try {
                        String formattedDdl = formatter.format(originalTargetDdl);
                        // Перевіряємо, чи форматування було успішним (без маркерів помилки)
                        if (!formattedDdl.contains(FAILED_TO_FORMAT_START) && !formattedDdl.contains(FAILED_TO_FORMAT_END)) {
                            ddlToCompareTarget = formattedDdl;
                        } else {
                            System.err.println("Warning: JSQLFormatter added failure markers for target DDL of object " + objectType + "/" + objectName + ". Using raw DDL for comparison.");
                        }
                    } catch (Exception e) {
                        System.err.println("Warning: Could not format target DDL for object " + objectType + "/" + objectName + ". Using raw DDL for comparison. Error: " + e.getMessage());
                        // ddlToCompareTarget залишається originalTargetDdl
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
                            originalTargetDdl, // Зберігаємо оригінальний DDL для нового об'єкта
                            "Object added to target schema (Owner: " + targetSchema.getName() + ")"
                    ));
                } else {
                    // Об'єкт існує в обох схемах, перевіряємо на зміни
                    String originalSourceDdl = sourceObjects.get(targetMultiKey); // Зберігаємо оригінальний DDL
                    String ddlToCompareSource = originalSourceDdl; // За замовчуванням використовуємо оригінальний DDL

                    if (originalSourceDdl != null && !originalSourceDdl.trim().isEmpty()) {
                        try {
                            String formattedDdl = formatter.format(originalSourceDdl);
                            // Перевіряємо, чи форматування було успішним (без маркерів помилки)
                            if (!formattedDdl.contains(FAILED_TO_FORMAT_START) && !formattedDdl.contains(FAILED_TO_FORMAT_END)) {
                                ddlToCompareSource = formattedDdl;
                            } else {
                                System.err.println("Warning: JSQLFormatter added failure markers for source DDL of object " + objectType + "/" + objectName + ". Using raw DDL for comparison.");
                            }
                        } catch (Exception e) {
                            System.err.println("Warning: Could not format source DDL for object " + objectType + "/" + objectName + ". Using raw DDL for comparison. Error: " + e.getMessage());
                            // ddlToCompareSource залишається originalSourceDdl
                        }
                    }

                    // Порівнюємо DDL, які були підготовлені (або відформатовані, або оригінальні)
                    if (!ddlToCompareSource.equals(ddlToCompareTarget)) {
                        differences.add(new Difference(
                                DifferenceType.MODIFIED,
                                objectType,
                                objectName,
                                targetSchema.getName(), // Або sourceSchema.getName(), залежно від логіки
                                originalSourceDdl, // Зберігаємо оригінальний DDL
                                originalTargetDdl, // Зберігаємо оригінальний DDL
                                "Object DDL has been modified (Compared owners: Source '" + sourceSchema.getName() + "', Target '" + targetSchema.getName() + "')"
                        ));
                    }
                }
            }
        }
        return differences;
    }
}
