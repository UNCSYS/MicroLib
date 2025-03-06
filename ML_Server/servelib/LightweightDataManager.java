package servelib;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

public class LightweightDataManager {

    private final String filePath;
    private final Map<Integer, Long> rowIndex;
    private final ExecutorService executor;
    private final Map<Integer, String> cache;
    private static final int CACHE_SIZE = 100;
    private List<String> headers;
    private static final String DELIMITER = "|";
    private final ReentrantLock writeLock = new ReentrantLock();

    public LightweightDataManager(String filePath) {
        this.filePath = filePath;
        this.rowIndex = new ConcurrentHashMap<>();
        this.executor = Executors.newFixedThreadPool(4);
        this.cache = new ConcurrentHashMap<>();
        this.headers = new ArrayList<>();
        loadHeaders();
        buildIndex();
    }

    private void loadHeaders() {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String headerLine = reader.readLine();
            if (headerLine != null) {
                headers = new ArrayList<>(Arrays.asList(headerLine.split("\\" + DELIMITER, -1)));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load headers", e);
        }
    }

    private void buildIndex() {
        rowIndex.clear();
        try (RandomAccessFile file = new RandomAccessFile(filePath, "r")) {
            long offset = file.getFilePointer();
            String line = file.readLine(); // Skip header
            offset = file.getFilePointer();
            int rowNumber = 0;
            while ((line = file.readLine()) != null) {
                rowIndex.put(rowNumber++, offset);
                offset = file.getFilePointer();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to build index", e);
        }
    }

    public String readRow(int rowNumber) {
        if (cache.containsKey(rowNumber)) {
            return cache.get(rowNumber);
        }
        Long offset = rowIndex.get(rowNumber);
        if (offset == null) {
            throw new IllegalArgumentException("Row number out of range: " + rowNumber);
        }
        try (RandomAccessFile file = new RandomAccessFile(filePath, "r")) {
            file.seek(offset);
            String line = file.readLine();
            cache.put(rowNumber, line);
            return line;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read row: " + rowNumber, e);
        }
    }

    public String get(int id, String column) {
        int columnIndex = headers.indexOf(column);
        if (columnIndex == -1) {
            throw new IllegalArgumentException("Column not found: " + column);
        }
        String row = readRow(id - 1);
        String[] values = row.split("\\" + DELIMITER, -1);
        return (columnIndex < values.length) ? values[columnIndex] : "null";
    }

    public String get(String searchColumn, String searchValue, String targetColumn) {
        int searchIndex = headers.indexOf(searchColumn);
        int targetIndex = headers.indexOf(targetColumn);
        if (searchIndex == -1 || targetIndex == -1) {
            throw new IllegalArgumentException("Column not found");
        }
        for (int rowNumber : rowIndex.keySet()) {
            String row = readRow(rowNumber);
            String[] values = row.split("\\" + DELIMITER, -1);
            if (searchIndex < values.length && values[searchIndex].equals(searchValue)) {
                return (targetIndex < values.length) ? values[targetIndex] : "null";
            }
        }
        return null;
    }

    public void set(int id, String column, String value) {
        int columnIndex = headers.indexOf(column);
        if (columnIndex == -1) {
            throw new IllegalArgumentException("Column not found: " + column);
        }
        String row = readRow(id - 1);
        String[] values = row.split("\\" + DELIMITER, -1);
        if (columnIndex >= values.length) {
            values = Arrays.copyOf(values, columnIndex + 1);
        }
        values[columnIndex] = value;
        String newRow = String.join(DELIMITER, values);
        updateRow(id - 1, newRow);
    }

    public void set(String searchColumn, String searchValue, String targetColumn, String targetValue) {
        int searchIndex = headers.indexOf(searchColumn);
        int targetIndex = headers.indexOf(targetColumn);
        if (searchIndex == -1 || targetIndex == -1) {
            throw new IllegalArgumentException("Column not found");
        }
        for (int rowNumber : rowIndex.keySet()) {
            String row = readRow(rowNumber);
            String[] values = row.split("\\" + DELIMITER, -1);
            if (searchIndex < values.length && values[searchIndex].equals(searchValue)) {
                if (targetIndex >= values.length) {
                    values = Arrays.copyOf(values, targetIndex + 1);
                }
                values[targetIndex] = targetValue;
                String newRow = String.join(DELIMITER, values);
                updateRow(rowNumber, newRow);
            }
        }
    }

    private void updateRow(int rowNumber, String newData) {
        Future<?> future = executor.submit(() -> {
            writeLock.lock();
            try (RandomAccessFile file = new RandomAccessFile(filePath, "rw")) {
                Long offset = rowIndex.get(rowNumber);
                if (offset == null) {
                    throw new IllegalArgumentException("Row number out of range: " + rowNumber);
                }
                file.seek(offset);
                String oldLine = file.readLine();
                long nextOffset = file.getFilePointer();
                byte[] remainingData = new byte[(int) (file.length() - nextOffset)];
                file.readFully(remainingData);
    
                file.seek(offset);
                file.writeBytes(newData + System.lineSeparator());
                file.write(remainingData);
    
                // 截断文件，确保文件长度正确
                file.setLength(file.getFilePointer());
    
                cache.put(rowNumber, newData);
            } catch (IOException e) {
                throw new RuntimeException("Failed to update row: " + rowNumber, e);
            } finally {
                writeLock.unlock();
            }
        });
    
        try {
            future.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to update row: " + rowNumber, e);
        }
    }

    public void addColumn(String column) {
        if (!headers.contains(column)) {
            headers.add(column);
            rewriteFile();
        }
    }

    public void removeColumn(String column) {
        int columnIndex = headers.indexOf(column);
        if (columnIndex == -1) {
            throw new IllegalArgumentException("Column not found: " + column);
        }
        headers.remove(columnIndex);
        rewriteFile();
    }

    private void rewriteFile() {
        Future<?> future = executor.submit(() -> {
            writeLock.lock();
            File tempFile = new File(filePath + ".tmp");
            List<String> oldHeaders;
            try (BufferedReader reader = new BufferedReader(new FileReader(filePath));
                 BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile))) {
                String oldHeaderLine = reader.readLine();
                oldHeaders = (oldHeaderLine != null) ?
                        Arrays.asList(oldHeaderLine.split("\\" + DELIMITER, -1)) :
                        Collections.emptyList();
                writer.write(String.join(DELIMITER, headers) + System.lineSeparator());
                Map<String, Integer> oldHeaderIndices = new HashMap<>();
                for (int i = 0; i < oldHeaders.size(); i++) {
                    oldHeaderIndices.put(oldHeaders.get(i), i);
                }
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] oldValues = line.split("\\" + DELIMITER, -1);
                    List<String> newValues = new ArrayList<>();
                    for (String header : headers) {
                        Integer oldIndex = oldHeaderIndices.get(header);
                        if (oldIndex != null && oldIndex < oldValues.length) {
                            newValues.add(oldValues[oldIndex]);
                        } else {
                            newValues.add("null");
                        }
                    }
                    writer.write(String.join(DELIMITER, newValues) + System.lineSeparator());
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to rewrite file", e);
            } finally {
                writeLock.unlock();
            }

            try {
                Files.move(tempFile.toPath(), new File(filePath).toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new RuntimeException("Failed to replace file", e);
            }

            buildIndex();
            cache.clear();
        });

        try {
            future.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to rewrite file", e);
        }
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }
}
