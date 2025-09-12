package com.example.tax.service.impl;

import com.core.lib.entity.Transaction;
import com.core.lib.exception.BusinessException;
import com.example.tax.repository.TransactionRepository;
import com.example.tax.service.ExcelParserService;
import lombok.extern.log4j.Log4j2;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import javax.xml.parsers.SAXParserFactory;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.example.tax.utility.ExcelFieldParser.PARSERS;

@Service
@Log4j2
public class ExcelParserServiceImpl implements ExcelParserService {

    private final TransactionRepository transactionRepository;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final int BATCH_SIZE = 30;

    public ExcelParserServiceImpl(TransactionRepository transactionRepository,KafkaTemplate<String, Object> kafkaTemplate) {
        this.transactionRepository = transactionRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void processExcel(MultipartFile file) {
        log.info("Starting Excel processing for file: {}", file.getOriginalFilename());

        try (OPCPackage pkg = OPCPackage.open(file.getInputStream())) {
            XSSFReader reader = new XSSFReader(pkg);
            StylesTable styles = reader.getStylesTable();
            ReadOnlySharedStringsTable strings = new ReadOnlySharedStringsTable(pkg);

            XSSFReader.SheetIterator iter = (XSSFReader.SheetIterator) reader.getSheetsData();
            if (!iter.hasNext()) {
                log.warn("No sheets found in the Excel file: {}", file.getOriginalFilename());
                return;
            }

            try (InputStream sheetInputStream = iter.next()) {
                parseSheet(sheetInputStream, styles, strings);
            }
            pushTransactionDetailsToKafka();

        } catch (Exception e) {
            log.error("Failed to process Excel file: {}", file.getOriginalFilename(), e);
            throw new BusinessException("500", e.getMessage());
        }

        log.info("Completed Excel processing for file: {}", file.getOriginalFilename());
    }

    private void parseSheet(InputStream sheetInputStream, StylesTable styles, ReadOnlySharedStringsTable strings) throws Exception {
        SAXParserFactory saxFactory = SAXParserFactory.newInstance();
        saxFactory.setNamespaceAware(true);
        XMLReader parser = saxFactory.newSAXParser().getXMLReader();

        List<Transaction> batch = new ArrayList<>(BATCH_SIZE);

        XSSFSheetXMLHandler.SheetContentsHandler handler = new XSSFSheetXMLHandler.SheetContentsHandler() {
            int rowNum = 0;
            String[] rowData = new String[17];

            @Override
            public void startRow(int row) {
                rowNum = row;
                rowData = new String[17];
            }

            @Override
            public void endRow(int row) {
                if (rowNum == 0) return;

                try {
                    Transaction txn = mapRowToEntity(rowData);
                    batch.add(txn);

                    if (batch.size() >= BATCH_SIZE) {
                        saveBatch(batch);
                    }
                } catch (Exception ex) {
                    log.error("Failed to parse row {}: {}", rowNum, ex.getMessage(), ex);
                }
            }

            @Override
            public void cell(String cellReference, String formattedValue, XSSFComment comment) {
                if (rowNum == 0) return;

                int colIndex = convertColumnToIndex(cellReference);
                if (colIndex < rowData.length) {
                    rowData[colIndex] = formattedValue;
                }
            }

            @Override
            public void headerFooter(String text, boolean isHeader, String tagName) {
            }

            private int convertColumnToIndex(String cellRef) {
                String letters = cellRef.replaceAll("\\d", "");
                int col = 0;
                for (int i = 0; i < letters.length(); i++) {
                    col *= 26;
                    col += letters.charAt(i) - 'A' + 1;
                }
                return col - 1;
            }
        };

        parser.setContentHandler(new XSSFSheetXMLHandler(styles, strings, handler, false));
        parser.parse(new InputSource(sheetInputStream));

        if (!batch.isEmpty()) {
            saveBatch(batch);
        }
    }

    private Transaction mapRowToEntity(String[] rowData) {
        Transaction txn = new Transaction();

        txn.setTxnDate(parseValue(rowData[0], Instant.class));
        txn.setTransactionId(rowData[1]);
        txn.setAccountNumber(rowData[2]);
        txn.setCustomerName(rowData[3]);
        txn.setMerchantName(rowData[4]);
        txn.setAmount(parseValue(rowData[5], Double.class));
        txn.setCurrency(rowData[6]);
        txn.setPaymentMethod(rowData[7]);
        txn.setStatus(rowData[8]);
        txn.setCategory(rowData[9]);
        txn.setSubCategory(rowData[10]);
        txn.setCountry(rowData[11]);
        txn.setCity(rowData[12]);
        txn.setChannel(rowData[13]);
        txn.setRewardPoints(parseValue(rowData[14], Integer.class));
        txn.setSettlementDate(parseValue(rowData[15], LocalDate.class));
        txn.setRemarks(rowData[16]);

        return txn;
    }

    private void saveBatch(List<Transaction> batch) {
        try {
            transactionRepository.saveAll(batch);
            log.info("Saved batch of {} records", batch.size());
            batch.clear();
        } catch (Exception e) {
            log.error("Failed to save batch of size {}: {}", batch.size(), e.getMessage(), e);
            throw new BusinessException("500", e.getMessage());
        }
    }

    private static <T> T parseValue(String value, Class<T> type) {
        if (value == null || value.isBlank()) return null;

        Function<String, ?> parser = PARSERS.get(type);
        if (parser == null) {
            throw new IllegalArgumentException("No parser registered for type: " + type.getSimpleName());
        }

        return (T) parser.apply(value);
    }


    private void pushTransactionDetailsToKafka() {
        log.info("Starting Kafka push for transactions");

        int page = 0;
        int pageSize = 100;
        boolean hasMore = true;

        try {
            while (hasMore) {
                Page<Transaction> transactionPage = transactionRepository
                        .findAll(PageRequest.of(page, pageSize, Sort.by("txnDate").ascending()));

                if (transactionPage.isEmpty()) {
                    hasMore = false;
                    break;
                }
                for (Transaction transaction : transactionPage.getContent()) {
                    Map<String, Object> request = new HashMap<>();
                    request.put("transaction_id", transaction.getTransactionId());
                    request.put("amount", transaction.getAmount());
                    try {
                        kafkaTemplate.send("tax_calculation", request);
                    } catch (Exception e) {
                        log.error("Failed to push transaction {} to Kafka: {}", transaction.getId(), e.getMessage(), e);
                    }
                }

                page++;
                hasMore = !transactionPage.isLast();
            }

            log.info("Completed pushing all transactions to Kafka");

        } catch (Exception e) {
            log.error("Unexpected error while pushing transactions to Kafka: {}", e.getMessage(), e);
            throw new BusinessException("500", "Kafka push failed: " + e.getMessage());
        }
    }
}
