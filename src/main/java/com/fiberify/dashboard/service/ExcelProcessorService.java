package com.fiberify.dashboard.service;

import com.fiberify.dashboard.model.BlockNode;
import com.fiberify.dashboard.model.GpEntry;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ExcelProcessorService {

    private List<BlockNode> currentData = new ArrayList<>();
    private List<Map<String, Object>> latestIncidents = new ArrayList<>();
    private String reportDate = "Initializing...";
    private volatile String syncProgress = "Idle";
    private volatile boolean stopSync = false;
    private volatile boolean isSyncRunning = false;

    private static final String DASHBOARD_FILES_DIR = "dashboard_files";
    private static final String LIVE_TOKEN = "Bearer eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJmaWJlcmlmeWluYyIsImF1dGgiOiJST0xFX0JBLFJPTEVfT0EsUk9MRV9QTEFOX0FETUlOLFJPTEVfUk9MTE9VVF9BRE1JTixST0xFX1JPTExPVVRfTUFOQUdFUixST0xFX1VTRVJfQURNSU4iLCJleHAiOjE3Nzg5MTExNjl9.8jWc2hshIU-6y4wFwUH5LTjuzLhIE3ThuNkUDV2fLCR6rz1ZiWTWg_bqRedzBK0m8CfLjCJlfGTvskUraVrl8A";

    @PostConstruct
    public void init() {
        processLatestFiles();
    }

    public List<BlockNode> getCurrentData() {
        return currentData;
    }

    public List<Map<String, Object>> getLatestIncidents() {
        return latestIncidents;
    }

    public String getReportDate() {
        return reportDate;
    }

    public String getSyncProgress() {
        return syncProgress;
    }

    public boolean isSyncRunning() {
        return isSyncRunning;
    }

    public void stopSync() {
        this.stopSync = true;
    }

    public void processLatestFiles() {
        File dir = new File(DASHBOARD_FILES_DIR);
        File[] oltFiles = dir.listFiles((d, name) -> name.startsWith("OLT_GP_") && name.endsWith(".xlsx"));
        if (oltFiles == null || oltFiles.length == 0) {
            reportDate = "No Base Excel Found";
            return;
        }
        Arrays.sort(oltFiles, Comparator.comparingLong(File::lastModified).reversed());
        File latestOlt = oltFiles[0];

        try {
            this.currentData = parseOltFile(latestOlt, new HashSet<>(), new HashSet<>(), new HashMap<>(),
                    new HashMap<>());
            this.reportDate = LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")) + " (Report File)";
        } catch (Exception e) {
            reportDate = "Error loading base data";
            e.printStackTrace();
        }
    }

    public void processLiveApi() {
        if (isSyncRunning)
            return;
        isSyncRunning = true;
        stopSync = false;
        syncProgress = "Fetching Live Data...";
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", LIVE_TOKEN);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            List<Map<String, Object>> allIncidents = new ArrayList<>();
            int page = 0, size = 100, maxPages = 50;

            while (page < maxPages && !stopSync) {
                syncProgress = "Syncing... (" + allIncidents.size() + " items fetched)";

                String url = "https://sitpolycab.fiberify.com/api/tr-cases/searchvalue/olt alerts?page=" + page
                        + "&size=" + size + "&sort=update_date,desc&cacheBuster=" + System.currentTimeMillis();
                System.out.println("Sync: Calling API Page " + page + ": " + url);

                ResponseEntity<Object> response = restTemplate.exchange(url, HttpMethod.GET, entity, Object.class);
                if (response.getStatusCode().is2xxSuccessful()) {
                    List<Map<String, Object>> pageItems = extractContent(response.getBody());
                    System.out.println("Sync: Page " + page + " fetched " + (pageItems != null ? pageItems.size() : 0)
                            + " items.");
                    if (pageItems == null || pageItems.isEmpty())
                        break;

                    allIncidents.addAll(pageItems);
                    if (pageItems.size() < size)
                        break;
                    page++;
                } else {
                    System.out.println("Sync: API Error on page " + page + ": " + response.getStatusCode());
                    break;
                }
            }
            this.latestIncidents = allIncidents;
            processIncidentsAndRefresh(allIncidents);
            syncProgress = stopSync ? "Stopped" : "Completed";
            reportDate = LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")) + " (Live)";
        } catch (Exception e) {
            syncProgress = "Error: " + e.getMessage();
            e.printStackTrace();
        } finally {
            isSyncRunning = false;
        }
    }

    private List<Map<String, Object>> extractContent(Object body) {
        if (body instanceof List)
            return (List<Map<String, Object>>) body;
        if (body instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) body;
            if (map.containsKey("content"))
                return (List<Map<String, Object>>) map.get("content");
        }
        return new ArrayList<>();
    }

    private void processIncidentsAndRefresh(List<Map<String, Object>> incidents) throws Exception {
        Set<String> downIds = new HashSet<>();
        Set<String> resolvedIds = new HashSet<>();
        Map<String, String> idToAlarm = new HashMap<>();
        Map<String, String> idToTime = new HashMap<>();

        for (Map<String, Object> item : incidents) {
            String ticketStatus = String.valueOf(item.getOrDefault("status", item.getOrDefault("ticketStatus", "")))
                    .toUpperCase();
            String title = String.valueOf(item.getOrDefault("title", "")).toUpperCase();
            String assetName = String.valueOf(item.getOrDefault("assetName", "")).toUpperCase();
            String alarm = String.valueOf(item.getOrDefault("alarmCause", "Network Issue"));
            String time = String.valueOf(item.getOrDefault("updatedDate", item.getOrDefault("createdDate", "--")));

            // Check top-level status
            boolean isActive = !ticketStatus.contains("RESOLVE") && !ticketStatus.contains("CLOSE");
            boolean isResolved = ticketStatus.contains("RESOLVE") || ticketStatus.contains("CLOSE");
            boolean isDownKeyword = title.contains("DOWN") || title.contains("UNKNOWN") || title.contains("UNREACHABLE")
                    || title.contains("OFFLINE");
            boolean isAssigning = ticketStatus.contains("ASSIGN") || ticketStatus.contains("NEW")
                    || ticketStatus.contains("OPEN") || ticketStatus.contains("TOBEASSIGNED");

            // 1. Initial detection based on title and ticket status
            boolean downDetected = (isActive && (isDownKeyword || isAssigning));

            // 2. Comprehensive Study of Every Attribute in the Ticket (only if active)
            if (isActive) {
                List<Map<String, Object>> attrs = (List<Map<String, Object>>) item.get("caseTypeAttributeValues");
                if (attrs != null) {
                    for (Map<String, Object> attr : attrs) {
                        String attrName = String.valueOf(attr.get("name")).toUpperCase();
                        String val = String.valueOf(attr.get("attributeValue")).toUpperCase();

                        if (val != null && !val.equals("NULL") && !val.trim().isEmpty()) {
                            // Check if the value contains any failure keywords
                            if (val.contains("DOWN") || val.contains("OFFLINE") || val.contains("UNREACHABLE") ||
                                    val.contains("UNKNOWN") || val.contains("CRITICAL") || val.contains("OUTAGE") ||
                                    val.contains("POWER FAILURE") || val.contains("LINK FAIL")) {
                                downDetected = true;
                            }

                            // Treat every attribute as a potential identifier
                            if (val.length() > 3) {
                                addId(downIds, val, idToAlarm, idToTime, alarm, time);
                                if (attrName.contains("BLOCK") || attrName.contains("OLT") || attrName.contains("GP")
                                        || attrName.contains("NODE")) {
                                    downDetected = true;
                                }
                            }
                        }
                    }
                }
            }

            if (downDetected) {
                addId(downIds, assetName, idToAlarm, idToTime, alarm, time);
                addId(downIds, String.valueOf(item.get("code")), idToAlarm, idToTime, alarm, time);
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("[A-Z0-9]+").matcher(title);
                while (m.find())
                    if (m.group().length() > 3)
                        addId(downIds, m.group(), idToAlarm, idToTime, alarm, time);
            } else if (isResolved) {
                addId(resolvedIds, assetName, idToAlarm, idToTime, alarm, time);
                addId(resolvedIds, String.valueOf(item.get("code")), idToAlarm, idToTime, alarm, time);
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("[A-Z0-9]+").matcher(title);
                while (m.find())
                    if (m.group().length() > 3)
                        addId(resolvedIds, m.group(), idToAlarm, idToTime, alarm, time);
            }
        }

        File latestOlt = getLatestOltFile();
        if (latestOlt != null)
            this.currentData = parseOltFile(latestOlt, downIds, resolvedIds, idToAlarm, idToTime);
    }

    private void addId(Set<String> set, String id, Map<String, String> alarms, Map<String, String> times, String alarm,
            String time) {
        if (id != null && !id.isEmpty() && !id.equalsIgnoreCase("null")) {
            String key = id.trim().toUpperCase();
            set.add(key);
            // Keep the first (most recent) alarm/time found
            alarms.putIfAbsent(key, alarm);
            times.putIfAbsent(key, time);
        }
    }

    private File getLatestOltFile() {
        File dir = new File(DASHBOARD_FILES_DIR);
        File[] files = dir.listFiles((d, name) -> name.startsWith("OLT_GP_") && name.endsWith(".xlsx"));
        if (files == null || files.length == 0)
            return null;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        return files[0];
    }

    private List<BlockNode> parseOltFile(File file, Set<String> downIds, Set<String> resolvedIds,
            Map<String, String> idToAlarm, Map<String, String> idToTime) throws Exception {
        List<BlockNode> nodes = new ArrayList<>();
        Map<String, BlockNode> ipToNode = new LinkedHashMap<>();
        try (FileInputStream fis = new FileInputStream(file); Workbook wb = new XSSFWorkbook(fis)) {
            Sheet sheet = wb.getSheetAt(0);
            Row hdr = sheet.getRow(0);
            int distCol = findCol(hdr, "DISTRICT");
            int nameCol = findCol(hdr, "BLOCK NODE NAME");
            int ipCol = findCol(hdr, "BLOCK NODE IP");
            int codeCol = findCol(hdr, "BLOCK CODE", "BLOCK NODE LOCATION CODE");
            int gpBlockCol = findCol(hdr, "GP BLOCK");
            int gpLocCol = findCol(hdr, "GP LOCATION");
            int gpCodeCol = findCol(hdr, "GP LOCATION CODE");

            String curIP = "", curName = "", curDist = "", curCode = "", curGpBlock = "";
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null)
                    continue;
                String ip = getVal(row, ipCol), name = getVal(row, nameCol), dist = getVal(row, distCol),
                        code = getVal(row, codeCol);
                String gpBlock = getVal(row, gpBlockCol), gpLoc = getVal(row, gpLocCol),
                        gpCode = getVal(row, gpCodeCol);
                if (!ip.isEmpty()) {
                    curIP = ip;
                    curName = name;
                    curDist = dist;
                    curCode = code;
                    curGpBlock = gpBlock;
                }

                if (!curIP.isEmpty()) {
                    BlockNode node = ipToNode.get(curIP);
                    if (node == null) {
                        node = new BlockNode();
                        node.setIp(curIP);
                        node.setName(curName);
                        node.setDistrict(curDist);
                        node.setBlockCode(curCode);
                        node.setGpBlock(curGpBlock);

                        // OLT Status Logic
                        if (isMatch(curIP, curName, curCode, downIds)) {
                            node.setStatus("UNREACHABLE");
                            node.setAlarm(findAlarm(curIP, curName, curCode, idToAlarm));
                        } else if (isMatch(curIP, curName, curCode, resolvedIds)) {
                            node.setStatus("RESOLVED");
                        } else {
                            node.setStatus("UP");
                        }

                        ipToNode.put(curIP, node);
                        nodes.add(node);
                    }

                    if (!gpLoc.isEmpty()) {
                        GpEntry gp = new GpEntry(gpLoc, gpCode);
                        // GP Status Logic with Inheritance
                        if (isExactMatch(gpLoc, gpCode, downIds)) {
                            gp.setStatus("DOWN");
                        } else if ("UNREACHABLE".equals(node.getStatus())) {
                            gp.setStatus("DOWN"); // Parent OLT is down
                        } else if (isExactMatch(gpLoc, gpCode, resolvedIds)) {
                            gp.setStatus("RESOLVED");
                        } else if ("RESOLVED".equals(node.getStatus())) {
                            gp.setStatus("RESOLVED"); // Parent OLT was resolved
                        } else {
                            gp.setStatus("UP");
                        }
                        node.addGp(gp);
                    }
                }
            }
        }
        return nodes;
    }

    private boolean isMatch(String ip, String name, String code, Set<String> downIds) {
        String[] keys = { ip, name, code };
        for (String k : keys) {
            if (k == null || k.isEmpty())
                continue;
            String normK = k.toUpperCase().trim();
            for (String downId : downIds)
                if (normK.contains(downId) || downId.contains(normK))
                    return true;
        }
        return false;
    }

    private boolean isExactMatch(String loc, String code, Set<String> downIds) {
        String normLoc = loc.toUpperCase().trim();
        String normCode = code.toUpperCase().trim();
        for (String downId : downIds)
            if (normLoc.equals(downId) || normCode.equals(downId))
                return true;
        return false;
    }

    private String findAlarm(String ip, String name, String code, Map<String, String> idToAlarm) {
        String[] keys = { ip, name, code };
        for (String k : keys) {
            if (k == null || k.isEmpty())
                continue;
            String normK = k.toUpperCase().trim();
            if (idToAlarm.containsKey(normK))
                return idToAlarm.get(normK);
        }
        return "Network Issue";
    }

    private int findCol(Row hdr, String... targets) {
        if (hdr == null)
            return -1;
        for (int c = 0; c < hdr.getLastCellNum(); c++) {
            String v = getVal(hdr.getCell(c)).toUpperCase().trim();
            for (String t : targets) {
                if (v.equals(t.toUpperCase()))
                    return c;
            }
        }
        // Fallback to contains if exact match fails
        for (int c = 0; c < hdr.getLastCellNum(); c++) {
            String v = getVal(hdr.getCell(c)).toUpperCase().trim();
            for (String t : targets) {
                if (v.contains(t.toUpperCase()))
                    return c;
            }
        }
        return -1;
    }

    private String getVal(Row row, int col) {
        if (col < 0)
            return "";
        return getVal(row.getCell(col));
    }

    private String getVal(Cell cell) {
        if (cell == null)
            return "";
        if (cell.getCellType() == CellType.NUMERIC) {
            double d = cell.getNumericCellValue();
            if (d == (long) d)
                return String.valueOf((long) d);
            return String.valueOf(d);
        }
        return cell.toString().trim();
    }
}
