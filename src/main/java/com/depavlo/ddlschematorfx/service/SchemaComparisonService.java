package com.depavlo.ddlschematorfx.service;

import com.depavlo.ddlschematorfx.model.Difference;
import com.depavlo.ddlschematorfx.model.DifferenceType;
import com.depavlo.ddlschematorfx.model.ObjectType;
import com.depavlo.ddlschematorfx.model.Schema;
// JSQLFormatter більше не використовується
// import com.manticore.jsqlformatter.JSQLFormatter;
import org.apache.commons.collections4.keyvalue.MultiKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SchemaComparisonService {

    // Константи FAILED_TO_FORMAT_START та FAILED_TO_FORMAT_END більше не потрібні

    public List<Difference> compareSchemas(Schema sourceSchema, Schema targetSchema) {
        if (sourceSchema == null || targetSchema == null) {
            throw new IllegalArgumentException("Source and target schemas cannot be null.");
        }

        List<Difference> differences = new ArrayList<>();
        Map<MultiKey<?>, String> sourceObjects = sourceSchema.getObjectDdls();
        Map<MultiKey<?>, String> targetObjects = targetSchema.getObjectDdls();

        // JSQLFormatter більше не ініціалізується та не використовується
        // JSQLFormatter formatter = new JSQLFormatter();

        // Об'єкти, що є в source, але відсутні в target (REMOVED)
        for (Map.Entry<MultiKey<?>, String> sourceEntry : sourceObjects.entrySet()) {
            MultiKey<?> sourceMultiKey = sourceEntry.getKey();
            if (!targetObjects.containsKey(sourceMultiKey)) {
                // Перевіряємо, що ключ складається з двох частин: ObjectType та String (objectName)
                if (sourceMultiKey.size() == 2 && sourceMultiKey.getKey(0) instanceof ObjectType && sourceMultiKey.getKey(1) instanceof String) {
                    ObjectType objectType = (ObjectType) sourceMultiKey.getKey(0);
                    String objectName = (String) sourceMultiKey.getKey(1);
                    differences.add(new Difference(
                            DifferenceType.REMOVED,
                            objectType,
                            objectName,
                            sourceSchema.getName(), // Власник схеми-джерела
                            sourceEntry.getValue(), // Оригінальний DDL
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
            String targetDdl = targetEntry.getValue(); // Оригінальний DDL

            // Перевіряємо, що ключ складається з двох частин: ObjectType та String (objectName)
            if (targetMultiKey.size() == 2 && targetMultiKey.getKey(0) instanceof ObjectType && targetMultiKey.getKey(1) instanceof String) {
                ObjectType objectType = (ObjectType) targetMultiKey.getKey(0);
                String objectName = (String) targetMultiKey.getKey(1);

                // Логіка форматування та перевірки маркерів помилок видалена
                // String ddlToCompareTarget = targetDdl;

                if (!sourceObjects.containsKey(targetMultiKey)) {
                    // Об'єкт додано (є в target, але немає в source)
                    differences.add(new Difference(
                            DifferenceType.ADDED,
                            objectType,
                            objectName,
                            targetSchema.getName(), // Власник цільової схеми
                            null,
                            targetDdl, // Оригінальний DDL
                            "Object added to target schema (Owner: " + targetSchema.getName() + ")"
                    ));
                } else {
                    // Об'єкт існує в обох схемах, перевіряємо на зміни
                    String sourceDdl = sourceObjects.get(targetMultiKey); // Оригінальний DDL

                    // Логіка форматування та перевірки маркерів помилок видалена
                    // String ddlToCompareSource = sourceDdl;

                    // Пряме порівняння DDL (припускаємо, що вони вже попередньо відформатовані зовнішнім інструментом)
                    // Перевіряємо на null перед порівнянням, щоб уникнути NullPointerException
                    boolean areDdlsEqual = (sourceDdl == null && targetDdl == null) || (sourceDdl != null && sourceDdl.equals(targetDdl));

                    if (!areDdlsEqual) {
                        differences.add(new Difference(
                                DifferenceType.MODIFIED,
                                objectType,
                                objectName,
                                targetSchema.getName(), // Власник цільової схеми (або sourceSchema.getName(), залежно від логіки відображення)
                                sourceDdl, // Оригінальний DDL
                                targetDdl, // Оригінальний DDL
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
