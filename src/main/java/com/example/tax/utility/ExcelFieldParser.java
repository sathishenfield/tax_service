package com.example.tax.utility;

import org.apache.poi.ss.usermodel.DateUtil;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.function.Function;

@Component
public class ExcelFieldParser {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS");

    public static final Map<Class<?>, Function<String, ?>> PARSERS = Map.ofEntries(
            Map.entry(String.class, Function.identity()),
            Map.entry(Double.class, (Function<String, Double>) Double::valueOf),
            Map.entry(Integer.class, (Function<String, Integer>) Integer::valueOf),
            Map.entry(LocalDate.class, (Function<String, LocalDate>) LocalDate::parse),
            Map.entry(Instant.class, (Function<String, Instant>) s -> {
                try {
                    LocalDateTime ldt = LocalDateTime.parse(s, DATE_TIME_FORMATTER);
                    return ldt.toInstant(ZoneOffset.UTC);
                } catch (Exception e) {
                    double excelDate = Double.parseDouble(s);
                    return Instant.ofEpochMilli(DateUtil.getJavaDate(excelDate).getTime());
                }
            })
    );


}
